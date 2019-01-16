import pytest
import re

@pytest.fixture(scope="module")
def container_run(request, docker_client, gerrit_master_image):
  container_run = docker_client.containers.run(
    image = gerrit_master_image.id,
    user = "gerrit",
    detach = True,
    auto_remove = True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run


def test_gerrit_master_contains_start_script(container_run):
  exit_code, _ = container_run.exec_run("test -f /var/tools/start")
  assert exit_code == 0
