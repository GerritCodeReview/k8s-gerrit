# pylint: disable=W0613

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

from pathlib import Path

import os.path

import git
import pytest
import requests


@pytest.fixture(scope="module", params=["http", "https"])
def base_url(request):
    port = 8080 if request.param == "http" else 8443
    return "{protocol}://localhost:{port}".format(protocol=request.param, port=port)


@pytest.fixture(scope="function")
def repo_dir(tmp_path_factory, random_repo_name):
    return tmp_path_factory.mktemp(random_repo_name)


@pytest.fixture(scope="function")
def mock_repo(repo_dir):
    repo = git.Repo.init(repo_dir)
    file_name = os.path.join(repo_dir, "test.txt")
    Path(file_name).touch()
    repo.index.add([file_name])
    repo.index.commit("initial commit")
    return repo


@pytest.mark.docker
def test_apache_git_http_backend_apache_running(
    container_run, base_url, apache_credentials_dir
):
    request = requests.get(
        base_url, verify=os.path.join(apache_credentials_dir, "server.crt")
    )
    assert request.status_code == 200


@pytest.mark.docker
def test_apache_git_http_backend_repo_creation(
    container_run, apache_credentials_dir, basic_auth_creds, repo_creation_url
):
    request = requests.get(
        repo_creation_url,
        auth=requests.auth.HTTPBasicAuth(
            basic_auth_creds["user"], basic_auth_creds["password"]
        ),
        verify=os.path.join(apache_credentials_dir, "server.crt"),
    )
    assert request.status_code == 201


@pytest.mark.docker
def test_apache_git_http_backend_repo_creation_fails_without_credentials(
    container_run, apache_credentials_dir, repo_creation_url
):
    request = requests.get(
        repo_creation_url, verify=os.path.join(apache_credentials_dir, "server.crt")
    )
    assert request.status_code == 401


@pytest.mark.docker
def test_apache_git_http_backend_repo_creation_fails_wrong_fs_permissions(
    container_run, apache_credentials_dir, basic_auth_creds, repo_creation_url
):
    container_run.exec_run("chown -R root:root /var/gerrit/git")
    request = requests.get(
        repo_creation_url,
        auth=requests.auth.HTTPBasicAuth(
            basic_auth_creds["user"], basic_auth_creds["password"]
        ),
        verify=os.path.join(apache_credentials_dir, "server.crt"),
    )
    container_run.exec_run("chown -R gerrit:users /var/gerrit/git")
    assert request.status_code == 500


@pytest.mark.docker
def test_apache_git_http_backend_repo_creation_push_repo(
    container_run,
    base_url,
    apache_credentials_dir,
    basic_auth_creds,
    mock_repo,
    random_repo_name,
):
    container_run.exec_run(
        "su -c 'git init --bare /var/gerrit/git/%s.git' gerrit" % random_repo_name
    )
    url = "%s/git/%s.git" % (base_url, random_repo_name)
    url = url.replace(
        "//", "//%s:%s@" % (basic_auth_creds["user"], basic_auth_creds["password"])
    )
    origin = mock_repo.create_remote("origin", url)
    assert origin.exists()
    with mock_repo.git.custom_environment(GIT_SSL_NO_VERIFY="true"):
        origin.fetch()
        result = origin.push(refspec="master:master")
        assert result
    remote_refs = {}
    git_cmd = git.cmd.Git()
    with git_cmd.custom_environment(GIT_SSL_NO_VERIFY="true"):
        for ref in git_cmd.ls_remote(url).split("\n"):
            hash_ref_list = ref.split("\t")
            remote_refs[hash_ref_list[1]] = hash_ref_list[0]
        assert remote_refs["HEAD"] == mock_repo.head.object.hexsha
