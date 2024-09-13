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

import abc
import git
import logging
import os
import subprocess

from datetime import timedelta
from glob import glob

from .util import Util

LOG = logging.getLogger(__name__)

LOG_FILE = "/var/log/git/gc.log"
MAX_AGE_GC_LOCK = timedelta(hours=12)
MAX_AGE_EMPTY_REF_DIRS = timedelta(hours=1)
MAX_AGE_INCOMING_PACKS = timedelta(days=1)
MAX_LOOSE_REF_COUNT = 10
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
PACK_PATH = "objects/pack"
PRESERVED_PACK_PATH = f"{PACK_PATH}/preserved"
REPO_SUFFIX = ".git"


class GCStep(abc.ABC):
    @abc.abstractmethod
    def run(self, repo):
        pass


class RepoConfigInitStep(GCStep):
    def __init__(self, create_bitmap=True):
        super().__init__()
        self.create_bitmap = create_bitmap

    def run(self, repo):
        config_writer = repo.config_writer("repository")
        config_writer.read()

        for section, key, value in GIT_CONFIG_OPTS:
            config_writer.set_value(section, key, value)

        config_writer.set_value("repack", "writebitmaps", self.create_bitmap)

        config_writer.write()
        config_writer.release()


class GCLockHandlingInitStep(GCStep):
    def run(self, repo):
        gc_lock_path = os.path.join(repo.git_dir, "gc.pid")
        if os.path.exists(gc_lock_path) and Util.is_file_stale(
            gc_lock_path, MAX_AGE_GC_LOCK
        ):
            LOG.warning(
                "Pruning stale 'gc.pid' lock file older than 12 hours: %s", gc_lock_path
            )
            os.remove(gc_lock_path)


class PreservePacksInitStep(GCStep):
    def run(self, repo):
        config_reader = repo.config_reader("repository")
        is_prune_preserved = config_reader.get_value("gc", "prunepreserved", False)
        is_preserve_old_packs = config_reader.get_value("gc", "preserveoldpacks", False)

        if is_prune_preserved:
            self._prune_preserved(repo)

        if is_preserve_old_packs:
            self._preserve_packs(repo)

    def _prune_preserved(self, repo):
        full_preserved_pack_path = os.path.join(repo.git_dir, PRESERVED_PACK_PATH)
        if os.path.exists(full_preserved_pack_path):
            LOG.info("Pruning old preserved packs.")
            count = 0
            for file in os.listdir(full_preserved_pack_path):
                if file.endswith(".old-pack") or file.endswith(".old-idx"):
                    count += 1
                    full_old_pack_path = os.path.join(full_preserved_pack_path, file)
                    LOG.debug("Deleting %s", full_old_pack_path)
                    os.remove(full_old_pack_path)
            LOG.info("Done pruning %d old preserved packs.", count)

    def _preserve_packs(self, repo):
        full_pack_path = os.path.join(repo.git_dir, PACK_PATH)
        full_preserved_pack_path = os.path.join(repo.git_dir, PRESERVED_PACK_PATH)
        if not os.path.exists(full_preserved_pack_path):
            os.makedirs(full_preserved_pack_path)
        LOG.info("Preserving packs.")
        count = 0
        for file in os.listdir(full_pack_path):
            full_file_path = os.path.join(full_pack_path, file)
            filename, ext = os.path.splitext(file)
            if (
                os.path.isfile(full_file_path)
                and filename.startswith("pack-")
                and ext in [".pack", ".idx"]
            ):
                LOG.debug("Preserving pack %s", file)
                os.link(
                    os.path.join(full_pack_path, file),
                    os.path.join(
                        full_preserved_pack_path,
                        self._get_preserved_packfile_name(file),
                    ),
                )
                if ext == ".pack":
                    count += 1
        LOG.info("Preserved %d packs", count)

    def _get_preserved_packfile_name(self, file):
        filename, ext = os.path.splitext(file)
        return f"{filename}.old-{ext[1:]}"


DEFAULT_INIT_STEPS = [GCLockHandlingInitStep()]


