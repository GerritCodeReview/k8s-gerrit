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
