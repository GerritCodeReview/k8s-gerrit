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
import urllib.request

import pytest
import requests

from kubernetes import client

import utils

PLUGINS = ["autosubmitter", "reviewers"]
GERRIT_VERSION = "2.16"

@pytest.fixture(scope="module")
def plugin_list():
  plugin_list = list()
  for plugin in PLUGINS:
    url = "https://gerrit-ci.gerritforge.com/view/Plugins-stable-{gerrit_version}/job/plugin-{plugin}-bazel-stable-{gerrit_version}/lastSuccessfulBuild/artifact/bazel-genfiles/plugins/{plugin}/{plugin}.jar".format(
      plugin=plugin, gerrit_version=GERRIT_VERSION)
    jar = urllib.request.urlopen(url).read()
    plugin_list.append({
      "name": plugin,
      "url": url,
      "sha256": hashlib.sha256(jar).hexdigest()
    })
  return plugin_list

@pytest.fixture(scope="class")
def gerrit_master_deployment_with_plugins_factory(
    request, gerrit_master_h2_deployment_factory):

  def install_chart(chart_opts):
    chart = gerrit_master_h2_deployment_factory(chart_opts)
    pod_labels = "app=gerrit-master,release=%s" % chart["name"]
    finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=300)
    if not finished_in_time:
      raise utils.TimeOutException("Gerrit master pod was not ready in time.")
    time.sleep(5)
    return chart

  return install_chart

@pytest.fixture(
  scope="class",
  params=[
    ["replication"],
    ["replication", "download-commands"]
  ],
  ids=[
    "single-core-plugin",
    "multiple-core-plugins"
  ])
def gerrit_master_deployment_with_core_plugins(
    request, docker_tag, test_cluster, gerrit_master_deployment_with_plugins_factory):
  plugins_opts_string = "{%s}" % (",".join(request.param))
  chart_opts = {
    "images.registry.name": request.config.getoption("--registry"),
    "images.version": docker_tag,
    "images.ImagePullPolicy": "IfNotPresent",
    "gerritMaster.ingress.host": "master.%s" % request.config.getoption("--ingress-url"),
    "gerritMaster.plugins.core": plugins_opts_string
  }
  chart = gerrit_master_deployment_with_plugins_factory(chart_opts)
  chart["installed_plugins"] = request.param

  yield chart

  test_cluster.helm.delete(chart["name"])

@pytest.fixture(
  scope="class",
  params=[1, 2],
  ids=[
    "single-other-plugin",
    "multiple-other-plugins"
  ])
def gerrit_master_deployment_with_other_plugins(
    request, docker_tag, test_cluster, plugin_list,
    gerrit_master_deployment_with_plugins_factory):
  chart_opts = {
    "images.registry.name": request.config.getoption("--registry"),
    "images.version": docker_tag,
    "images.ImagePullPolicy": "IfNotPresent",
    "gerritMaster.ingress.host": "master.%s" % request.config.getoption("--ingress-url")
  }
  selected_plugins = plugin_list[:request.param]
  for counter, plugin in enumerate(selected_plugins):
    chart_opts["gerritMaster.plugins.other[%d].name" % counter] = plugin["name"]
    chart_opts["gerritMaster.plugins.other[%d].url" % counter] = plugin["url"]
    chart_opts["gerritMaster.plugins.other[%d].sha256" % counter] = plugin["sha256"]
  chart = gerrit_master_deployment_with_plugins_factory(chart_opts)
  chart["installed_plugins"] = selected_plugins

  yield chart

  test_cluster.helm.delete(chart["name"])

@pytest.fixture()
def gerrit_master_deployment_with_other_plugin_wrong_sha(
    request, docker_tag, test_cluster, plugin_list,
    gerrit_master_h2_deployment_factory):
  chart_opts = {
    "images.registry.name": request.config.getoption("--registry"),
    "images.version": docker_tag,
    "images.ImagePullPolicy": "IfNotPresent",
    "gerritMaster.ingress.host": "master.%s" % request.config.getoption("--ingress-url")
  }
  plugin = plugin_list[0]
  chart_opts["gerritMaster.plugins.other[0].name"] = plugin["name"]
  chart_opts["gerritMaster.plugins.other[0].url"] = plugin["url"]
  chart_opts["gerritMaster.plugins.other[0].sha256"] = "notAValidSha"
  chart = gerrit_master_h2_deployment_factory(chart_opts)
  chart["installed_plugins"] = plugin

  yield chart

  test_cluster.helm.delete(chart["name"])

def update_chart(helm, chart, opts):
  helm.upgrade(
    chart=chart["chart"],
    name=chart["name"],
    set_values=opts,
    reuse_values=True,
    recreate_pods=True,
    fail_on_err=True
  )
  pod_labels = "app=gerrit-master,release=%s" % chart["name"]
  finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=300)
  if not finished_in_time:
    raise utils.TimeOutException("Gerrit master pod was not ready in time.")

  # Increase the probability that all REST endpoints are started.
  # TODO: Use healthcheck plugin as better readiness indicator
  time.sleep(5)

