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

import pytest


@pytest.fixture(scope="module")
def container_run(docker_client, container_endless_run_factory, base_image):
    container_run = container_endless_run_factory(docker_client, base_image)
    yield container_run
    container_run.stop(timeout=1)


@pytest.mark.docker
@pytest.mark.structure
def test_base_contains_git(container_run):
    exit_code, _ = container_run.exec_run("which git")
    assert exit_code == 0


@pytest.mark.docker
@pytest.mark.structure
def test_base_has_non_root_user_gerrit(container_run):
    exit_code, output = container_run.exec_run("id -u gerrit")
    assert exit_code == 0
    uid = int(output.strip().decode("utf-8"))
    assert uid != 0


@pytest.mark.docker
@pytest.mark.structure
def test_base_gerrit_no_root_permissions(container_run):
    exit_code, _ = container_run.exec_run("su -c 'rm -rf /bin' gerrit")
    assert exit_code > 0
