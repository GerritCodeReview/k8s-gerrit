# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import git
import logging
import os

from datetime import datetime, timedelta
from glob import glob

LOG = logging.getLogger(__name__)

LOG_FILE = "/var/log/git/gc.log"
MAX_AGE_GC_LOCK_SEC = timedelta(hours=12)
MAX_AGE_EMPTY_REF_DIRS_SEC = timedelta(hours=1)
MAX_AGE_INCOMING_PACKS_SEC = timedelta(days=1)
GIT_CONFIG_OPTS = [
    ("core", "logallrefupdates", True),
    ("gc", "auto", 0),
    ("gc", "autopacklimit", 0),
    ("gc", "cruftPacks", True),
    ("gc", "indexversion", 2),
    ("gc", "packRefs", True),
    ("gc", "pruneExpire", "2.weeks.ago"),
    ("gc", "reflogexpire", "never"),
    ("gc", "reflogexpireunreachable", "never"),
    ("pack", "compression", 9),
    ("pack", "depth", 50),
    ("pack", "indexversion", 2),
    ("pack", "window", 250),
    ("receive", "autogc", False),
    ("repack", "usedeltabaseoffset", True),
]


class GitGarbageCollection:
    def __init__(self, site_path, bitmap, pack_refs, preserve_packs):
        self.repo_dir = os.path.join(site_path, "git")
        self.is_bitmap = bitmap
        self.is_pack_refs = pack_refs
        self.is_preserve_packs = preserve_packs

    def gc(self, projects=[], skips=[]):
        LOG.info("Started")
        if not projects:
            projects = self._find_all_projects()

        for project in projects:
            if project in skips:
                LOG.info("Skipped: %s" % project)
                continue

            self._gc_project(project)

        LOG.info("Finished")

    def _find_all_projects(self):
        for current, dirs, _ in os.walk(self.repo_dir, topdown=True):
            project, ext = os.path.splitext(current)

            if ext != ".git":
                continue

            dirs.clear()
            yield os.path.basename(project)

    def _gc_project(self, project):
        LOG.info("Started: %s" % project)
        project_dir = os.path.join(self.repo_dir, project + ".git")
        if not os.path.exists(project_dir) or not os.path.isdir(project_dir):
            LOG.error("Failed: Directory does not exist: %s" % project_dir)
            return

        gc_opts = []
        if self._is_aggressive(project_dir):
            gc_opts.append("--aggressive")

        repo = git.Repo(project_dir)
        self._ensure_repo_config(repo)
        self._delete_stale_gc_lock(project_dir)

        try:
            if self.is_preserve_packs:
                output = repo.git.gc_preserve(gc_opts)
            else:
                output = repo.git.gc(gc_opts)
            if output:
                LOG.info(output)
        except git.GitCommandError as e:
            if e.stdout:
                LOG.info(e.stdout)
            LOG.info(e.stderr)
            LOG.error("Failed: %s", project)

        self._delete_empty_ref_dirs(project_dir)
        self._delete_stale_incoming_packs(project_dir)

        if self.is_pack_refs:
            loose_ref_count = len(
                [
                    file
                    for file in os.listdir(os.path.join(project_dir, "refs"))
                    if os.path.isfile(file)
                ]
            )
            if loose_ref_count > 10:
                repo.git.pack_refs("--all")
                LOG.info("Found %d loose refs -> pack all refs" % loose_ref_count)

        LOG.info("Finished: %s", project)

    def _ensure_repo_config(self, repo):
        config_writer = repo.config_writer("repository")
        config_writer.read()

        for section, key, value in GIT_CONFIG_OPTS:
            config_writer.set_value(section, key, value)

        config_writer.set_value("repack", "writebitmaps", self.is_bitmap)

        config_writer.write()

    def _is_aggressive(self, project_dir):
        if os.path.exists(os.path.join(project_dir, "gc-aggressive")):
            LOG.info("Running aggressive gc in %s" % project_dir)
            return True
        elif os.path.exists(os.path.join(project_dir, "gc-aggressive-once")):
            LOG.info("Running aggressive gc once in %s" % project_dir)
            os.remove(os.path.join(project_dir, "gc-aggressive-once"))
            return True
        return False

    def _delete_stale_gc_lock(self, project_dir):
        gc_lock_path = os.path.join(project_dir, "gc.pid")
        if os.path.exists(gc_lock_path) and self._is_file_stale(
            gc_lock_path, MAX_AGE_GC_LOCK_SEC
        ):
            LOG.warning(
                "Pruning stale 'gc.pid' lock file older than 12 hours: %s"
                % gc_lock_path
            )
            os.remove(gc_lock_path)

    def _delete_empty_ref_dirs(self, project_dir):
        refs_path = os.path.join(project_dir, "refs")
        for dir in glob(os.path.join(refs_path, "*/*")):
            if (
                os.path.isdir(dir)
                and len(os.listdir(dir)) == 0
                and self._is_file_stale(dir, MAX_AGE_EMPTY_REF_DIRS_SEC)
            ):
                os.removedirs(dir)

    def _delete_stale_incoming_packs(self, project_dir):
        objects_path = os.path.join(project_dir, "objects")
        for file in glob(os.path.join(objects_path, "incoming_*.pack")):
            if self._is_file_stale(file, MAX_AGE_INCOMING_PACKS_SEC):
                LOG.warning(
                    "Pruning stale incoming pack/index file older than 24 hours: %s",
                    file,
                )
                os.remove(file)

    def _is_file_stale(self, file, max_age):
        return (
            datetime.fromtimestamp(os.stat(file).st_mtime) + max_age
            < datetime.now().timestamp()
        )
