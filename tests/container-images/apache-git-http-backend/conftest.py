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

import os.path
import random
import string
import time

from passlib.apache import HtpasswdFile

import pytest


class GitBackendContainer:
    def __init__(self, docker_client, image, port, apache_credentials_dir):
        self.docker_client = docker_client
        self.image = image
        self.port = port
        self.apache_credentials_dir = apache_credentials_dir

        self.container = None

    def start(self):
        self.container = self.docker_client.containers.run(
            image=self.image.id,
            ports={"80": self.port},
            volumes={
                self.apache_credentials_dir: {
                    "bind": "/var/apache/credentials",
                    "mode": "ro",
                }
            },
            detach=True,
            auto_remove=True,
        )

    def stop(self):
        self.container.stop(timeout=1)


@pytest.fixture(scope="module")
def container_run_factory(
    docker_client, apache_git_http_backend_image, apache_credentials_dir
):
    def run_container(port):
        return GitBackendContainer(
            docker_client,
            apache_git_http_backend_image,
            port,
            str(apache_credentials_dir),
        )

    return run_container


@pytest.fixture(scope="module")
def container_run(container_run_factory, free_port):
    test_setup = container_run_factory(free_port)
    test_setup.start()
    time.sleep(3)

    yield test_setup

    test_setup.stop()


@pytest.fixture(scope="module")
def basic_auth_creds():
    return {"user": "git", "password": "secret"}


@pytest.fixture(scope="module")
def credentials_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("apache_creds")


@pytest.fixture(scope="module")
def mock_htpasswd(credentials_dir, basic_auth_creds):
    htpasswd_file = HtpasswdFile(os.path.join(credentials_dir, ".htpasswd"), new=True)
    htpasswd_file.set_password(basic_auth_creds["user"], basic_auth_creds["password"])
    htpasswd_file.save()


@pytest.fixture(scope="module")
def apache_credentials_dir(credentials_dir, mock_htpasswd):
    return credentials_dir


@pytest.fixture(scope="module")
def base_url(container_run):
    return f"http://localhost:{container_run.port}"


@pytest.fixture(scope="function")
def random_repo_name():
    return "".join(
        [random.choice(string.ascii_letters + string.digits) for n in range(8)]
    )


@pytest.fixture(scope="function")
def repo_creation_url(base_url, random_repo_name):
    return f"{base_url}/new/{random_repo_name}"