def get_gerrit_plugin_list(gerrit_url, user="admin", password="secret"):
  list_plugins_url = "%s/a/plugins/?all" % gerrit_url
  response = requests.get(list_plugins_url, auth=(user, password))
  assert response.status_code == 200
  body = response.text
  return json.loads(body[body.index('\n')+1:])

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartCorePluginInstall(object):
  def _assert_installed_plugins(self, expected_plugins, installed_plugins):
    for plugin in expected_plugins:
      assert plugin in installed_plugins
      assert installed_plugins[plugin]["filename"] == "%s.jar" % plugin

  def test_install_core_plugins(self, request, test_cluster,
                                gerrit_master_deployment_with_core_plugins):
    response = get_gerrit_plugin_list("http://master.%s" % (
      request.config.getoption("--ingress-url")))
    self._assert_installed_plugins(
      gerrit_master_deployment_with_core_plugins["installed_plugins"], response)

  def test_install_core_plugins_are_removed_with_update(
      self, request, test_cluster, gerrit_master_deployment_with_core_plugins):
    chart = gerrit_master_deployment_with_core_plugins
    chart["removed_plugin"] = chart["installed_plugins"].pop()
    plugins_opts_string = "{%s}" % (",".join(chart["installed_plugins"]))
    update_chart(
      test_cluster.helm, chart, {"gerritMaster.plugins.core": plugins_opts_string}
      )
    response = get_gerrit_plugin_list("http://master.%s" % (
      request.config.getoption("--ingress-url")))
    assert chart["removed_plugin"] not in response
    self._assert_installed_plugins(chart["installed_plugins"], response)

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartOtherPluginInstall(object):
  def _remove_plugin_from_install_list(self, installed_plugins):
    removed_plugin = installed_plugins.pop()
    plugin_install_list = dict()
    if installed_plugins:
      for counter, plugin in enumerate(installed_plugins):
        plugin_install_list["gerritMaster.plugins.other[%d].name" % counter] = \
          plugin["name"]
        plugin_install_list["gerritMaster.plugins.other[%d].url" % counter] = \
          plugin["url"]
        plugin_install_list["gerritMaster.plugins.other[%d].sha256" % counter] = \
          plugin["sha256"]
    else:
      plugin_install_list["gerritMaster.plugins.other"] = "{}"
    return plugin_install_list, removed_plugin, installed_plugins

  def _assert_installed_plugins(self, expected_plugins, installed_plugins):
    for plugin in expected_plugins:
      assert plugin["name"] in installed_plugins
      assert installed_plugins[plugin["name"]]["filename"] == "%s.jar" % plugin["name"]

  def test_install_other_plugins(self, request, test_cluster,
                                 gerrit_master_deployment_with_other_plugins):
    response = get_gerrit_plugin_list("http://master.%s" % (
      request.config.getoption("--ingress-url")))
    self._assert_installed_plugins(
      gerrit_master_deployment_with_other_plugins["installed_plugins"], response)

  def test_install_core_plugins_are_removed_with_update(
      self, request, test_cluster, gerrit_master_deployment_with_other_plugins):
    chart = gerrit_master_deployment_with_other_plugins
    chart_opts, chart["removed_plugin"], chart["installed_plugin"] = \
      self._remove_plugin_from_install_list(chart["installed_plugins"])
    update_chart(
      test_cluster.helm, chart, chart_opts
    )
    response = get_gerrit_plugin_list("http://master.%s" % (
      request.config.getoption("--ingress-url")))
    assert chart["removed_plugin"]["name"] not in response
    self._assert_installed_plugins(chart["installed_plugins"], response)

def test_install_other_plugins_fails_wrong_sha(
    request, test_cluster, gerrit_master_deployment_with_other_plugin_wrong_sha):
  pod_labels = "app=gerrit-master,release=%s" % (
    gerrit_master_deployment_with_other_plugin_wrong_sha["name"])
  core_v1 = client.CoreV1Api()
  pod_list = core_v1.list_pod_for_all_namespaces(
    watch=False, label_selector=pod_labels)
  if len(pod_list.items) > 1:
    raise RuntimeError("Too many gerrit-master pods with the same release name.")
  pod_name = pod_list.items[0].metadata.name
  current_status = None
  timeout = time.time() + 180
  while time.time() < timeout:
    pod = core_v1.read_namespaced_pod_status(
      pod_name, gerrit_master_deployment_with_other_plugin_wrong_sha["namespace"])
    for init_container_status in pod.status.init_container_statuses:
      if init_container_status.name == "install-plugins" and init_container_status.state.terminated:
        current_status = init_container_status
        assert current_status.state.terminated.exit_code > 0
        return

  assert current_status.state.terminated.exit_code > 0
