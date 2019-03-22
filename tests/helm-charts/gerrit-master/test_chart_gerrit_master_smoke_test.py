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

import os.path
import re

from pathlib import Path

import git
import pytest
import requests

@pytest.fixture(scope="class")
def tmp_test_repo(tmp_path_factory):
  return tmp_path_factory.mktemp("gerrit_master_chart_clone_test")

@pytest.mark.smoke
def test_ui_connection(request):
  response = requests.get(request.config.getoption("--ingress-url"))
  assert response.status_code == 200
  assert re.search(r'content="Gerrit Code Review"', response.text)

@pytest.mark.smoke
@pytest.mark.incremental
class TestGerritMasterRestGitCalls:
  def test_create_project_rest(self, request):
    create_project_url = "%s/a/projects/test" % (
      request.config.getoption("--ingress-url"))
    response = requests.put(create_project_url, auth=('admin', 'secret'))
    assert response.status_code == 201

  def test_cloning_project(self, request, tmp_test_repo):
    repo_url = "%s/test.git" % (request.config.getoption("--ingress-url"))
    repo_url = repo_url.replace("//", "//%s:%s@" % (
      "admin",
      "secret"))
    repo = git.Repo.clone_from(repo_url, tmp_test_repo)
    assert repo.git_dir == os.path.join(tmp_test_repo, ".git")

  def test_push_change(self, request, tmp_test_repo):
    repo = git.Repo.init(tmp_test_repo)
    file_name = os.path.join(tmp_test_repo, "test.txt")
    Path(file_name).touch()
    repo.index.add([file_name])
    repo.index.commit("initial commit")
    origin = repo.remote(name='origin')
    with repo.git.custom_environment(GIT_SSL_NO_VERIFY="true"):
      result = origin.push(refspec="master:master")
      assert result
    remote_refs = {}
    git_cmd = git.cmd.Git()
    url = "%s/test.git" % (request.config.getoption("--ingress-url"))
    with git_cmd.custom_environment(GIT_SSL_NO_VERIFY="true"):
      for ref in git_cmd.ls_remote(url).split("\n"):
        hash_ref_list = ref.split("\t")
        remote_refs[hash_ref_list[1]] = hash_ref_list[0]
      assert remote_refs["HEAD"] == repo.head.object.hexsha

  def test_delete_project_rest(self, request):
    project_url = "%s/a/projects/test" % (
      request.config.getoption("--ingress-url"))
    response = requests.delete(project_url, auth=('admin', 'secret'))
    assert response.status_code == 204
