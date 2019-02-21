# pylint: disable=E1101

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

import os
import re

import pytest

@pytest.fixture(scope="function")
def temp_index_dir(tmp_path_factory):
  return tmp_path_factory.mktemp("gerrit-index-test")

@pytest.fixture(scope="function")
def container_run_endless(request, docker_client, gerrit_init_image, temp_index_dir):
  container_run = docker_client.containers.run(
    image=gerrit_init_image.id,
    entrypoint="/bin/bash",
    command=["-c", "tail -f /dev/null"],
    volumes={
      str(temp_index_dir): {
        "bind": "/var/gerrit/index",
        "mode": "rw"}
    },
    user="gerrit",
    detach=True,
    auto_remove=True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run

@pytest.mark.incremental
class TestGerritReindex(object):

  def test_gerrit_init_skips_reindexing_on_fresh_site(self, container_run_endless):
    container_run_endless.exec_run("/var/tools/gerrit_init.py -s /var/gerrit")
    container_run_endless.exec_run("rm -f /var/war/gerrit.war")
    exit_code, _ = container_run_endless.exec_run(
      "/var/tools/gerrit_reindex.py -s /var/gerrit")
    assert exit_code == 0

  def test_gerrit_init_fixes_missing_index_config(
      self, container_run_endless, temp_index_dir):
    container_run_endless.exec_run("/var/tools/gerrit_init.py -s /var/gerrit")
    os.remove(os.path.join(temp_index_dir, "gerrit_index.config"))
    exit_code, _ = container_run_endless.exec_run(
      "/var/tools/gerrit_reindex.py -s /var/gerrit")
    assert exit_code == 0
    exit_code, _ = container_run_endless.exec_run(
      "/var/gerrit/bin/gerrit.sh start")
    assert exit_code == 0

  def test_gerrit_init_fixes_unready_indices(
      self, container_run_endless):
    container_run_endless.exec_run("/var/tools/gerrit_init.py -s /var/gerrit")
    _, project_index = container_run_endless.exec_run(
      "git config -f /var/gerrit/index/gerrit_index.config " + \
      "--name-only " + \
      "--get-regexp index.projects_[0-9]+")
    container_run_endless.exec_run(
      "git config -f /var/gerrit/index/gerrit_index.config %s false" % \
        project_index.decode().strip())
    exit_code, _ = container_run_endless.exec_run(
      "/var/tools/gerrit_reindex.py -s /var/gerrit")
    assert exit_code == 0
    exit_code, _ = container_run_endless.exec_run(
      "/var/gerrit/bin/gerrit.sh start")
    assert exit_code == 0

  def test_gerrit_init_fixes_outdated_indices(
      self, container_run_endless, temp_index_dir):
    container_run_endless.exec_run("/var/tools/gerrit_init.py -s /var/gerrit")
    _, project_index = container_run_endless.exec_run(
      "git config -f /var/gerrit/index/gerrit_index.config " + \
      "--name-only " + \
      "--get-regexp index.projects_[0-9]+")
    project_index_version = re.findall(r"\d+", project_index.decode())[0]
    os.rename(
      os.path.join(temp_index_dir, "projects_%s" % project_index_version),
      os.path.join(temp_index_dir, "projects_{0:04d}".format(int(project_index_version)-1)))
    exit_code, output = container_run_endless.exec_run(
      "/var/tools/gerrit_reindex.py -s /var/gerrit")
    print(output)
    assert exit_code == 0
    exit_code, output = container_run_endless.exec_run(
      "/var/gerrit/bin/gerrit.sh start")
    print(output)
    assert exit_code == 0
