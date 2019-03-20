# pylint: disable=W0613

# Copyright (C) 2018 The Android Open Source Project
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

@pytest.fixture(scope="module")
def gerrit_master_deployment_with_custom_plugins(
    request, docker_tag, test_cluster, gerrit_master_h2_deployment_factory):
  chart_opts = {
    "images.registry.name": request.config.getoption("--registry"),
    "images.version": docker_tag,
    "gerritMaster.ingress.host": "master.%s" % request.config.getoption("--ingress-url"),
    "gerritMaster.plugins.core": "{replication}"
  }
  chart = gerrit_master_h2_deployment_factory(chart_opts)
  pod_labels = "app=gerrit-master,release=%s" % chart["name"]
  finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=180)
  if not finished_in_time:
    raise utils.TimeOutException("Gerrit master pod was not ready in time.")

  yield chart

  test_cluster.helm.delete(chart["name"])

@pytest.fixture(scope="module")
def gerrit_master_deployment_Update_remove_core_plugin(
    test_cluster, gerrit_master_deployment_with_custom_plugins):
  chart_opts = {
    "gerritMaster.plugins.core": "{}"
  }
  test_cluster.helm.upgrade(
    chart=gerrit_master_deployment_with_custom_plugins["chart"],
    name=gerrit_master_deployment_with_custom_plugins["name"],
    set_values=chart_opts,
    reuse_values=True,
    fail_on_err=True
  )
  pod_labels = "app=gerrit-master,release=%s" % gerrit_master_deployment_with_custom_plugins["name"]
  finished_in_time = utils.wait_for_pod_readiness(pod_labels, timeout=180)
  if not finished_in_time:
    raise utils.TimeOutException("Gerrit master pod was not ready in time.")

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartPluginInstall(object):
  def test_install_core_plugins(self, request, test_cluster,
                                gerrit_master_deployment_with_custom_plugins):
    time.sleep(5)
    list_plugins_url = "http://master.%s/a/plugins/?all" % (
      request.config.getoption("--ingress-url"))
    response = requests.get(list_plugins_url, auth=('admin', 'secret'))
    body = response.text
    body = json.loads(body[body.index('\n')+1:])
    assert response.status_code == 200
    assert "replication" in body
    assert body["replication"]["filename"] == "replication.jar"

  def test_install_core_plugins_are_removed_with_update(
      self, request, test_cluster, gerrit_master_deployment_Update_remove_core_plugin):
    time.sleep(5)
    list_plugins_url = "http://master.%s/a/plugins/?all" % (
      request.config.getoption("--ingress-url"))
    response = requests.get(list_plugins_url, auth=('admin', 'secret'))
    body = response.text
    body = json.loads(body[body.index('\n')+1:])
    assert response.status_code == 200
    assert "replication" not in body
