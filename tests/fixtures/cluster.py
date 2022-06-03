# pylint: disable=W0613

# Copyright (C) 2022 The Android Open Source Project
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

import base64
import json
import warnings

from kubernetes import client, config

import pytest

from .helm.client import HelmClient


class Cluster:
    def __init__(self, kube_config):
        self.kube_config = kube_config

        self.image_pull_secrets = []
        self.namespaces = []

        context = self._load_kube_config()
        self.helm = HelmClient(self.kube_config, context)

    def _load_kube_config(self):
        config.load_kube_config(config_file=self.kube_config)
        _, context = config.list_kube_config_contexts(config_file=self.kube_config)
        return context["name"]

    def _apply_image_pull_secrets(self, namespace):
        for ips in self.image_pull_secrets:
            try:
                client.CoreV1Api().create_namespaced_secret(namespace, ips)
            except client.rest.ApiException as exc:
                if exc.status == 409 and exc.reason == "Conflict":
                    warnings.warn(
                        "Kubernetes Cluster not empty. Image pull secret already exists."
                    )
                else:
                    raise exc

    def add_container_registry(self, secret_name, url, user, pwd):
        data = {
            "auths": {
                url: {
                    "auth": base64.b64encode(str.encode(f"{user}:{pwd}")).decode(
                        "utf-8"
                    )
                }
            }
        }
        metadata = client.V1ObjectMeta(name=secret_name)
        self.image_pull_secrets.append(
            client.V1Secret(
                api_version="v1",
                kind="Secret",
                metadata=metadata,
                type="kubernetes.io/dockerconfigjson",
                data={
                    ".dockerconfigjson": base64.b64encode(
                        json.dumps(data).encode()
                    ).decode("utf-8")
                },
            )
        )

    def create_namespace(self, name):
        namespace_metadata = client.V1ObjectMeta(name=name)
        namespace_body = client.V1Namespace(
            kind="Namespace", api_version="v1", metadata=namespace_metadata
        )
        client.CoreV1Api().create_namespace(body=namespace_body)
        self.namespaces.append(name)
        self._apply_image_pull_secrets(name)

    def delete_namespace(self, name):
        if name not in self.namespaces:
            return

        client.CoreV1Api().delete_namespace(name, body=client.V1DeleteOptions())
        self.namespaces.remove(name)

    def cleanup(self):
        while self.namespaces:
            self.helm.delete_all(
                namespace=self.namespaces[0],
            )
            self.delete_namespace(self.namespaces[0])


@pytest.fixture(scope="session")
def test_cluster(request):
    kube_config = request.config.getoption("--kubeconfig")

    test_cluster = Cluster(kube_config)
    test_cluster.add_container_registry(
        "image-pull-secret",
        request.config.getoption("--registry"),
        request.config.getoption("--registry-user"),
        request.config.getoption("--registry-pwd"),
    )

    yield test_cluster

    test_cluster.cleanup()


@pytest.fixture(scope="session")
def ldap_credentials(test_cluster):
    ldap_secret = client.CoreV1Api().read_namespaced_secret(
        "openldap-users", namespace="openldap"
    )
    users = base64.b64decode(ldap_secret.data["users"]).decode("utf-8").split(",")
    passwords = (
        base64.b64decode(ldap_secret.data["passwords"]).decode("utf-8").split(",")
    )
    credentials = {}
    for i, user in enumerate(users):
        credentials[user] = passwords[i]

    yield credentials


@pytest.fixture(scope="session")
def ldap_admin_credentials(test_cluster):
    ldap_secret = client.CoreV1Api().read_namespaced_secret(
        "openldap-admin", namespace="openldap"
    )
    password = base64.b64decode(ldap_secret.data["adminpassword"]).decode("utf-8")

    yield ("admin", password)
