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

import utils


@pytest.fixture(scope="module")
def container_run(docker_client, container_endless_run_factory, gerrit_slave_image):
    container_run = container_endless_run_factory(docker_client, gerrit_slave_image)
    yield container_run
    container_run.stop(timeout=1)


@pytest.fixture(scope="function", params=["/var/tools/start"])
def expected_script(request):
    return request.param


# pylint: disable=E1101
@pytest.mark.structure
def test_gerrit_slave_inherits_from_gerrit_base(gerrit_slave_image):
    assert utils.check_if_ancestor_image_is_inherited(
        gerrit_slave_image, "gerrit-base:latest"
    )


@pytest.mark.docker
@pytest.mark.structure
def test_gerrit_slave_contains_expected_scripts(container_run, expected_script):
    exit_code, _ = container_run.exec_run("test -f %s" % expected_script)
    assert exit_code == 0


@pytest.mark.docker
@pytest.mark.structure
def test_gerrit_slave_contains_initialized_gerrit_site(container_run):
    exit_code, _ = container_run.exec_run("/var/gerrit/bin/gerrit.sh check")
    assert exit_code == 3


@pytest.mark.docker
@pytest.mark.structure
def test_gerrit_slave_gerrit_is_configured_slave(container_run):
    exit_code, output = container_run.exec_run(
        "git config -f /var/gerrit/etc/gerrit.config --get container.slave"
    )
    output = output.decode("utf-8").strip().lower()
    assert exit_code == 0
    assert output == "true"
