import abc
import random
import re
import string

from time import time

from kubernetes import client


class AbstractDeployment(abc.ABC):
    def __init__(self, tmp_dir):
        self.tmp_dir = tmp_dir
        self.namespace = "".join(
            [random.choice(string.ascii_letters) for n in range(8)]
        ).lower()
        self.values_file = self._set_values_file()
        self.chart_opts = {}

    @abc.abstractmethod
    def install(self, wait=True):
        pass

    @abc.abstractmethod
    def update(self):
        pass

    @abc.abstractmethod
    def uninstall(self):
        pass

    @abc.abstractmethod
    def _set_values_file(self):
        pass

    def set_helm_value(self, combined_key, value):
        nested_keys = re.split(r"(?<!\\)\.", combined_key)
        dct_pointer = self.chart_opts
        for key in nested_keys[:-1]:
            # pylint: disable=W1401
            key.replace("\.", ".")
            dct_pointer = dct_pointer.setdefault(key, {})
        # pylint: disable=W1401
        dct_pointer[nested_keys[-1].replace("\.", ".")] = value

    def _wait_for_pod_readiness(self, pod_labels, timeout=180):
        """Helper function that can be used to wait for all pods with a given set of
        labels to be ready.

        Arguments:
        pod_labels {str} -- Label selector string to be used to select pods.
            (https://kubernetes.io/docs/concepts/overview/working-with-objects/\
                labels/#label-selectors)

        Keyword Arguments:
        timeout {int} -- Time in seconds to wait for the pod status to become ready.
            (default: {180})

        Returns:
        boolean -- Whether pods were ready in time.
        """

        def check_pod_readiness():
            core_v1 = client.CoreV1Api()
            pod_list = core_v1.list_pod_for_all_namespaces(
                watch=False, label_selector=pod_labels
            )
            for pod in pod_list.items:
                for condition in pod.status.conditions:
                    if condition.type != "Ready" and condition.status != "True":
                        return False
            return True

        return self._exec_fn_with_timeout(check_pod_readiness, limit=timeout)

    def _exec_fn_with_timeout(self, func, limit=60):
        """Helper function that executes a given function until it returns True or a
        given time limit is reached.

        Arguments:
        func {function} -- Function to execute. The function can return some output
                        (or None) and as a second return value a boolean indicating,
                        whether the event the function was waiting for has happened.

        Keyword Arguments:
        limit {int} -- Maximum time in seconds to wait for a positive response of
                        the function (default: {60})

        Returns:
        boolean -- False, if the timeout was reached
        any -- Last output of fn
        """

        timeout = time() + limit
        while time() < timeout:
            is_finished = func()
            if is_finished:
                return True
        return False
