import os.path
import pytest

@pytest.fixture(scope="module")
def container_run(request, docker_client, build_images):
  print("Starting base-container...")
  container_run = docker_client.containers.run(
    image = build_images['base'],
    command = 'tail -f /dev/null',
    detach = True,
    auto_remove = True
  )

  def stop_container():
    print("Stopping base-container...")
    container_run.stop()

  request.addfinalizer(stop_container)

  return container_run


def test_base_contains_git(container_run):
  exit_code, _ = container_run.exec_run(
    "which git"
  )
  assert exit_code == 0

def test_base_has_user_gerrit(container_run):
  exit_code, _ = container_run.exec_run(
    "id -u gerrit"
  )
  assert exit_code == 0

def test_base_gerrit_not_root(container_run):
  _, output = container_run.exec_run(
    "id -u gerrit"
  )
  assert output != 0

def test_base_gerrit_no_root_permissions(container_run):
  exit_code, _ = container_run.exec_run(
    "su -c 'rm -rf /bin' gerrit"
  )
  assert exit_code > 0
