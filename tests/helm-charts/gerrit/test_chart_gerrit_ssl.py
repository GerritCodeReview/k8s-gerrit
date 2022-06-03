# Copyright (C) 2019 The Android Open Source Project
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

import pygit2 as git
import pytest
import requests

import git_callbacks
import mock_ssl


@pytest.fixture(scope="module")
def cert_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("gerrit-cert")


def _create_ssl_certificate(url, cert_dir):
    keypair = mock_ssl.MockSSLKeyPair("*." + url.split(".", 1)[1], url)
    with open(os.path.join(cert_dir, "server.crt"), "wb") as f:
        f.write(keypair.get_cert())
    with open(os.path.join(cert_dir, "server.key"), "wb") as f:
        f.write(keypair.get_key())
    return keypair


@pytest.fixture(scope="class")
def gerrit_deployment_with_ssl(cert_dir, gerrit_deployment):
    ssl_certificate = _create_ssl_certificate(gerrit_deployment.hostname, cert_dir)
    gerrit_deployment.set_helm_value("ingress.tls.enabled", True)
    gerrit_deployment.set_helm_value(
        "ingress.tls.cert", ssl_certificate.get_cert().decode()
    )
    gerrit_deployment.set_helm_value(
        "ingress.tls.key", ssl_certificate.get_key().decode()
    )
    gerrit_deployment.set_gerrit_config_value(
        "httpd", "listenUrl", "proxy-https://*:8080/"
    )
    gerrit_deployment.set_gerrit_config_value(
        "gerrit",
        "canonicalWebUrl",
        f"https://{gerrit_deployment.hostname}",
    )

    gerrit_deployment.install()
    gerrit_deployment.create_admin_account()

    yield gerrit_deployment


@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
@pytest.mark.slow
class TestgerritChartSetup:
    # pylint: disable=W0613
    def test_create_project_rest(
        self, cert_dir, gerrit_deployment_with_ssl, ldap_credentials
    ):
        create_project_url = (
            f"https://{gerrit_deployment_with_ssl.hostname}/a/projects/test"
        )
        response = requests.put(
            create_project_url,
            auth=("gerrit-admin", ldap_credentials["gerrit-admin"]),
            verify=os.path.join(cert_dir, "server.crt"),
        )
        assert response.status_code == 201

    def test_cloning_project(self, tmp_path_factory, gerrit_deployment_with_ssl):
        clone_dest = tmp_path_factory.mktemp("gerrit_chart_clone_test")
        repo_url = f"https://{gerrit_deployment_with_ssl.hostname}/test.git"
        repo = git.clone_repository(
            repo_url, clone_dest, callbacks=git_callbacks.TestRemoteCallbacks()
        )
        assert repo.path == os.path.join(clone_dest, ".git/")
