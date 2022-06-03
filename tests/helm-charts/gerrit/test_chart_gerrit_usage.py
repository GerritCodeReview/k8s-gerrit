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

import pygit2 as git
import pytest
import requests


@pytest.mark.slow
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
class TestGerritChartSetup:
    @pytest.mark.timeout(240)
    def test_create_project_rest(self, default_gerrit_deployment, ldap_credentials):
        create_project_url = (
            f"http://{default_gerrit_deployment.hostname}/a/projects/test"
        )
        response = None

        while not response:
            try:
                response = requests.put(
                    create_project_url,
                    auth=("gerrit-admin", ldap_credentials["gerrit-admin"]),
                )
            except requests.exceptions.ConnectionError:
                break

        assert response.status_code == 201

    def test_cloning_project(self, tmp_path_factory, default_gerrit_deployment):
        clone_dest = tmp_path_factory.mktemp("gerrit_chart_clone_test")
        repo_url = f"http://{default_gerrit_deployment.hostname}/test.git"
        repo = git.clone_repository(repo_url, clone_dest)
        assert repo.path == os.path.join(clone_dest, ".git/")