class DeleteEmptyRefDirsCleanupStep(GCStep):
    def run(self, repo):
        refs_path = os.path.join(repo.git_dir, "refs")
        for dir in glob(os.path.join(refs_path, "*/*")):
            if (
                os.path.isdir(dir)
                and len(os.listdir(dir)) == 0
                and Util.is_file_stale(dir, MAX_AGE_EMPTY_REF_DIRS)
            ):
                os.removedirs(dir)


class DeleteStaleIncomingPacksCleanupStep(GCStep):
    def run(self, repo):
        objects_path = os.path.join(repo.git_dir, "objects")
        for file in glob(os.path.join(objects_path, "incoming_*.pack")):
            if Util.is_file_stale(file, MAX_AGE_INCOMING_PACKS):
                LOG.warning(
                    "Pruning stale incoming pack/index file older than 24 hours: %s",
                    file,
                )
                os.remove(file)


class PackRefsAfterStep(GCStep):
    def run(self, repo):
        loose_ref_count = 0
        for _, _, files in os.walk(os.path.join(repo.git_dir, "refs"), topdown=True):
            loose_ref_count += len([file for file in files])
        if loose_ref_count > MAX_LOOSE_REF_COUNT:
            repo.git.pack_refs("--all")
            LOG.info("Found %d loose refs -> pack all refs", loose_ref_count)
        else:
            LOG.info("Found less than 10 refs -> skipping pack all refs")


DEFAULT_AFTER_STEPS = [
    DeleteEmptyRefDirsCleanupStep(),
    DeleteStaleIncomingPacksCleanupStep(),
]


class GitGarbageCollectionProvider:
    @staticmethod
    def get(site_path, create_bitmap=True, pack_refs=True, preserve_packs=False):
        init_steps = DEFAULT_INIT_STEPS.copy()
        after_steps = DEFAULT_AFTER_STEPS.copy()

        init_steps.append(RepoConfigInitStep(create_bitmap))
        if preserve_packs:
            init_steps.append(PreservePacksInitStep())

        if pack_refs:
            after_steps.append(PackRefsAfterStep())

        return GitGarbageCollection(site_path, init_steps, after_steps)


class GitGarbageCollection:
    def __init__(self, site_path, init_steps, after_steps):
        self.repo_dir = os.path.join(site_path, "git")
        self.init_steps = init_steps
        self.after_steps = after_steps

    def gc(self, projects=[], skips=[]):
        LOG.info("Started")
        if not projects:
            projects = self._find_all_projects()

        for project in projects:
            if project in skips:
                LOG.info("Skipped: %s", project)
                continue

            self._gc_project(project)

        LOG.info("Finished")

    def _find_all_projects(self):
        for current, dirs, _ in os.walk(self.repo_dir, topdown=True):
            project, ext = os.path.splitext(current)

            if ext != REPO_SUFFIX:
                continue

            dirs.clear()
            yield f"{current[len(self.repo_dir) + 1:-len(REPO_SUFFIX)]}"

    def _gc_project(self, project):
        LOG.info("Started: %s", project)
        project_dir = os.path.join(self.repo_dir, project + REPO_SUFFIX)
        if not os.path.exists(project_dir) or not os.path.isdir(project_dir):
            LOG.error("Failed: Directory does not exist: %s", project_dir)
            return

        repo = git.Repo(project_dir)
        for init_step in self.init_steps:
            init_step.run(repo)

        cmd = "git gc"
        if self._is_aggressive(project_dir):
            cmd += " --aggressive"

        try:
            # Git gc requires a shell to output logs, i.e. `shell` has to be `True`
            subprocess.run(cmd, shell=True, cwd=repo.git_dir, check=True)
        except subprocess.CalledProcessError as e:
            LOG.info(e.stdout)
            LOG.info(e.stderr)
            LOG.error("Failed: %s", project)

        for after_step in self.after_steps:
            after_step.run(repo)

        LOG.info("Finished: %s", project)

    def _is_aggressive(self, project_dir):
        if os.path.exists(os.path.join(project_dir, "gc-aggressive")):
            LOG.info("Running aggressive gc in %s", project_dir)
            return True
        elif os.path.exists(os.path.join(project_dir, "gc-aggressive-once")):
            LOG.info("Running aggressive gc once in %s", project_dir)
            os.remove(os.path.join(project_dir, "gc-aggressive-once"))
            return True
        return False
