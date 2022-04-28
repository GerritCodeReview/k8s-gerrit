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

import hashlib
import json
import time

import pytest
import requests

from kubernetes import client

import utils

PLUGINS = ["reviewers", "account"]
GERRIT_VERSION = "3.5"


@pytest.fixture(scope="module")
def plugin_list():
    plugin_list = []
    for plugin in PLUGINS:
        url = (
            f"https://gerrit-ci.gerritforge.com/view/Plugins-stable-{GERRIT_VERSION}/"
            f"job/plugin-{plugin}-bazel-stable-{GERRIT_VERSION}/lastSuccessfulBuild/"
            f"artifact/bazel-bin/plugins/{plugin}/{plugin}.jar"
        )
        jar = requests.get(url, verify=False).content
        plugin_list.append(
            {"name": plugin, "url": url, "sha1": hashlib.sha1(jar).hexdigest()}
        )
    return plugin_list


@pytest.fixture(scope="class")
def gerrit_deployment_with_plugins_factory(request, gerrit_deployment_factory):
    def install_chart(chart_opts):
        chart = gerrit_deployment_factory(chart_opts)
        pod_labels = f"app=gerrit,release={chart['name']}"
        finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=300)
        if not finished_in_time:
            raise utils.TimeOutException("Gerrit pod was not ready in time.")

        return chart

    return install_chart


@pytest.fixture(
    scope="class",
    params=[["replication"], ["replication", "download-commands"]],
    ids=["single-packaged-plugin", "multiple-packaged-plugins"],
)
def gerrit_deployment_with_packaged_plugins(
    request, docker_tag, test_cluster, gerrit_deployment_with_plugins_factory
):
    plugins_opts_string = ",".join(request.param)
    plugins_opts_string = f"{{{plugins_opts_string}}}"
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "images.ImagePullPolicy": "IfNotPresent",
        "ingress.enabled": True,
        "ingress.host": f"primary.{request.config.getoption('--ingress-url')}",
        "gerrit.plugins.packaged": plugins_opts_string,
    }
    chart = gerrit_deployment_with_plugins_factory(chart_opts)
    chart["installed_plugins"] = request.param

    yield chart

    test_cluster.helm.delete(chart["name"], namespace=chart["namespace"])
    test_cluster.delete_namespace(chart["namespace"])


@pytest.fixture(
    scope="class", params=[1, 2], ids=["single-other-plugin", "multiple-other-plugins"]
)
def gerrit_deployment_with_other_plugins(
    request,
    docker_tag,
    test_cluster,
    plugin_list,
    gerrit_deployment_with_plugins_factory,
):
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "images.ImagePullPolicy": "IfNotPresent",
        "ingress.enabled": True,
        "ingress.host": f"primary.{request.config.getoption('--ingress-url')}",
    }
    selected_plugins = plugin_list[: request.param]
    for counter, plugin in enumerate(selected_plugins):
        chart_opts[f"gerrit.plugins.downloaded[{counter}].name"] = plugin["name"]
        chart_opts[f"gerrit.plugins.downloaded[{counter}].url"] = plugin["url"]
        chart_opts[f"gerrit.plugins.downloaded[{counter}].sha1"] = plugin["sha1"]
    chart = gerrit_deployment_with_plugins_factory(chart_opts)
    chart["installed_plugins"] = selected_plugins

    yield chart

    test_cluster.helm.delete(chart["name"], namespace=chart["namespace"])
    test_cluster.delete_namespace(chart["namespace"])


@pytest.fixture()
def gerrit_deployment_with_other_plugin_wrong_sha(
    request, docker_tag, test_cluster, plugin_list, gerrit_deployment_factory
):
    chart_opts = {
        "images.registry.name": request.config.getoption("--registry"),
        "images.version": docker_tag,
        "images.ImagePullPolicy": "IfNotPresent",
        "ingress.enabled": True,
        "ingress.host": f"primary.{request.config.getoption('--ingress-url')}",
    }
    plugin = plugin_list[0]
    chart_opts["gerrit.plugins.downloaded[0].name"] = plugin["name"]
    chart_opts["gerrit.plugins.downloaded[0].url"] = plugin["url"]
    chart_opts["gerrit.plugins.downloaded[0].sha1"] = "notAValidSha"
    chart = gerrit_deployment_factory(chart_opts, wait=False)
    chart["installed_plugins"] = plugin

    yield chart

    test_cluster.helm.delete(chart["name"], namespace=chart["namespace"])
    test_cluster.delete_namespace(chart["namespace"])


def update_chart(helm, chart, opts):
    helm.upgrade(
        chart=chart["chart"],
        name=chart["name"],
        namespace=chart["namespace"],
        set_values=opts,
        reuse_values=True,
        fail_on_err=True,
    )


def get_gerrit_plugin_list(gerrit_url, user="admin", password="secret"):
    list_plugins_url = f"{gerrit_url}/a/plugins/?all"
    response = requests.get(list_plugins_url, auth=(user, password))
    if not response.status_code == 200:
        return None
    body = response.text
    return json.loads(body[body.index("\n") + 1 :])


