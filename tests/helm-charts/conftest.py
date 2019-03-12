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
from argparse import ArgumentTypeError

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

        if re.match(r"Error: a release named efs already exists.", res.stderr):
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
    def __init__(self, kube_config, storage_provisioner, registry):
        self.kube_config = kube_config
        self.registry = registry
        self.storage_provisioner = storage_provisioner

        self.current_context = None
        self.helm = None
        self.namespaces = list()

    def _load_kube_config(self):
        config.load_kube_config(config_file=self.kube_config)
        _, context = config.list_kube_config_contexts(config_file=self.kube_config)
        self.current_context = context["name"]

    def _create_and_deploy_helm_crb(self):
        crb_meta = client.V1ObjectMeta(name="helm")
        crb_name = "cluster-admin"
        crb_role_ref = client.V1RoleRef(
            api_group="rbac.authorization.k8s.io", kind="ClusterRole", name=crb_name
        )
        crb_subjects = [
            client.V1Subject(
                kind="ServiceAccount",
                name=HELM_SERVICE_ACCOUNT_NAME,
                namespace=HELM_SERVICE_ACCOUNT_NAMESPACE,
            )
        ]
        crb = client.V1ClusterRoleBinding(
            metadata=crb_meta, role_ref=crb_role_ref, subjects=crb_subjects
        )

        rbac_v1 = client.RbacAuthorizationV1Api()
        try:
            rbac_v1.create_cluster_role_binding(crb)
        except client.rest.ApiException as exc:
            if exc.status == 409 and exc.reason == "Conflict":
                warnings.warn(
                    "Kubernetes Cluster not empty. Helm cluster role binding already exists."
                )
            else:
                raise exc

    def _create_and_deploy_helm_service_account(self):
        helm_service_account_metadata = client.V1ObjectMeta(
            name=HELM_SERVICE_ACCOUNT_NAME, namespace=HELM_SERVICE_ACCOUNT_NAMESPACE
        )
        helm_service_account = client.V1ServiceAccount(
            metadata=helm_service_account_metadata
        )

        core_v1 = client.CoreV1Api()
        try:
            core_v1.create_namespaced_service_account(
                HELM_SERVICE_ACCOUNT_NAMESPACE, helm_service_account
            )
        except client.rest.ApiException as exc:
            if exc.status == 409 and exc.reason == "Conflict":
                warnings.warn(
                    "Kubernetes Cluster not empty. Helm service account already exists."
                )
            else:
                raise exc

    def create_image_pull_secret(self, namespace="default"):
        secret_metadata = client.V1ObjectMeta(name="image-pull-secret")
        auth_string = str.encode(
            "%s:%s" % (self.registry["user"], self.registry["pwd"])
        )
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

    def init_helm(self):
        self._create_and_deploy_helm_crb()
        self._create_and_deploy_helm_service_account()
        self.helm = Helm(self.kube_config, self.current_context)
        self.helm.init(HELM_SERVICE_ACCOUNT_NAME)

    def remove_helm(self):
        self.helm.reset()
        apps_v1 = client.AppsV1Api()
        replica_sets = apps_v1.list_namespaced_replica_set("kube-system")
        for replica_set in replica_sets.items:
            if re.match(r"tiller-deploy-.*", replica_set.metadata.name):
                apps_v1.delete_namespaced_replica_set(
                    replica_set.metadata.name,
                    "kube-system",
                    body=client.V1DeleteOptions(),
                )
                break
        core_v1 = client.CoreV1Api()
        core_v1.delete_namespaced_service_account(
            HELM_SERVICE_ACCOUNT_NAME,
            HELM_SERVICE_ACCOUNT_NAMESPACE,
            body=client.V1DeleteOptions(),
        )
        rbac_v1 = client.RbacAuthorizationV1Api()
        rbac_v1.delete_cluster_role_binding(
            HELM_SERVICE_ACCOUNT_NAME, body=client.V1DeleteOptions()
        )

    def install_storage_provisioner(self):
        self.storage_provisioner.set_helm_connector(self.helm)
        self.storage_provisioner.deploy()

    def setup(self):
        self._load_kube_config()
        self.create_image_pull_secret()
        self.init_helm()
        self.install_storage_provisioner()

    def cleanup(self):
        self.helm.delete_all(exceptions=[self.storage_provisioner.name])
        self.storage_provisioner.delete()
        self.remove_helm()
        core_v1 = client.CoreV1Api()
        core_v1.delete_namespaced_secret(
            "image-pull-secret", "default", body=client.V1DeleteOptions()
        )
        while self.namespaces:
            self.delete_namespace(self.namespaces[0])


@pytest.fixture(scope="session")
def test_cluster(request):
    kube_config = request.config.getoption("--kubeconfig")
    infra_provider = request.config.getoption("--infra-provider").lower()

    if infra_provider == "aws":
        efs_id = request.config.getoption("--efs-id")
        if not efs_id:
            raise ArgumentTypeError("No EFS-ID was provided.")
        efs_region = request.config.getoption("--efs-region")
        if not efs_region:
            raise ArgumentTypeError("No EFS-region was provided.")
        storage_provisioner = EFSProvisioner(efs_id, efs_region)

    registry = {
        "url": request.config.getoption("--registry"),
        "user": request.config.getoption("--registry-user"),
        "pwd": request.config.getoption("--registry-pwd"),
    }
    test_cluster = TestCluster(kube_config, storage_provisioner, registry)
    test_cluster.setup()

    yield test_cluster

    test_cluster.cleanup()
