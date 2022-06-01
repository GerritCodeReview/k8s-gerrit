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
import subprocess


class HelmClient:
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
        base_cmd = [
            "helm",
            "--kubeconfig",
            self.kubeconfig,
            "--kube-context",
            self.kubecontext,
        ]
        return subprocess.run(
            base_cmd + cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=fail_on_err,
            text=True,
        )

    def install(
        self,
        chart,
        name,
        values_file=None,
        set_values=None,
        namespace=None,
        fail_on_err=True,
        wait=True,
    ):
        """Installs a chart on the cluster

        Arguments:
            chart {str} -- Release name or path of a helm chart
            name {str} -- Name with which the chart will be installed on the cluster

        Keyword Arguments:
            values_file {str} -- Path to a custom values.yaml file (default: {None})
            set_values {dict} -- Dictionary containing key-value-pairs that are used
                                to overwrite values in the values.yaml-file.
                                (default: {None})
            namespace {str} -- Namespace to install the release into (default: {default})
            fail_on_err {bool} -- Whether to fail with an exception if the installation
                                fails (default: {True})
            wait {bool} -- Whether to wait for all pods to be ready (default: {True})

        Returns:
            CompletedProcess -- CompletedProcess-object returned by subprocess
                                containing details about the result and output of the
                                executed command.
        """

        helm_cmd = ["install", name, chart, "--dependency-update"]
        if values_file:
            helm_cmd.extend(("-f", values_file))
        if set_values:
            opt_list = [f"{k}={v}" for k, v in set_values.items()]
            helm_cmd.extend(("--set", ",".join(opt_list)))
        if namespace:
            helm_cmd.extend(("--namespace", namespace))
        if wait:
            helm_cmd.append("--wait")
        return self._exec_command(helm_cmd, fail_on_err)

    def list(self, namespace=None):
        """Lists helm charts installed on the cluster.

        Keyword Arguments:
            namespace {str} -- Kubernetes namespace (default: {None})

        Returns:
            list -- List of helm chart realeases installed on the cluster.
        """

        helm_cmd = ["list", "--all", "--output", "json"]
        if namespace:
            helm_cmd.extend(("--namespace", namespace))
        output = self._exec_command(helm_cmd).stdout
        return json.loads(output)

    def upgrade(
        self,
        chart,
        name,
        namespace,
        values_file=None,
        set_values=None,
        reuse_values=True,
        fail_on_err=True,
    ):
        """Updates a chart on the cluster

        Arguments:
            chart {str} -- Release name or path of a helm chart
            name {str} -- Name with which the chart will be installed on the cluster
            namespace {str} -- Kubernetes namespace

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
        helm_cmd = ["upgrade", name, chart, "--namespace", namespace, "--wait"]
        if values_file:
            helm_cmd.extend(("-f", values_file))
        if reuse_values:
            helm_cmd.append("--reuse-values")
        if set_values:
            opt_list = [f"{k}={v}" for k, v in set_values.items()]
            helm_cmd.extend(("--set", ",".join(opt_list)))
        return self._exec_command(helm_cmd, fail_on_err)

    def delete(self, name, namespace=None):
        """Deletes a chart from the cluster

        Arguments:
            name {str} -- Name of the chart to delete

        Keyword Arguments:
            namespace {str} -- Kubernetes namespace (default: {None})

        Returns:
            CompletedProcess -- CompletedProcess-object returned by subprocess
                                containing details about the result and output of
                                the executed command.
        """

        if name not in self.list(namespace):
            return None

        helm_cmd = ["delete", name]
        if namespace:
            helm_cmd.extend(("--namespace", namespace))
        return self._exec_command(helm_cmd)

    def delete_all(self, namespace=None, exceptions=None):
        """Deletes all charts on the cluster

        Keyword Arguments:
            namespace {str} -- Kubernetes namespace (default: {None})
            exceptions {list} -- List of chart names not to delete (default: {None})
        """

        charts = self.list(namespace)
        for chart in charts:
            if exceptions and chart["name"] in exceptions:
                continue
            self.delete(chart["name"], namespace)

    def is_installed(self, namespace, chart):
        """Checks if a chart is installed in the cluster

        Keyword Arguments:
            namespace {str} -- Kubernetes namespace
            chart {str} -- Name of the chart

        Returns:
            bool -- Whether the chart is installed
        """

        for installed_chart in self.list(namespace):
            if installed_chart["name"] == chart:
                return True

        return False
