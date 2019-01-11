import pytest

@pytest.fixture(scope="module")
def container_run(request, docker_client, apache_git_http_backend_image):
  print("Starting apache-git-http-backend-container...")
  container_run = docker_client.containers.run(
    image = apache_git_http_backend_image.id,
    entrypoint = '/bin/bash',
    command = ['-c', 'tail -f /dev/null'],
    user = 'gerrit',
    detach = True,
    auto_remove = True
  )

  def stop_container():
    print("Stopping apache-git-http-backend-container...")
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run


def test_apache_git_http_backend_contains_git(container_run):
  exit_code, _ = container_run.exec_run(
    "which git"
  )
  assert exit_code == 0

def test_apache_git_http_backend_contains_apache2(container_run):
  exit_code, _ = container_run.exec_run(
    "which apache2"
  )
  assert exit_code == 0

def test_apache_git_http_backend_http_site_configured(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /etc/apache2/sites-enabled/git-http-backend.conf"
  )
  assert exit_code == 0

def test_apache_git_http_backend_https_site_configured(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /etc/apache2/sites-enabled/git-https-backend.conf"
  )
  assert exit_code == 0

def test_apache_git_http_backend_contains_start_script(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /var/tools/start"
  )
  assert exit_code == 0

def test_apache_git_http_backend_contains_repo_creation_cgi_script(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /var/cgi/create_repo.sh"
  )
  assert exit_code == 0

def test_apache_git_http_backend_has_entrypoint(apache_git_http_backend_image):
  entrypoint = apache_git_http_backend_image.attrs['ContainerConfig']['Entrypoint']
  assert len(entrypoint) == 1
  assert entrypoint[0] == '/var/tools/start'
