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

import re

from docker.errors import NotFound

import pytest

import utils

@pytest.fixture(scope="class")
def container_run_default(
    request, docker_client, gerrit_init_image, tmp_path_factory):
  tmp_site_dir = tmp_path_factory.mktemp('gerrit_site')
  container_run = docker_client.containers.run(
    image=gerrit_init_image.id,
    user="gerrit",
    volumes={
      tmp_site_dir: {
        "bind": "/var/gerrit",
        "mode": "rw"
      }
    },
    detach=True,
    auto_remove=True
  )

  def stop_container():
    try:
      container_run.stop(timeout=1)
    except NotFound:
      print("Container already stopped.")

  request.addfinalizer(stop_container)

  return container_run

@pytest.fixture(scope="class")
def container_run_endless(
    request, docker_client, gerrit_init_image, tmp_path_factory):
  tmp_site_dir = tmp_path_factory.mktemp('gerrit_site')
  container_run = docker_client.containers.run(
    image=gerrit_init_image.id,
    entrypoint="/bin/bash",
    command=["-c", "tail -f /dev/null"],
    user="gerrit",
    volumes={
      tmp_site_dir: {
        "bind": "/var/gerrit",
        "mode": "rw"
      }
    },
    detach=True,
    auto_remove=True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run

@pytest.mark.incremental
class TestGerritInitEmptySite(object):
  def test_gerrit_init_gerrit_is_initialized(self, container_run_default):
    def wait_for_init_success_message():
      log = container_run_default.logs().decode("utf-8")
      return log, re.search(r"Initialized /var/gerrit", log)

    finished_in_time, _ = utils.exec_fn_with_timeout(wait_for_init_success_message)
    assert finished_in_time

  def test_gerrit_init_exits_after_init(self, container_run_default):
    def wait_for_container_exit():
      try:
        container_run_default.reload()
        return None, False
      except NotFound:
        return None, True

    finished_in_time, _ = utils.exec_fn_with_timeout(wait_for_container_exit)
    assert finished_in_time
    assert container_run_default.attrs["State"]["ExitCode"] == 0

@pytest.mark.incremental
class TestGerritInitEmptySiteEndlessRun(object):
  def test_gerrit_init_plugins_are_installed(self, container_run_endless):
    exit_code, _ = container_run_endless.exec_run(
      "/var/tools/gerrit_init.py -s /var/gerrit -p replication -p reviewnotes")
    assert exit_code == 0
    cmd = "/bin/bash -c '" + \
      "test -f /var/gerrit/plugins/replication.jar && " + \
      "test -f /var/gerrit/plugins/reviewnotes.jar'"
    exit_code, _ = container_run_endless.exec_run(cmd)
    assert exit_code == 0

  def test_gerrit_init_plugins_are_installed_in_existing_site(
      self, container_run_endless):
    exit_code, _ = container_run_endless.exec_run(
      "/var/tools/gerrit_init.py -s /var/gerrit -p download-commands")
    assert exit_code == 0

    cmd = "/bin/bash -c '" + \
      "test -f /var/gerrit/plugins/download-commands.jar'"
    exit_code, _ = container_run_endless.exec_run(cmd)
    assert exit_code == 0

    cmd = "/bin/bash -c '" + \
      "test -f /var/gerrit/plugins/reviewnotes.jar'"
    exit_code, _ = container_run_endless.exec_run(cmd)
    assert exit_code == 1
