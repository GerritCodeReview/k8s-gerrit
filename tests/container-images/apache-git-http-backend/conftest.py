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

from OpenSSL import crypto
from passlib.apache import HtpasswdFile

import pytest


@pytest.fixture(scope="module")
def container_run_factory(
    docker_client, apache_git_http_backend_image, apache_credentials_dir
):
    def run_container(env=None):
        return docker_client.containers.run(
            image=apache_git_http_backend_image.id,
            ports={"80": "8080", "443": "8443"},
            volumes={
                str(apache_credentials_dir): {
                    "bind": "/var/apache/credentials",
                    "mode": "ro",
                }
            },
            environment=env,
            detach=True,
            auto_remove=True,
        )

    return run_container


@pytest.fixture(scope="module")
def container_run(request, container_run_factory):
    container_run = container_run_factory()
    time.sleep(3)

    def stop_container():
        container_run.stop(timeout=1)

    request.addfinalizer(stop_container)
    return container_run


@pytest.fixture(scope="module")
def basic_auth_creds():
    return {"user": "git", "password": "secret"}


@pytest.fixture(scope="module")
def credentials_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("apache_creds")


@pytest.fixture(scope="module")
def mock_certificates(credentials_dir):
    key = crypto.PKey()
    key.generate_key(crypto.TYPE_RSA, 1024)

    cert = crypto.X509()
    cert.get_subject().C = "DE"
    cert.get_subject().O = "Gerrit"
    cert.get_subject().CN = "localhost"
    cert.add_extensions(
        [crypto.X509Extension(b"subjectAltName", False, b"DNS:localhost")]
    )
    cert.gmtime_adj_notBefore(0)
    cert.gmtime_adj_notAfter(10 * 365 * 24 * 60 * 60)
    cert.set_issuer(cert.get_subject())
    cert.set_pubkey(key)
    cert.sign(key, "sha1")

    open(os.path.join(credentials_dir, "server.crt"), "wb").write(
        crypto.dump_certificate(crypto.FILETYPE_PEM, cert)
    )
    open(os.path.join(credentials_dir, "server.key"), "wb").write(
        crypto.dump_privatekey(crypto.FILETYPE_PEM, key)
    )


@pytest.fixture(scope="module")
def mock_htpasswd(credentials_dir, basic_auth_creds):
    htpasswd_file = HtpasswdFile(os.path.join(credentials_dir, ".htpasswd"), new=True)
    htpasswd_file.set_password(basic_auth_creds["user"], basic_auth_creds["password"])
    htpasswd_file.save()


@pytest.fixture(scope="module")
def apache_credentials_dir(credentials_dir, mock_certificates, mock_htpasswd):
    return credentials_dir


@pytest.fixture(scope="module", params=["http", "https"])
def container_connection_data(request):
    port = 8080 if request.param == "http" else 8443
    return {"protocol": request.param, "port": port}


@pytest.fixture(scope="module")
def base_url(container_connection_data):
    return "{protocol}://localhost:{port}".format(
        protocol=container_connection_data["protocol"],
        port=container_connection_data["port"],
    )


@pytest.fixture(scope="function")
def random_repo_name():
    return "".join(
        [random.choice(string.ascii_letters + string.digits) for n in range(8)]
    )


@pytest.fixture(scope="function")
def repo_creation_url(base_url, random_repo_name):
    return "%s/new/%s" % (base_url, random_repo_name)
