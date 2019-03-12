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

import time

import pytest
import requests

from kubernetes import client, config

import utils

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartH2(object):
  def test_deployment(self, test_cluster, gerrit_master_h2_deployment):
    installed_charts = test_cluster.helm.list()
    gerrit_master_chart = None
    for chart in installed_charts:
      if chart["Name"] == "gerrit-master":
        gerrit_master_chart = chart
    assert gerrit_master_chart is not None
    assert gerrit_master_chart["Status"] == "DEPLOYED"

  def test_gerrit_pod_gets_ready(self, request, test_cluster,
                                 gerrit_master_h2_deployment):
    def wait_for_readiness():
      core_v1 = client.CoreV1Api()
      pod_list = core_v1.list_pod_for_all_namespaces(watch=False, labe)
      core_v1.read_namespaced_pod_status(name, "default")

    finished_in_time, _ = utils.exec_fn_with_timeout(wait_for_readiness)
    assert finished_in_time

  def test_create_project_rest(self, request, test_cluster,
                               gerrit_master_h2_deployment):
    time.sleep(60)
    create_project_url = "http://master.%s/a/projects/test" % (
      request.config.getoption("--ingress-url"))
    response = requests.put(create_project_url, auth=('admin', 'secret'))
    print(response.text)
    assert response.status_code == 201
