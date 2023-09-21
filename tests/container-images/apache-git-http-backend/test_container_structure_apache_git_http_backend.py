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
import utils


# pylint: disable=E1101
@pytest.mark.structure
def test_apache_git_http_backend_inherits_from_base(apache_git_http_backend_image):
    assert utils.check_if_ancestor_image_is_inherited(
        apache_git_http_backend_image, "base:latest"
    )


@pytest.mark.docker
@pytest.mark.structure
def test_apache_git_http_backend_contains_apache2(container_run):
    exit_code, _ = container_run.container.exec_run("which httpd")
    assert exit_code == 0


@pytest.mark.docker
@pytest.mark.structure
def test_apache_git_http_backend_http_site_configured(container_run):
    exit_code, _ = container_run.container.exec_run(
        "test -f /etc/apache2/conf.d/git-http-backend.conf"
    )
    assert exit_code == 0


@pytest.mark.docker
@pytest.mark.structure
def test_apache_git_http_backend_contains_start_script(container_run):
    exit_code, _ = container_run.container.exec_run("test -f /var/tools/start")
    assert exit_code == 0


@pytest.mark.docker
@pytest.mark.structure
def test_apache_git_http_backend_contains_repo_creation_cgi_script(container_run):
    exit_code, _ = container_run.container.exec_run("test -f /var/cgi/project_admin.sh")
    assert exit_code == 0


@pytest.mark.structure
def test_apache_git_http_backend_has_entrypoint(apache_git_http_backend_image):
    entrypoint = apache_git_http_backend_image.attrs["ContainerConfig"]["Entrypoint"]
    assert len(entrypoint) == 2
    assert entrypoint[1] == "/var/tools/start"
