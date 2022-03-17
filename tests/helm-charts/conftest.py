# pylint: disable=W0613

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

from abc import ABC, abstractmethod

import base64
import json
import re
import subprocess
import warnings

from kubernetes import client, config

import pytest

from helm import Helm

HELM_SERVICE_ACCOUNT_NAME = "helm"
HELM_SERVICE_ACCOUNT_NAMESPACE = "kube-system"


class AbstractStorageProvisioner(ABC):
    def __init__(self, name):
        self.name = name

    @abstractmethod
    def deploy(self):
        """
        Deploy provisioner on cluster
        """

    @abstractmethod
    def delete(self):
        """
        Delete provisioner from cluster
        """


class EFSProvisioner(AbstractStorageProvisioner):
    def __init__(self, efs_id, efs_region, chart_name="efs"):
        super().__init__(chart_name)

        self.efs_id = efs_id
        self.efs_region = efs_region

        self.helm = None

    def set_helm_connector(self, helm):
        self.helm = helm

    def deploy(self):
        chart_opts = {
            "efsProvisioner.efsFileSystemId": self.efs_id,
            "efsProvisioner.awsRegion": self.efs_region,
            "efsProvisioner.storageClass.name": "shared-storage",
        }

        res = self.helm.install(
            "stable/efs-provisioner",
            self.name,
            set_values=chart_opts,
            fail_on_err=False,
        )

        if res.returncode == 0:
            return

        if re.match(r"Error: cannot re-use a name that is still in use", res.stderr):
            warnings.warn(
                "Kubernetes Cluster not empty. EFS provisioner already exists."
            )
        else:
            print(res.stderr)
            raise subprocess.CalledProcessError(
                res.returncode, res.args, output=res.stdout, stderr=res.stderr
            )

    def delete(self):
        try:
            self.helm.delete(self.name)
        except subprocess.CalledProcessError as exc:
            print("deletion of EFS-provisioner failed: ", exc)


class TestCluster:
    def __init__(self, kube_config, registry):
        self.kube_config = kube_config
        self.registry = registry

        self.current_context = None
        self.helm = None
        self.namespaces = []

    def _load_kube_config(self):
        config.load_kube_config(config_file=self.kube_config)
        _, context = config.list_kube_config_contexts(config_file=self.kube_config)
        self.current_context = context["name"]

    def create_image_pull_secret(self, namespace="default"):
        secret_metadata = client.V1ObjectMeta(name="image-pull-secret")
        auth_string = str.encode(f"{self.registry['user']}:{self.registry['pwd']}")
        secret_data = {
            "auths": {
                self.registry["url"]: {
                    "auth": base64.b64encode(auth_string).decode("utf-8")
                }
            }
        }
        secret_data = json.dumps(secret_data).encode()
        secret_body = client.V1Secret(
            api_version="v1",
            kind="Secret",
            metadata=secret_metadata,
            type="kubernetes.io/dockerconfigjson",
            data={".dockerconfigjson": base64.b64encode(secret_data).decode("utf-8")},
        )
        core_v1 = client.CoreV1Api()
        try:
            core_v1.create_namespaced_secret(namespace, secret_body)
        except client.rest.ApiException as exc:
            if exc.status == 409 and exc.reason == "Conflict":
                warnings.warn(
                    "Kubernetes Cluster not empty. Image pull secret already exists."
                )
            else:
                raise exc

    def create_namespace(self, name):
        namespace_metadata = client.V1ObjectMeta(name=name)
        namespace_body = client.V1Namespace(
            kind="Namespace", api_version="v1", metadata=namespace_metadata
        )
        core_v1 = client.CoreV1Api()
        core_v1.create_namespace(body=namespace_body)
        self.namespaces.append(name)
        self.create_image_pull_secret(name)

    def delete_namespace(self, name):
        core_v1 = client.CoreV1Api()
        core_v1.delete_namespace(name, body=client.V1DeleteOptions())
        self.namespaces.remove(name)

    def setup(self):
        self._load_kube_config()
        self.create_image_pull_secret()
        self.helm = Helm(self.kube_config, self.current_context)

    def cleanup(self):
        while self.namespaces:
            self.helm.delete_all(
                namespace=self.namespaces[0],
            )
            self.delete_namespace(self.namespaces[0])
        core_v1 = client.CoreV1Api()
        core_v1.delete_namespaced_secret(
            "image-pull-secret", "default", body=client.V1DeleteOptions()
        )


@pytest.fixture(scope="session")
def test_cluster(request):
    kube_config = request.config.getoption("--kubeconfig")

    registry = {
        "url": request.config.getoption("--registry"),
        "user": request.config.getoption("--registry-user"),
        "pwd": request.config.getoption("--registry-pwd"),
    }
    test_cluster = TestCluster(kube_config, registry)
    test_cluster.setup()

    yield test_cluster

    test_cluster.cleanup()
