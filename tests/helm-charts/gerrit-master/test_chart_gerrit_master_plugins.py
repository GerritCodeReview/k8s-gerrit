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

import json
import time

import pytest
import requests

import utils


@pytest.fixture(scope="class")
def gerrit_master_deployment_with_plugins_factory(
    request, gerrit_master_deployment_factory
):
    def install_chart(chart_opts):
        chart = gerrit_master_deployment_factory(chart_opts)
        pod_labels = "app=gerrit-master,release=%s" % chart["name"]
        finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=300)
        if not finished_in_time:
            raise utils.TimeOutException("Gerrit master pod was not ready in time.")

        return chart

    return install_chart


@pytest.fixture(
    scope="class",
    params=[["replication"], ["replication", "download-commands"]],
    ids=["single-packaged-plugin", "multiple-packaged-plugins"],
)
def gerrit_master_deployment_with_packaged_plugins(
    request, docker_tag, test_cluster, gerrit_master_deployment_with_plugins_factory
):

    plugins_opts_string = "{%s}" % (",".join(request.param))
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "images.ImagePullPolicy": "IfNotPresent",
        "gerritMaster.ingress.host": "master.%s"
        % request.config.getoption("--ingress-url"),
        "gerritMaster.plugins.packaged": plugins_opts_string,
    }
    chart = gerrit_master_deployment_with_plugins_factory(chart_opts)
    chart["installed_plugins"] = request.param

    yield chart

    test_cluster.helm.delete(chart["name"])
    test_cluster.delete_namespace(chart["namespace"])


def update_chart(helm, chart, opts):
    helm.upgrade(
        chart=chart["chart"],
        name=chart["name"],
        set_values=opts,
        reuse_values=True,
        recreate_pods=True,
        fail_on_err=True,
    )


def get_gerrit_plugin_list(gerrit_url, user="admin", password="secret"):
    list_plugins_url = "%s/a/plugins/?all" % gerrit_url
    response = requests.get(list_plugins_url, auth=(user, password))
    if not response.status_code == 200:
        return None
    body = response.text
    return json.loads(body[body.index("\n") + 1 :])


@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartPackagedPluginInstall:
    def _assert_installed_plugins(self, expected_plugins, installed_plugins):
        for plugin in expected_plugins:
            assert plugin in installed_plugins
            assert installed_plugins[plugin]["filename"] == "%s.jar" % plugin

    @pytest.mark.timeout(300)
    def test_install_packaged_plugins(
        self, request, test_cluster, gerrit_master_deployment_with_packaged_plugins
    ):
        response = None
        while not response:
            try:
                response = get_gerrit_plugin_list(
                    "http://master.%s" % (request.config.getoption("--ingress-url"))
                )
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        self._assert_installed_plugins(
            gerrit_master_deployment_with_packaged_plugins["installed_plugins"],
            response,
        )

    @pytest.mark.timeout(300)
    def test_install_packaged_plugins_are_removed_with_update(
        self, request, test_cluster, gerrit_master_deployment_with_packaged_plugins
    ):
        chart = gerrit_master_deployment_with_packaged_plugins
        chart["removed_plugin"] = chart["installed_plugins"].pop()
        plugins_opts_string = "{%s}" % (",".join(chart["installed_plugins"]))
        plugins_opts_string = plugins_opts_string if plugins_opts_string else "false"
        update_chart(
            test_cluster.helm,
            chart,
            {"gerritMaster.plugins.packaged": plugins_opts_string},
        )

        response = None
        while True:
            try:
                response = get_gerrit_plugin_list(
                    "http://master.%s" % (request.config.getoption("--ingress-url"))
                )
                if response is not None and chart["removed_plugin"] not in response:
                    break
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert chart["removed_plugin"] not in response
        self._assert_installed_plugins(chart["installed_plugins"], response)
