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

import os.path

import pytest

from kubernetes import client

import utils

GERRIT_MASTER_STARTUP_TIMEOUT = 240


@pytest.fixture(scope="session")
def gerrit_master_deployment_factory(
    request,
    repository_root,
    test_cluster,
    docker_tag,
    gerrit_master_image,
    gitgc_image,
    gerrit_init_image,
):
    def install_chart(chart_opts):
        chart_path = os.path.join(repository_root, "helm-charts", "gerrit-master")
        chart_name = "gerrit-master-" + utils.create_random_string()
        namespace_name = utils.create_random_string()
        test_cluster.create_namespace(namespace_name)

        core_v1 = client.CoreV1Api()
        core_v1.create_namespaced_persistent_volume_claim(
            namespace_name,
            body=client.V1PersistentVolumeClaim(
                kind="PersistentVolumeClaim",
                api_version="v1",
                metadata=client.V1ObjectMeta(name="repo-storage"),
                spec=client.V1PersistentVolumeClaimSpec(
                    access_modes=["ReadWriteMany"],
                    storage_class_name="shared-storage",
                    resources=client.V1ResourceRequirements(
                        requests={"storage": "1Gi"}
                    ),
                ),
            ),
        )

        chart_opts["gitRepositoryStorage.externalPVC.use"] = "false"
        chart_opts["gitRepositoryStorage.externalPVC.name"] = "repo-storage"

        test_cluster.helm.install(
            chart_path,
            chart_name,
            set_values=chart_opts,
            fail_on_err=True,
            namespace=namespace_name,
        )

        return {"chart": chart_path, "name": chart_name, "namespace": namespace_name}

    return install_chart


@pytest.fixture(scope="module")
def gerrit_master_deployment(
    request, docker_tag, test_cluster, gerrit_master_deployment_factory
):
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "gerritMaster.ingress.host": "master.%s"
        % request.config.getoption("--ingress-url"),
    }
    chart = gerrit_master_deployment_factory(chart_opts)

    yield chart

    test_cluster.helm.delete(chart["name"])
    test_cluster.delete_namespace(chart["namespace"])


@pytest.fixture(scope="module")
def gerrit_master_ready_deployment(gerrit_master_deployment):

    pod_labels = "app=gerrit-master,release=%s" % gerrit_master_deployment["name"]
    finished_in_time = utils.wait_for_pod_readiness(
        pod_labels, timeout=GERRIT_MASTER_STARTUP_TIMEOUT
    )

    if not finished_in_time:
        raise utils.TimeOutException(
            "Gerrit master pod was not ready in time (%d s)."
            % GERRIT_MASTER_STARTUP_TIMEOUT
        )

    yield finished_in_time
