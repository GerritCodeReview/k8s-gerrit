import os.path
import pytest

@pytest.fixture(scope="module")
def container_run(request, docker_client, gitgc_image):
  print("Starting git-gc-container...")
  container_run = docker_client.containers.run(
    image = gitgc_image.id,
    entrypoint = '/bin/bash',
    command = ['-c', 'tail -f /dev/null'],
    user = 'gerrit',
    detach = True,
    auto_remove = True
  )

  def stop_container():
    print("Stopping git-gc-container...")
    container_run.stop()

  request.addfinalizer(stop_container)

  return container_run


def test_gitgc_contains_git(container_run):
  exit_code, _ = container_run.exec_run(
    "which git"
  )
  assert exit_code == 0

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
  entrypoint = gitgc_image.attrs['ContainerConfig']['Entrypoint']
  assert len(entrypoint) == 1
  assert entrypoint[0] == '/var/tools/gc-all.sh'
