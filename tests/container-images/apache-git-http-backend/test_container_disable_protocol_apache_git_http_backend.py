import os.path
import pytest
import requests
import time

@pytest.fixture(scope="function", params=["NONE", "DISABLE_HTTP", "DISABLE_HTTPS"])
def container_run_with_disabled_protocol(request, container_run_factory):
  if request.param == "NONE":
    container_run = container_run_factory()
  else:
    env = {request.param : "true"}
    container_run = container_run_factory(env)

  time.sleep(3)

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run, request.param

def test_apache_git_http_backend_disable_protocol(
    container_run_with_disabled_protocol,
    container_connection_data,
    apache_credentials_dir,
    basic_auth_creds,
    repo_creation_url):
  _, disabled = container_run_with_disabled_protocol

  def execute_request():
    return requests.get(
      repo_creation_url,
      auth=requests.auth.HTTPBasicAuth(
        basic_auth_creds["user"],
        basic_auth_creds["password"]),
      verify=os.path.join(apache_credentials_dir, "server.crt"))

  if disabled == "DISABLE_HTTP":
    request = execute_request()
    assert request.status_code == \
      201 if container_connection_data["protocol"] == "https" else 500
  elif disabled == "DISABLE_HTTPS":
    if container_connection_data["protocol"] == "http":
      request = execute_request()
      assert request.status_code == 201
    elif container_connection_data["protocol"] == "https":
      with pytest.raises(requests.exceptions.SSLError):
        request = execute_request()
  else:
    request = execute_request()
    assert request.status_code == 201
