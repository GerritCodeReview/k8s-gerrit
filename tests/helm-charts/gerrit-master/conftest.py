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

@pytest.fixture(scope="module")
def gerrit_master_h2_deployment(request, repository_root, test_cluster,
                                docker_tag, gerrit_master_image, gitgc_image,
                                gerrit_init_image):
  chart_path = os.path.join(repository_root, "helm-charts", "gerrit-master")
  chart_name = "gerrit-master"
  chart_opts = {
    "images.registry.name": request.config.getoption("--registry"),
    "images.version": docker_tag,
    "gerritMaster.ingress.host": "master.%s" % request.config.getoption("--ingress-url")
  }
  test_cluster.helm.install(
    chart_path, chart_name, set_values=chart_opts, fail_on_err=True)

  yield

  test_cluster.helm.delete(chart_name)
