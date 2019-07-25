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
def container_run(docker_client, container_endless_run_factory, gerrit_init_image):
  container_run = container_endless_run_factory(docker_client, gerrit_init_image)
  yield container_run
  container_run.stop(timeout=1)


@pytest.fixture(scope="function",
                params=["/var/tools/gerrit_init.py",
                        "/var/tools/git_config_parser.py",
                        "/var/tools/init_config.py",
                        "/var/tools/validate_db.py"])
def expected_script(request):
  return request.param

@pytest.fixture(scope="function",
                params=["python3",
                        "pip3",
                        "mysql"])
def expected_tool(request):
  return request.param

@pytest.fixture(scope="function",
                params=["pymysql",
                        "pyyaml",
                        "sqlalchemy"])
def expected_pip_package(request):
  return request.param

# pylint: disable=E1101
def test_gerrit_init_inherits_from_gerrit_base(gerrit_init_image):
  assert utils.check_if_ancestor_image_is_inherited(
    gerrit_init_image, "gerrit-base:latest")

def test_gerrit_init_contains_expected_scripts(container_run, expected_script):
  exit_code, _ = container_run.exec_run("test -f %s" % expected_script)
  assert exit_code == 0

def test_gerrit_init_contains_expected_tools(container_run, expected_tool):
  exit_code, _ = container_run.exec_run("which %s" % expected_tool)
  assert exit_code == 0

def test_gerrit_init_contains_expected_pip_packages(container_run, expected_pip_package):
  exit_code, _ = container_run.exec_run("pip3 show %s" % expected_pip_package)
  assert exit_code == 0

def test_gerrit_init_has_entrypoint(gerrit_init_image):
  entrypoint = gerrit_init_image.attrs["ContainerConfig"]["Entrypoint"]
  assert len(entrypoint) >= 1
  assert entrypoint == ["/var/tools/gerrit_init.py", "-s", "/var/gerrit"]
