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

import git
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
  tmp_dir = tmp_path_factory.mktemp("gerrit_master_chart_clone_test")
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
class TestGerritMasterRestGitCalls:
  def _is_delete_project_plugin_enabled(self, gerrit_url, user, pwd):
    url = "%s/a/plugins/delete-project/gerrit~status" % gerrit_url
    response = requests.get(url, auth=(user, pwd))
    return response.status_code == requests.codes["OK"]

  def test_create_project_rest(self, request, random_repo_name, admin_creds):
    create_project_url = "%s/a/projects/%s" % (
      request.config.getoption("--ingress-url"), random_repo_name)
    response = requests.put(
      create_project_url,
      auth=admin_creds)
    assert response.status_code == requests.codes["CREATED"]

  def test_cloning_project(self, request, tmp_test_repo, random_repo_name, admin_creds):
    repo_url = "%s/%s.git" % (
      request.config.getoption("--ingress-url"), random_repo_name)
    repo_url = repo_url.replace("//", "//%s:%s@" % admin_creds)
    repo = git.Repo.clone_from(repo_url, tmp_test_repo)
    assert repo.git_dir == os.path.join(tmp_test_repo, ".git")

  def test_push_commit(self, request, tmp_test_repo, random_repo_name):
    repo = git.Repo.init(tmp_test_repo)
    file_name = os.path.join(tmp_test_repo, "test.txt")
    Path(file_name).touch()
    repo.index.add([file_name])
    repo.index.commit("initial commit")

    origin = repo.remote(name='origin')
    with repo.git.custom_environment(GIT_SSL_NO_VERIFY="true"):
      result = origin.push(refspec="master:master")
      assert result

    git_cmd = git.cmd.Git()
    url = "%s/%s.git" % (
      request.config.getoption("--ingress-url"), random_repo_name)
    with git_cmd.custom_environment(GIT_SSL_NO_VERIFY="true"):
      for ref in git_cmd.ls_remote(url).split("\n"):
        hash_ref_list = ref.split("\t")
        if hash_ref_list[1] == "HEAD":
          assert repo.head.object.hexsha == hash_ref_list[0]
          return
    pytest.fail()

  def test_delete_project_rest(self, request, random_repo_name, admin_creds):
    if not self._is_delete_project_plugin_enabled(
        request.config.getoption("--ingress-url"),
        admin_creds[0],
        admin_creds[1]
      ):
      pytest.skip(
        "Delete-project plugin not installed." + \
        "The test project (%s) has to be deleted manually." % random_repo_name)
    project_url = "%s/a/projects/%s/delete-project~delete" % (
      request.config.getoption("--ingress-url"), random_repo_name)
    response = requests.post(
      project_url,
      auth=admin_creds)
    assert response.status_code == requests.codes["NO_CONTENT"]
