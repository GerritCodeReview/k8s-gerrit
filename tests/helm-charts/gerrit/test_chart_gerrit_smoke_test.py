# pylint: disable=W0613

# Copyright (C) 2019 The Android Open Source Project
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

import os.path
import re
import shutil

from pathlib import Path

import pygit2 as git
import pytest
import requests

import utils


@pytest.fixture(scope="module")
def admin_creds(request):
    user = request.config.getoption("--gerrit-user")
    pwd = request.config.getoption("--gerrit-pwd")
    return user, pwd


@pytest.fixture(scope="class")
def tmp_test_repo(request, tmp_path_factory):
    tmp_dir = tmp_path_factory.mktemp("gerrit_chart_clone_test")
    yield tmp_dir
    shutil.rmtree(tmp_dir)


@pytest.fixture(scope="class")
def random_repo_name():
    return utils.create_random_string(16)


@pytest.mark.smoke
def test_ui_connection(request):
    response = requests.get(request.config.getoption("--ingress-url"))
    assert response.status_code == requests.codes["OK"]
    assert re.search(r'content="Gerrit Code Review"', response.text)


@pytest.mark.smoke
@pytest.mark.incremental
class TestGerritRestGitCalls:
    def _is_delete_project_plugin_enabled(self, gerrit_url, user, pwd):
        url = f"{gerrit_url}/a/plugins/delete-project/gerrit~status"
        response = requests.get(url, auth=(user, pwd))
        return response.status_code == requests.codes["OK"]

    def test_create_project_rest(self, request, random_repo_name, admin_creds):
        ingress_url = request.config.getoption("--ingress-url")
        create_project_url = f"{ingress_url}/a/projects/{random_repo_name}"
        response = requests.put(create_project_url, auth=admin_creds)
        assert response.status_code == requests.codes["CREATED"]

    def test_cloning_project(
        self, request, tmp_test_repo, random_repo_name, admin_creds
    ):
        repo_url = f"{request.config.getoption('--ingress-url')}/{random_repo_name}.git"
        repo_url = repo_url.replace("//", f"//{admin_creds[0]}:{admin_creds[1]}@")
        repo = git.clone_repository(repo_url, tmp_test_repo)
        assert repo.path == os.path.join(tmp_test_repo, ".git/")

    def test_push_commit(self, tmp_test_repo):
        repo = git.Repository(tmp_test_repo)
        file_name = os.path.join(tmp_test_repo, "test.txt")
        Path(file_name).touch()
        repo.index.add("test.txt")
        repo.index.write()
        # pylint: disable=E1101
        author = git.Signature("Gerrit Review", "gerrit@review.com")
        committer = git.Signature("Gerrit Review", "gerrit@review.com")
        message = "Initial commit"
        tree = repo.index.write_tree()
        repo.create_commit("HEAD", author, committer, message, tree, [])

        origin = repo.remotes["origin"]
        origin.push(["refs/heads/master:refs/heads/master"])

        remote_refs = origin.ls_remotes()
        assert remote_refs[0]["name"] == repo.revparse_single("HEAD").hex

    def test_delete_project_rest(self, request, random_repo_name, admin_creds):
        ingress_url = request.config.getoption("--ingress-url")
        if not self._is_delete_project_plugin_enabled(
            ingress_url, admin_creds[0], admin_creds[1]
        ):
            pytest.skip(
                "Delete-project plugin not installed."
                + f"The test project ({random_repo_name}) has to be deleted manually."
            )
        project_url = (
            f"{ingress_url}/a/projects/{random_repo_name}/delete-project~delete"
        )
        response = requests.post(project_url, auth=admin_creds)
        assert response.status_code == requests.codes["NO_CONTENT"]
