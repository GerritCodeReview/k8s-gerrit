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

import time

import os.path
import pytest
import requests


@pytest.fixture(scope="function", params=["NONE", "DISABLE_HTTP", "DISABLE_HTTPS"])
def container_run_with_disabled_protocol(request, container_run_factory):
    if request.param == "NONE":
        container_run = container_run_factory()
    else:
        env = {request.param: "true"}
        container_run = container_run_factory(env)

    time.sleep(3)

    def stop_container():
        container_run.stop(timeout=1)

    request.addfinalizer(stop_container)

    return container_run, request.param


@pytest.mark.docker
@pytest.mark.integration
@pytest.mark.slow
def test_apache_git_http_backend_disable_protocol(
    container_run_with_disabled_protocol,
    container_connection_data,
    apache_credentials_dir,
    basic_auth_creds,
    repo_creation_url,
):
    _, disabled = container_run_with_disabled_protocol

    def execute_request():
        return requests.get(
            repo_creation_url,
            auth=requests.auth.HTTPBasicAuth(
                basic_auth_creds["user"], basic_auth_creds["password"]
            ),
            verify=os.path.join(apache_credentials_dir, "server.crt"),
        )

    if disabled == "DISABLE_HTTP":
        request = execute_request()
        assert (
            request.status_code == 201
            if container_connection_data["protocol"] == "https"
            else 500
        )
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
