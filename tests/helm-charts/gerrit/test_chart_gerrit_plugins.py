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

PLUGINS = ["avatars-gravatar", "readonly"]
GERRIT_VERSION = "3.8"


@pytest.fixture(scope="module")
def plugin_list():
    plugin_list = []
    for plugin in PLUGINS:
        url = (
            f"https://gerrit-ci.gerritforge.com/view/Plugins-stable-{GERRIT_VERSION}/"
            f"job/plugin-{plugin}-bazel-master-stable-{GERRIT_VERSION}/lastSuccessfulBuild/"
            f"artifact/bazel-bin/plugins/{plugin}/{plugin}.jar"
        )
        jar = requests.get(url, verify=False).content
        plugin_list.append(
            {"name": plugin, "url": url, "sha1": hashlib.sha1(jar).hexdigest()}
        )
    return plugin_list


@pytest.fixture(
    scope="class",
    params=[["replication"], ["replication", "download-commands"]],
    ids=["single-packaged-plugin", "multiple-packaged-plugins"],
)
def gerrit_deployment_with_packaged_plugins(request, gerrit_deployment):
    gerrit_deployment.set_helm_value("gerrit.plugins.packaged", request.param)
    gerrit_deployment.install()
    gerrit_deployment.create_admin_account()

    yield gerrit_deployment, request.param


@pytest.fixture(
    scope="class", params=[1, 2], ids=["single-other-plugin", "multiple-other-plugins"]
)
def gerrit_deployment_with_other_plugins(
    request,
    plugin_list,
    gerrit_deployment,
):
    selected_plugins = plugin_list[: request.param]

    gerrit_deployment.set_helm_value("gerrit.plugins.downloaded", selected_plugins)

    gerrit_deployment.install()
    gerrit_deployment.create_admin_account()

    yield gerrit_deployment, selected_plugins


@pytest.fixture(scope="class")
def gerrit_deployment_with_other_plugin_wrong_sha(plugin_list, gerrit_deployment):
    plugin = plugin_list[0]
    plugin["sha1"] = "notAValidSha"
    gerrit_deployment.set_helm_value("gerrit.plugins.downloaded", [plugin])

    gerrit_deployment.install(wait=False)

    yield gerrit_deployment


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
        self, request, gerrit_deployment_with_packaged_plugins, ldap_credentials
    ):
        gerrit_deployment, expected_plugins = gerrit_deployment_with_packaged_plugins
        response = None
        while not response:
            try:
                response = get_gerrit_plugin_list(
                    f"http://{gerrit_deployment.hostname}",
                    "gerrit-admin",
                    ldap_credentials["gerrit-admin"],
                )
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        self._assert_installed_plugins(expected_plugins, response)

    @pytest.mark.timeout(300)
    def test_install_packaged_plugins_are_removed_with_update(
        self,
        request,
        test_cluster,
        gerrit_deployment_with_packaged_plugins,
        ldap_credentials,
    ):
        gerrit_deployment, expected_plugins = gerrit_deployment_with_packaged_plugins
        removed_plugin = expected_plugins.pop()

        gerrit_deployment.set_helm_value("gerrit.plugins.packaged", expected_plugins)
        gerrit_deployment.update()

        response = None
        while True:
            try:
                response = get_gerrit_plugin_list(
                    f"http://{gerrit_deployment.hostname}",
                    "gerrit-admin",
                    ldap_credentials["gerrit-admin"],
                )
                if response is not None and removed_plugin not in response:
                    break
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert removed_plugin not in response
        self._assert_installed_plugins(expected_plugins, response)


@pytest.mark.slow
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
class TestGerritChartOtherPluginInstall:
    def _assert_installed_plugins(self, expected_plugins, installed_plugins):
        for plugin in expected_plugins:
            assert plugin["name"] in installed_plugins
            assert (
                installed_plugins[plugin["name"]]["filename"] == f"{plugin['name']}.jar"
            )

    @pytest.mark.timeout(300)
    def test_install_other_plugins(
        self, gerrit_deployment_with_other_plugins, ldap_credentials
    ):
        gerrit_deployment, expected_plugins = gerrit_deployment_with_other_plugins
        response = None
        while not response:
            try:
                response = get_gerrit_plugin_list(
                    f"http://{gerrit_deployment.hostname}",
                    "gerrit-admin",
                    ldap_credentials["gerrit-admin"],
                )
            except requests.exceptions.ConnectionError:
                continue
        self._assert_installed_plugins(expected_plugins, response)

    @pytest.mark.timeout(300)
    def test_install_other_plugins_are_removed_with_update(
        self, gerrit_deployment_with_other_plugins, ldap_credentials
    ):
        gerrit_deployment, installed_plugins = gerrit_deployment_with_other_plugins
        removed_plugin = installed_plugins.pop()
        gerrit_deployment.set_helm_value("gerrit.plugins.downloaded", installed_plugins)
        gerrit_deployment.update()

        response = None
        while True:
            try:
                response = get_gerrit_plugin_list(
                    f"http://{gerrit_deployment.hostname}",
                    "gerrit-admin",
                    ldap_credentials["gerrit-admin"],
                )
                if response is not None and removed_plugin["name"] not in response:
                    break
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert removed_plugin["name"] not in response
        self._assert_installed_plugins(installed_plugins, response)


@pytest.mark.integration
@pytest.mark.kubernetes
@pytest.mark.timeout(180)
def test_install_other_plugins_fails_wrong_sha(
    gerrit_deployment_with_other_plugin_wrong_sha,
):
    pod_labels = f"app.kubernetes.io/component=gerrit,release={gerrit_deployment_with_other_plugin_wrong_sha.chart_name}"
    core_v1 = client.CoreV1Api()
    pod_name = ""
    while not pod_name:
        pod_list = core_v1.list_namespaced_pod(
            namespace=gerrit_deployment_with_other_plugin_wrong_sha.namespace,
            watch=False,
            label_selector=pod_labels,
        )
        if len(pod_list.items) > 1:
            raise RuntimeError("Too many gerrit pods with the same release name.")
        elif len(pod_list.items) == 1:
            pod_name = pod_list.items[0].metadata.name

    current_status = None
    while not current_status:
        pod = core_v1.read_namespaced_pod_status(
            pod_name, gerrit_deployment_with_other_plugin_wrong_sha.namespace
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
