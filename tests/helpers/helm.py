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
import subprocess

class Helm:

  def __init__(self, kubeconfig, kubecontext):
    """Wrapper for Helm CLI.

    Arguments:
      kubeconfig {str} -- Path to kubeconfig-file describing the cluster to
                          connect to.
      kubecontext {str} -- Name of the context to use.
    """

    self.kubeconfig = kubeconfig
    self.kubecontext = kubecontext

  def _exec_command(self, cmd, fail_on_err=True):
    base_cmd = ["helm",
                "--kubeconfig", self.kubeconfig,
                "--kube-context", self.kubecontext]
    return subprocess.run(
      base_cmd + cmd,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
      check=fail_on_err,
      text=True)

  def init(self, serviceaccount):
    """Installs tiller on the cluster.

    Arguments:
      serviceaccount {str} -- Name of the service account, which tiller is meant
                              to use.

    Returns:
      CompletedProcess -- CompletedProcess-object returned by subprocess
                          containing details about the result and output of the
                          executed command.
    """

    helm_cmd = ["init", "--wait", "--service-account", serviceaccount]
    return self._exec_command(helm_cmd)

  def install(self, chart, name, values_file=None, set_values=None,
              fail_on_err=True):
    """Installs a chart on the cluster

    Arguments:
      chart {str} -- Release name or path of a helm chart
      name {str} -- Name with which the chart will be installed on the cluster

    Keyword Arguments:
      values_file {str} -- Path to a custom values.yaml file (default: {None})
      set_values {dict} -- Dictionary containing key-value-pairs that are used
                           to overwrite values in the values.yaml-file.
                           (default: {None})
      fail_on_err {bool} -- Whether to fail with an exception if the installation
                            fails (default: {True})

    Returns:
      CompletedProcess -- CompletedProcess-object returned by subprocess
                          containing details about the result and output of the
                          executed command.
    """

    helm_cmd = ["install", chart, "--dep-up", "-n", name, "--wait"]
    if values_file:
      helm_cmd.append("-f", values_file)
    if set_values:
      opt_list = ["%s=%s" % (k, v) for k, v in set_values.items()]
      helm_cmd.extend(("--set", ",".join(opt_list)))
    return self._exec_command(helm_cmd, fail_on_err)

  def list(self):
    """Lists helm charts installed on the cluster.

    Returns:
      CompletedProcess -- CompletedProcess-object returned by subprocess
                          containing details about the result and output of the
                          executed command.
    """

    helm_cmd = ["list", "--all", "--output", "json"]
    output = self._exec_command(helm_cmd).stdout
    output = json.loads(output)
    return output["Releases"]

  def upgrade(self, chart, name, values_file=None, set_values=None,
              reuse_values=True, fail_on_err=True):
    """Updates a chart on the cluster

    Arguments:
      chart {str} -- Release name or path of a helm chart
      name {str} -- Name with which the chart will be installed on the cluster

    Keyword Arguments:
      values_file {str} -- Path to a custom values.yaml file (default: {None})
      set_values {dict} -- Dictionary containing key-value-pairs that are used
                           to overwrite values in the values.yaml-file.
                           (default: {None})
      reuse_values {bool} -- Whether to reuse existing not overwritten values
                            (default: {True})
      fail_on_err {bool} -- Whether to fail with an exception if the installation
                            fails (default: {True})

    Returns:
      CompletedProcess -- CompletedProcess-object returned by subprocess
                          containing details about the result and output of the
                          executed command.
    """
    helm_cmd = ["upgrade", name, chart]
    if values_file:
      helm_cmd.append("-f", values_file)
    if reuse_values:
      helm_cmd.append("--reuse-values")
    if set_values:
      opt_list = ["%s=%s" % (k, v) for k, v in set_values.items()]
      helm_cmd.extend(("--set", ",".join(opt_list)))
    return self._exec_command(helm_cmd, fail_on_err)

  def delete(self, name, purge=True):
    """Deletes a chart from the cluster

    Arguments:
      name {str} -- Name of the chart to delete

    Keyword Arguments:
      purge {bool} -- Whether to also remove the release metadata as well
                      (default: {True})

    Returns:
      CompletedProcess -- CompletedProcess-object returned by subprocess
                          containing details about the result and output of the
                          executed command.
    """

    helm_cmd = ["delete", name]
    if purge:
      helm_cmd.append("--purge")
    return self._exec_command(helm_cmd)

  def delete_all(self, exceptions=None):
    """Deletes all charts on the cluster

    Keyword Arguments:
      exceptions {list} -- List of chart names not to delete (default: {None})
    """

    charts = self.list()
    for chart in charts:
      if chart["Name"] in exceptions:
        continue
      self.delete(chart["Name"])

  def reset(self):
    """Uninstall Tiller from cluster

    Returns:
      CompletedProcess -- CompletedProcess-object returned by subprocess
                          containing details about the result and output of the
                          executed command.
    """

    helm_cmd = ["reset", "--force"]
    return self._exec_command(helm_cmd, fail_on_err=True)
