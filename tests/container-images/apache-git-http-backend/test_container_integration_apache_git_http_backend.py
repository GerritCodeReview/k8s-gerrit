# pylint: disable=W0613

# Copyright (C) 2018 The Android Open Source Project
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

from pathlib import Path

import os.path

import pygit2 as git
import pytest
import requests


@pytest.fixture(scope="function")
def repo_dir(tmp_path_factory, random_repo_name):
    return tmp_path_factory.mktemp(random_repo_name)


@pytest.fixture(scope="function")
def mock_repo(repo_dir):
    repo = git.init_repository(repo_dir, False)
    file_name = os.path.join(repo_dir, "test.txt")
    Path(file_name).touch()
    repo.index.add("test.txt")
    repo.index.write()
    # pylint: disable=E1101
    author = git.Signature("Gerrit Review", "gerrit@review.com")
    committer = git.Signature("Gerrit Review", "gerrit@review.com")
    message = "Initial commit"
    tree = repo.index.write_tree()
    repo.create_commit("HEAD", author, committer, message, tree, [])
    return repo


@pytest.mark.docker
@pytest.mark.integration
def test_apache_git_http_backend_repo_creation(
    container_run, htpasswd, repo_creation_url
):
    request = requests.put(
        repo_creation_url,
        auth=requests.auth.HTTPBasicAuth(htpasswd["user"], htpasswd["password"]),
    )
    assert request.status_code == 201


@pytest.mark.docker
@pytest.mark.integration
def test_apache_git_http_backend_repo_creation_fails_without_credentials(
    container_run, repo_creation_url
):
    request = requests.put(repo_creation_url)
    assert request.status_code == 401


@pytest.mark.docker
@pytest.mark.integration
def test_apache_git_http_backend_repo_creation_push_repo(
    container_run, base_url, htpasswd, mock_repo, random_repo_name
):
    container_run.container.exec_run(
        f"git init --bare /var/gerrit/git/{random_repo_name}.git"
    )
    url = f"{base_url}/{random_repo_name}.git"
    url = url.replace("//", f"//{htpasswd['user']}:{htpasswd['password']}@")
    origin = mock_repo.remotes.create("origin", url)
    origin.push(["refs/heads/master:refs/heads/master"])

    remote_refs = origin.ls_remotes()
    assert str(remote_refs[0]["oid"]) == mock_repo.revparse_single("HEAD").hex
