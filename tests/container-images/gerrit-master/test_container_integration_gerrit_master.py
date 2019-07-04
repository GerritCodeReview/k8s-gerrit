# pylint: disable=W0613, E1101

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

import re
import time

import pytest
import requests


@pytest.fixture(scope="module")
def tmp_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("gerrit-master-test")


@pytest.fixture(scope="class")
def container_run(
    docker_client,
    docker_network,
    tmp_dir,
    gerrit_master_image,
    gerrit_container_factory,
):
    configs = {
        "gerrit.config": """
      [gerrit]
        basePath = git

      [httpd]
        listenUrl = http://*:8081

      [test]
        success = True
      """,
        "secure.config": """
      [test]
        success = True
      """,
        "replication.config": """
      [test]
        success = True
      """,
    }
    test_setup = gerrit_container_factory(
        docker_client, docker_network, tmp_dir, gerrit_master_image, configs, 8081
    )
    test_setup.start()

    yield test_setup.gerrit_container

    test_setup.stop()


@pytest.fixture(params=["gerrit.config", "secure.config", "replication.config"])
def config_file_to_test(request):
    return request.param


@pytest.mark.docker
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.slow
class TestGerritMasterStartScript:
    @pytest.mark.timeout(60)
    def test_gerrit_master_gerrit_starts_up(self, container_run):
        def wait_for_gerrit_start():
            log = container_run.logs().decode("utf-8")
            return re.search(r"Gerrit Code Review .+ ready", log)

        while not wait_for_gerrit_start:
            continue

    def test_gerrit_master_custom_gerrit_config_available(
        self, container_run, config_file_to_test
    ):
        exit_code, output = container_run.exec_run(
            "git config --file=/var/gerrit/etc/%s --get test.success"
            % config_file_to_test
        )
        output = output.decode("utf-8").strip()
        assert exit_code == 0
        assert output == "True"

    @pytest.mark.timeout(60)
    def test_gerrit_master_httpd_is_responding(self, container_run):
        status = None
        while not status == 200:
            try:
                response = requests.get("http://localhost:8081")
                status = response.status_code
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert response.status_code == 200
        assert re.search(r'content="Gerrit Code Review"', response.text)
