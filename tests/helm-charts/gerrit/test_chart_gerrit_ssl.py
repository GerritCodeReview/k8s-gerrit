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
import utils


@pytest.fixture(scope="module")
def cert_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("gerrit-cert")


@pytest.fixture(scope="class")
def ssl_certificate(request, cert_dir):
    url = f"primary.{request.config.getoption('--ingress-url')}"
    keypair = mock_ssl.MockSSLKeyPair(f"*.{request.config.getoption('--ingress-url')}", url)
    with open(os.path.join(cert_dir, "server.crt"), "wb") as f:
        f.write(keypair.get_cert())
    with open(os.path.join(cert_dir, "server.key"), "wb") as f:
        f.write(keypair.get_key())
    return keypair


@pytest.fixture(scope="class")
def gerrit_deployment_with_ssl(
    request, docker_tag, test_cluster, ssl_certificate, gerrit_deployment_factory
):
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "images.ImagePullPolicy": "IfNotPresent",
        "ingress.enabled": True,
        "ingress.host": f"primary.{request.config.getoption('--ingress-url')}",
        "ingress.tls.enabled": "true",
        "ingress.tls.cert": ssl_certificate.get_cert().decode(),
        "ingress.tls.key": ssl_certificate.get_key().decode(),
    }
    chart = gerrit_deployment_factory(chart_opts)
    pod_labels = f"app=gerrit,release={chart['name']}"
    finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=300)
    if not finished_in_time:
        raise utils.TimeOutException("Gerrit pod was not ready in time.")

    yield chart

    test_cluster.helm.delete(chart["name"], namespace=chart["namespace"])
    test_cluster.delete_namespace(chart["namespace"])


@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
@pytest.mark.slow
class TestgerritChartSetup:
    # pylint: disable=W0613
    def test_create_project_rest(self, request, cert_dir, gerrit_deployment_with_ssl):
        ingress_url = request.config.getoption("--ingress-url")
        create_project_url = f"https://primary.{ingress_url}/a/projects/test"
        response = requests.put(
            create_project_url,
            auth=("admin", "secret"),
            verify=os.path.join(cert_dir, "server.crt"),
        )
        assert response.status_code == 201

    def test_cloning_project(
        self, request, tmp_path_factory, test_cluster, gerrit_deployment_with_ssl
    ):
        clone_dest = tmp_path_factory.mktemp("gerrit_chart_clone_test")
        repo_url = (
            f"https://primary.{request.config.getoption('--ingress-url')}/test.git"
        )
        repo = git.clone_repository(
            repo_url, clone_dest, callbacks=git_callbacks.TestRemoteCallbacks()
        )
        assert repo.path == os.path.join(clone_dest, ".git/")
