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
import time

import pytest

from kubernetes import client

import utils

GERRIT_MASTER_STARTUP_TIMEOUT = 240


@pytest.fixture(scope="module")
def gerrit_master_deployment(
    request,
    repository_root,
    test_cluster,
    docker_tag,
    gerrit_master_image,
    gitgc_image,
    gerrit_init_image,
):
    chart_path = os.path.join(repository_root, "helm-charts", "gerrit-master")
    chart_name = "gerrit-master-" + utils.create_random_string()
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "gerritMaster.ingress.host": "master.%s"
        % request.config.getoption("--ingress-url"),
    }
    namespace_name = utils.create_random_string()
    test_cluster.create_namespace(namespace_name)
    test_cluster.helm.install(
        chart_path,
        chart_name,
        set_values=chart_opts,
        fail_on_err=True,
        namespace=namespace_name,
    )

    yield {"name": chart_name, "namespace": namespace_name}

    test_cluster.helm.delete(chart_name)
    test_cluster.delete_namespace(namespace_name)


@pytest.fixture(scope="module")
def gerrit_master_ready_deployment(gerrit_master_deployment):
    def wait_for_readiness():
        pod_labels = "app=gerrit-master,release=%s" % gerrit_master_deployment["name"]
        core_v1 = client.CoreV1Api()
        pod_list = core_v1.list_pod_for_all_namespaces(
            watch=False, label_selector=pod_labels
        )
        for condition in pod_list.items[0].status.conditions:
            if condition.type == "Ready" and condition.status == "True":
                return True
        return False

    timeout = time.time() + GERRIT_MASTER_STARTUP_TIMEOUT

    finished_in_time = False

    while time.time() <= timeout:
        finished_in_time = wait_for_readiness()
        if finished_in_time:
            break

    if not finished_in_time:
        raise utils.TimeOutException(
            "Gerrit master pod was not ready in time (%d s)."
            % GERRIT_MASTER_STARTUP_TIMEOUT
        )

    yield finished_in_time