@pytest.mark.slow
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
class TestgerritChartPackagedPluginInstall:
    def _assert_installed_plugins(self, expected_plugins, installed_plugins):
        for plugin in expected_plugins:
            assert plugin in installed_plugins
            assert installed_plugins[plugin]["filename"] == f"{plugin}.jar"

    @pytest.mark.timeout(300)
    def test_install_packaged_plugins(
        self, request, test_cluster, gerrit_deployment_with_packaged_plugins
    ):
        response = None
        while not response:
            try:
                response = get_gerrit_plugin_list(
                    f"http://primary.{request.config.getoption('--ingress-url')}"
                )
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        self._assert_installed_plugins(
            gerrit_deployment_with_packaged_plugins["installed_plugins"], response
        )

    @pytest.mark.timeout(300)
    def test_install_packaged_plugins_are_removed_with_update(
        self, request, test_cluster, gerrit_deployment_with_packaged_plugins
    ):
        chart = gerrit_deployment_with_packaged_plugins
        chart["removed_plugin"] = chart["installed_plugins"].pop()
        plugins_opts_string = ",".join(chart["installed_plugins"])
        if plugins_opts_string:
            plugins_opts_string = f"{{{plugins_opts_string}}}"
        else:
            plugins_opts_string = "false"

        update_chart(
            test_cluster.helm, chart, {"gerrit.plugins.packaged": plugins_opts_string}
        )

        response = None
        while True:
            try:
                response = get_gerrit_plugin_list(
                    f"http://primary.{request.config.getoption('--ingress-url')}"
                )
                if response is not None and chart["removed_plugin"] not in response:
                    break
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert chart["removed_plugin"] not in response
        self._assert_installed_plugins(chart["installed_plugins"], response)


@pytest.mark.slow
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
class TestgerritChartOtherPluginInstall:
    def _remove_plugin_from_install_list(self, installed_plugins):
        removed_plugin = installed_plugins.pop()
        plugin_install_list = {}
        if installed_plugins:
            for counter, plugin in enumerate(installed_plugins):
                plugin_install_list[
                    f"gerrit.plugins.downloaded[{counter}].name"
                ] = plugin["name"]
                plugin_install_list[
                    f"gerrit.plugins.downloaded[{counter}].url"
                ] = plugin["url"]
                plugin_install_list[
                    f"gerrit.plugins.downloaded[{counter}].sha1"
                ] = plugin["sha1"]
        else:
            plugin_install_list["gerrit.plugins.downloaded"] = "false"
        return plugin_install_list, removed_plugin, installed_plugins

    def _assert_installed_plugins(self, expected_plugins, installed_plugins):
        for plugin in expected_plugins:
            assert plugin["name"] in installed_plugins
            assert (
                installed_plugins[plugin["name"]]["filename"] == f"{plugin['name']}.jar"
            )

    @pytest.mark.timeout(300)
    def test_install_other_plugins(
        self, request, test_cluster, gerrit_deployment_with_other_plugins
    ):
        response = None
        while not response:
            try:
                response = get_gerrit_plugin_list(
                    f"http://primary.{request.config.getoption('--ingress-url')}"
                )
            except requests.exceptions.ConnectionError:
                continue
        self._assert_installed_plugins(
            gerrit_deployment_with_other_plugins["installed_plugins"], response
        )

    @pytest.mark.timeout(300)
    def test_install_other_plugins_are_removed_with_update(
        self, request, test_cluster, gerrit_deployment_with_other_plugins
    ):
        chart = gerrit_deployment_with_other_plugins
        (
            chart_opts,
            chart["removed_plugin"],
            chart["installed_plugin"],
        ) = self._remove_plugin_from_install_list(chart["installed_plugins"])
        update_chart(test_cluster.helm, chart, chart_opts)

        response = None
        while True:
            try:
                response = get_gerrit_plugin_list(
                    f"http://primary.{request.config.getoption('--ingress-url')}"
                )
                if (
                    response is not None
                    and chart["removed_plugin"]["name"] not in response
                ):
                    break
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert chart["removed_plugin"]["name"] not in response
        self._assert_installed_plugins(chart["installed_plugins"], response)


@pytest.mark.integration
@pytest.mark.kubernetes
@pytest.mark.timeout(180)
def test_install_other_plugins_fails_wrong_sha(
    request, test_cluster, gerrit_deployment_with_other_plugin_wrong_sha
):
    pod_labels = (
        f"app=gerrit,release={gerrit_deployment_with_other_plugin_wrong_sha['name']}"
    )
    core_v1 = client.CoreV1Api()
    pod_name = ""
    while not pod_name:
        pod_list = core_v1.list_namespaced_pod(
            namespace=gerrit_deployment_with_other_plugin_wrong_sha["namespace"],
            watch=False,
            label_selector=pod_labels,
        )
        if len(pod_list.items) > 1:
            raise RuntimeError("Too many gerrit pods with the same release name.")
        pod_name = pod_list.items[0].metadata.name

    current_status = None
    while not current_status:
        pod = core_v1.read_namespaced_pod_status(
            pod_name, gerrit_deployment_with_other_plugin_wrong_sha["namespace"]
        )
        if not pod.status.init_container_statuses:
            time.sleep(1)
            continue
        for init_container_status in pod.status.init_container_statuses:
            if (
                init_container_status.name == "gerrit-init"
                and init_container_status.last_state.terminated
            ):
                current_status = init_container_status
                assert current_status.last_state.terminated.exit_code > 0
                return

    assert current_status.last_state.terminated.exit_code > 0
