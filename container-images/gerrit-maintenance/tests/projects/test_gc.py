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
import os
import pytest
import unittest.mock as mock

from datetime import datetime, timedelta
from pathlib import Path
from tools.projects.gc import (
    DeleteStaleIncomingPacksCleanupStep,
    DeleteEmptyRefDirsCleanupStep,
    GCLockHandlingInitStep,
    GitGarbageCollection,
    PackRefsAfterStep,
    PreservePacksInitStep,
    RepoConfigInitStep,
    GIT_CONFIG_OPTS,
)


@pytest.fixture(scope="function")
def repo(tmp_path_factory):
    dir = tmp_path_factory.mktemp("repo.git")
    return git.Repo.init(dir, bare=True)


@pytest.fixture(scope="function")
def local_repo(tmp_path_factory, repo):
    dir = tmp_path_factory.mktemp("local.git")
    return git.Repo.clone_from(repo.git_dir, dir)


@pytest.fixture(scope="function")
def site(tmp_path_factory):
    site = tmp_path_factory.mktemp("site")
    git_dir = os.path.join(site, "git")
    os.makedirs(git_dir)
    git.Repo.init(os.path.join(git_dir, "test.git"), bare=True)
    git.Repo.init(os.path.join(git_dir, "nested", "nested_test.git"), bare=True)
    return site


def test_RepoConfigInitStep(repo):
    RepoConfigInitStep(create_bitmap=True).run(repo)

    reader = repo.config_reader("repository")
    for section, key, value in GIT_CONFIG_OPTS:
        assert reader.get_value(section, key) == value

    assert reader.get_value("repack", "writebitmaps")

    RepoConfigInitStep(create_bitmap=False).run(repo)
    reader = repo.config_reader("repository")
    assert not reader.get_value("repack", "writebitmaps")


def test_GCLockHandlingInitStep(repo):
    lock_file = os.path.join(repo.git_dir, "gc.pid")
    with open(lock_file, "w") as f:
        f.write("1234")

    task = GCLockHandlingInitStep()

    task.run(repo)
    assert os.path.exists(lock_file)

    _mofify_last_modified(lock_file, timedelta(hours=13))

    task.run(repo)
    assert not os.path.exists(lock_file)


def test_PreservePacksInitStep(repo):
    task = PreservePacksInitStep()

    pack_path = os.path.join(repo.git_dir, "objects", "pack")
    preserved_pack_path = os.path.join(pack_path, "preserved")

    fake_pack = os.path.join(pack_path, "pack-fake.pack")
    fake_preserved_pack = os.path.join(preserved_pack_path, "pack-fake.old-pack")
    fake_idx = os.path.join(pack_path, "pack-fake.idx")
    fake_preserved_idx = os.path.join(preserved_pack_path, "pack-fake.old-idx")
    fake_rev = os.path.join(pack_path, "pack-fake.rev")
    fake_preserved_rev = os.path.join(preserved_pack_path, "pack-fake.old-rev")

    Path(fake_pack).touch()
    Path(fake_idx).touch()
    Path(fake_rev).touch()

    writer = repo.config_writer("repository")
    writer.set_value("gc", "preserveoldpacks", False)
    writer.write()

    task.run(repo)

    assert not os.path.exists(fake_preserved_pack)
    assert not os.path.exists(fake_preserved_idx)
    assert not os.path.exists(fake_preserved_rev)

    writer.set_value("gc", "preserveoldpacks", True)
    writer.write()

    task.run(repo)

    assert os.path.exists(fake_preserved_pack)
    assert os.path.exists(fake_preserved_idx)
    assert not os.path.exists(fake_preserved_rev)

    writer.set_value("gc", "preserveoldpacks", False)
    writer.set_value("gc", "prunepreserved", True)
    writer.write()

    task.run(repo)

    assert not os.path.exists(fake_preserved_pack)
    assert not os.path.exists(fake_preserved_idx)

    writer.release()


def test_DeleteEmptyRefDirsCleanupStep(repo):
    delete_path = os.path.join(repo.git_dir, "refs", "heads", "delete")
    os.makedirs(delete_path)
    keep_path = os.path.join(repo.git_dir, "refs", "heads", "keep")
    os.makedirs(keep_path)
    Path(os.path.join(keep_path, "abcd1234")).touch()

    task = DeleteEmptyRefDirsCleanupStep()

    task.run(repo)
    assert os.path.exists(delete_path)
    assert os.path.exists(keep_path)

    _mofify_last_modified(delete_path, timedelta(hours=2))
    task.run(repo)
    assert not os.path.exists(delete_path)


def test_DeleteStaleIncomingPacksCleanupStep(repo):
    task = DeleteStaleIncomingPacksCleanupStep()

    objects_path = os.path.join(repo.git_dir, "objects")
    pack_path = os.path.join(objects_path, "pack")
    pack_file = os.path.join(pack_path, "pack-1234.pack")
    Path(pack_file).touch()
    object_shard = os.path.join(objects_path, "f8")
    os.makedirs(object_shard)
    object_file = os.path.join(objects_path, "f8", "abcd")
    Path(object_file).touch()
    incoming_pack_file = os.path.join(objects_path, "incoming_1234.pack")
    Path(incoming_pack_file).touch()

    task.run(repo)

    assert os.path.exists(pack_file)
    assert os.path.exists(object_file)
    assert os.path.exists(incoming_pack_file)

    _mofify_last_modified(pack_file, timedelta(days=2))
    _mofify_last_modified(object_file, timedelta(days=2))
    _mofify_last_modified(incoming_pack_file, timedelta(days=2))

    task.run(repo)

    assert os.path.exists(pack_file)
    assert os.path.exists(object_file)
    assert not os.path.exists(incoming_pack_file)


def test_PackRefsAfterStep(repo, local_repo):
    test_file = Path(os.path.join(local_repo.working_dir, "test.txt"))
    test_file.touch()
    local_repo.index.add(test_file)
    local_repo.index.commit("test commit")

    target_loose_ref_count = 15
    loose_ref_count = 0
    while loose_ref_count < target_loose_ref_count:
        loose_ref_count += 1
        local_repo.remotes.origin.push(f"HEAD:refs/heads/test{loose_ref_count}")

    task = PackRefsAfterStep()
    task.run(repo)

    assert len(os.listdir(os.path.join(repo.git_dir, "refs", "heads"))) == 0
    packed_refs_file = os.path.join(repo.git_dir, "packed-refs")
    assert os.path.exists(packed_refs_file)
    with open(packed_refs_file, "r") as f:
        assert (
            len(f.readlines()) == target_loose_ref_count + 1
        )  # First line is a comment

    local_repo.remotes.origin.push(f"HEAD:refs/heads/test{target_loose_ref_count + 1}")
    task.run(repo)
    assert len(os.listdir(os.path.join(repo.git_dir, "refs", "heads"))) == 1
    with open(packed_refs_file, "r") as f:
        assert (
            len(f.readlines()) == target_loose_ref_count + 1
        )  # First line is a comment


@mock.patch("subprocess.run")
def test_gc_executed(mock_subproc_run, site):
    gc = GitGarbageCollection(site, [], [])
    gc.gc()
    mock_subproc_run.assert_called()
    assert mock_subproc_run.call_count == 2


def _mofify_last_modified(file, time_delta):
    file_stat = os.stat(file)
    new_mod_timestamp = datetime.timestamp(
        datetime.fromtimestamp(file_stat.st_mtime) - time_delta
    )
    os.utime(file, (file_stat.st_atime, new_mod_timestamp))
