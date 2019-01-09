import pytest
import re

@pytest.fixture(scope="module")
def container_run(request, docker_client, gerrit_base_image):
  print("Starting gerrit-base-container...")
  container_run = docker_client.containers.run(
    image = gerrit_base_image.id,
    entrypoint = '/bin/bash',
    command = ['-c', 'tail -f /dev/null'],
    user = 'gerrit',
    detach = True,
    auto_remove = True
  )

  def stop_container():
    print("Stopping base-container...")
    container_run.stop()

  request.addfinalizer(stop_container)

  return container_run


def test_gerrit_base_contains_git(container_run):
  exit_code, _ = container_run.exec_run(
    "which git"
  )
  assert exit_code == 0

def test_gerrit_base_contains_java8(container_run):
  exit_code, output = container_run.exec_run(
    "java -version"
  )
  output = output.strip().decode('utf-8')
  assert re.search(re.compile('openjdk version "1.8.[0-9]_[0-9]+"'), output)

def test_gerrit_base_java_path(container_run):
  exit_code, output = container_run.exec_run(
    '/bin/bash -c "readlink -f $(which java)"'
  )
  assert exit_code == 0
  output = output.strip().decode('utf-8')
  assert output == "/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java"

def test_gerrit_base_contains_gerrit_war(container_run):
  exit_code, _ = container_run.exec_run(
    'test -f /var/war/gerrit.war'
  )
  assert exit_code == 0

  exit_code, _ = container_run.exec_run(
    'test -f /var/gerrit/bin/gerrit.war'
  )
  assert exit_code == 0

def test_gerrit_base_war_contains_gerrit(container_run):
  exit_code, output = container_run.exec_run(
    'java -jar /var/war/gerrit.war version'
  )
  assert exit_code == 0
  output = output.strip().decode('utf-8')
  assert re.search(re.compile('gerrit version.*'), output)

  exit_code, output = container_run.exec_run(
    'java -jar /var/gerrit/bin/gerrit.war version'
  )
  assert exit_code == 0
  output = output.strip().decode('utf-8')
  assert re.search(re.compile('gerrit version.*'), output)

def test_gerrit_base_site_permissions(container_run):
  exit_code, _ = container_run.exec_run(
    'test -O /var/gerrit'
  )
  assert exit_code == 0

def test_gerrit_base_war_dir_permissions(container_run):
  exit_code, _ = container_run.exec_run(
    'test -O /var/war'
  )
  assert exit_code == 0
