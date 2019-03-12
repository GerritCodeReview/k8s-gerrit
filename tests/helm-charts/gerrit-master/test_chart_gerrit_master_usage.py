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

import git
import pytest
import requests

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartSetup(object):
  def test_create_project_rest(self, request, test_cluster,
                               gerrit_master_h2_ready_deployment):
    create_project_url = "http://master.%s/a/projects/test" % (
      request.config.getoption("--ingress-url"))
    response = requests.put(create_project_url, auth=('admin', 'secret'))
    assert response.status_code == 201

  def test_cloning_project(self, request, tmp_path_factory, test_cluster,
                           gerrit_master_h2_ready_deployment):
    clone_dest = tmp_path_factory.mktemp("gerrit_master_chart_clone_test")
    repo_url = "http://master.%s/test.git" % (
      request.config.getoption("--ingress-url"))
    repo = git.Repo.clone_from(repo_url, clone_dest)
    assert repo.git_dir == os.path.join(clone_dest, ".git")
