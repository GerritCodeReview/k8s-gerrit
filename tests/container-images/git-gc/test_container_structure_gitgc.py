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
def container_run(request, docker_client, gitgc_image):
  print("Starting git-gc-container...")
  container_run = docker_client.containers.run(
    image = gitgc_image.id,
    entrypoint = "/bin/bash",
    command = ["-c", "tail -f /dev/null"],
    user = "gerrit",
    detach = True,
    auto_remove = True
  )

  def stop_container():
    print("Stopping git-gc-container...")
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run


def test_gitgc_inherits_from_base(gitgc_image):
  containsTag = False
  for layer in gitgc_image.history():
    containsTag = layer['Tags'] is not None and "base:latest" in layer['Tags']
    if containsTag:
      break
  assert containsTag

def test_gitgc_log_dir_writable_by_gerrit(container_run):
  exit_code, _ = container_run.exec_run(
    "touch /var/log/git/test.log"
  )
  assert exit_code == 0

def test_gitgc_contains_gc_script(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /var/tools/gc-all.sh"
  )
  assert exit_code == 0

def test_gitgc_has_entrypoint(gitgc_image):
  entrypoint = gitgc_image.attrs["ContainerConfig"]["Entrypoint"]
  assert len(entrypoint) == 1
  assert entrypoint[0] == "/var/tools/gc-all.sh"
