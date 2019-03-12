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

import pytest

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterChartSetup(object):
  def test_deployment(self, test_cluster, gerrit_master_h2_deployment):
    installed_charts = test_cluster.helm.list()
    gerrit_master_chart = None
    for chart in installed_charts:
      if chart["Name"] == gerrit_master_h2_deployment["name"]:
        gerrit_master_chart = chart
    assert gerrit_master_chart is not None
    assert gerrit_master_chart["Status"] == "DEPLOYED"

  def test_gerrit_pod_gets_ready(self, request, test_cluster,
                                 gerrit_master_h2_ready_deployment):
    assert gerrit_master_h2_ready_deployment
