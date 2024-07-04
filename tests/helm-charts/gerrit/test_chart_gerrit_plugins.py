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
import os.path
import time

import pytest
import requests

from kubernetes import client
from kubernetes.stream import stream

PLUGINS = ["avatars-gravatar", "uploadvalidator"]
LIBS = ["global-refdb"]
GERRIT_VERSION = "3.10"


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


@pytest.fixture(scope="module")
def lib_list():
    lib_list = []
    for lib in LIBS:
        url = (
            f"https://gerrit-ci.gerritforge.com/view/Plugins-stable-{GERRIT_VERSION}/"
            f"job/module-{lib}-bazel-stable-{GERRIT_VERSION}/lastSuccessfulBuild/"
            f"artifact/bazel-bin/plugins/{lib}/{lib}.jar"
        )
        jar = requests.get(url, verify=False).content
        lib_list.append(
            {"name": lib, "url": url, "sha1": hashlib.sha1(jar).hexdigest()}
        )
    return lib_list


@pytest.fixture(
    scope="class",
    params=[
        [{"name": "replication"}],
        [{"name": "replication"}, {"name": "download-commands"}],
    ],
    ids=["single-packaged-plugin", "multiple-packaged-plugins"],
)
def gerrit_deployment_with_packaged_plugins(request, gerrit_deployment):
    gerrit_deployment.set_helm_value("gerrit.pluginManagement.plugins", request.param)
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

    gerrit_deployment.set_helm_value(
        "gerrit.pluginManagement.plugins", selected_plugins
    )

    gerrit_deployment.install()
    gerrit_deployment.create_admin_account()

    yield gerrit_deployment, selected_plugins


@pytest.fixture(scope="class")
def gerrit_deployment_with_libs(
    request,
    lib_list,
    gerrit_deployment,
):
    gerrit_deployment.set_helm_value("gerrit.pluginManagement.libs", lib_list)

    gerrit_deployment.install()
    gerrit_deployment.create_admin_account()

    yield gerrit_deployment, lib_list


@pytest.fixture(scope="class")
def gerrit_deployment_with_other_plugin_wrong_sha(plugin_list, gerrit_deployment):
    plugin = plugin_list[0]
    plugin["sha1"] = "notAValidSha"
    gerrit_deployment.set_helm_value("gerrit.pluginManagement.plugins", [plugin])

    gerrit_deployment.install(wait=False)

    yield gerrit_deployment


def get_gerrit_plugin_list(gerrit_url, user="admin", password="secret"):
    list_plugins_url = f"{gerrit_url}/a/plugins/?all"
    response = requests.get(list_plugins_url, auth=(user, password))
    if not response.status_code == 200:
        return None
    body = response.text
    return json.loads(body[body.index("\n") + 1 :])


def get_gerrit_lib_list(gerrit_deployment):
    response = (
        stream(
            client.CoreV1Api().connect_get_namespaced_pod_exec,
            gerrit_deployment.chart_name + "-gerrit-stateful-set-0",
            gerrit_deployment.namespace,
            command=["/bin/ash", "-c", "ls /var/gerrit/lib"],
            stdout=True,
        )
        .strip()
        .split()
    )
    return [os.path.splitext(r)[0] for r in response]


@pytest.mark.slow
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
class TestgerritChartPackagedPluginInstall:
    def _assert_installed_plugins(self, expected_plugins, installed_plugins):
        for plugin in expected_plugins:
            plugin_name = plugin["name"]
            assert plugin_name in installed_plugins
            assert installed_plugins[plugin_name]["filename"] == f"{plugin_name}.jar"

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

        gerrit_deployment.set_helm_value(
            "gerrit.pluginManagement.plugins", expected_plugins
        )
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
        gerrit_deployment.set_helm_value(
            "gerrit.pluginManagement.plugins", installed_plugins
        )
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


@pytest.mark.slow
@pytest.mark.incremental
@pytest.mark.integration
@pytest.mark.kubernetes
class TestGerritChartLibModuleInstall:
    def _assert_installed_libs(self, expected_libs, installed_libs):
        for lib in expected_libs:
            assert lib["name"] in installed_libs

    @pytest.mark.timeout(300)
    def test_install_libs(self, gerrit_deployment_with_libs):
        gerrit_deployment, expected_libs = gerrit_deployment_with_libs
        response = get_gerrit_lib_list(gerrit_deployment)
        self._assert_installed_libs(expected_libs, response)

    @pytest.mark.timeout(300)
    def test_install_other_plugins_are_removed_with_update(
        self, gerrit_deployment_with_libs
    ):
        gerrit_deployment, installed_libs = gerrit_deployment_with_libs
        removed_lib = installed_libs.pop()
        gerrit_deployment.set_helm_value("gerrit.pluginManagement.libs", installed_libs)
        gerrit_deployment.update()

        response = None
        while True:
            try:
                response = get_gerrit_lib_list(gerrit_deployment)
                if response is not None and removed_lib["name"] not in response:
                    break
            except requests.exceptions.ConnectionError:
                time.sleep(1)

        assert removed_lib["name"] not in response
        self._assert_installed_libs(installed_libs, response)


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
