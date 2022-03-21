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
def container_run(docker_client, container_endless_run_factory, gitgc_image):
    container_run = container_endless_run_factory(docker_client, gitgc_image)
    yield container_run
    container_run.stop(timeout=1)


# pylint: disable=E1101
@pytest.mark.structure
def test_gitgc_inherits_from_base(gitgc_image):
    assert utils.check_if_ancestor_image_is_inherited(gitgc_image, "base:latest")


@pytest.mark.docker
@pytest.mark.structure
def test_gitgc_log_dir_writable_by_gerrit(container_run):
    exit_code, _ = container_run.exec_run("touch /var/log/git/test.log")
    assert exit_code == 0


@pytest.mark.docker
@pytest.mark.structure
def test_gitgc_contains_gc_script(container_run):
    exit_code, _ = container_run.exec_run("test -f /var/tools/gc.sh")
    assert exit_code == 0


@pytest.mark.structure
def test_gitgc_has_entrypoint(gitgc_image):
    entrypoint = gitgc_image.attrs["ContainerConfig"]["Entrypoint"]
    assert len(entrypoint) == 1
    assert entrypoint[0] == "/var/tools/gc.sh"
