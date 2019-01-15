import pytest

@pytest.fixture(scope="module")
def container_run(request, docker_client, mysql_replication_init_image):
  container_run = docker_client.containers.run(
    image = mysql_replication_init_image.id,
    entrypoint = "/bin/bash",
    command = ["-c", "tail -f /dev/null"],
    detach = True,
    auto_remove = True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run


def test_mysql_replication_init_contains_mysql_client(container_run):
  exit_code, _ = container_run.exec_run(
    "which mysql"
  )
  assert exit_code == 0

def test_mysql_replication_init_contains_start_script(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /var/tools/start"
  )
  assert exit_code == 0

def test_mysql_replication_init_has_entrypoint(mysql_replication_init_image):
  entrypoint = mysql_replication_init_image.attrs["ContainerConfig"]["Entrypoint"]
  assert len(entrypoint) >= 1
  assert "/var/tools/start" in entrypoint
