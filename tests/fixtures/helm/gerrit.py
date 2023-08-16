# Copyright (C) 2022 The Android Open Source Project
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

from copy import deepcopy
from pathlib import Path

import pytest
import yaml

import pygit2 as git
import chromedriver_autoinstaller
from kubernetes import client
from selenium import webdriver
from selenium.webdriver.common.by import By

from .abstract_deployment import AbstractDeployment


class TimeOutException(Exception):
    """Exception to be raised, if some action does not finish in time."""


def dict_to_git_config(config_dict):
    config = ""
    for section, options in config_dict.items():
        config += f"[{section}]\n"
        for key, value in options.items():
            if isinstance(value, bool):
                value = "true" if value else "false"
            elif isinstance(value, list):
                for opt in value:
                    config += f"  {key} = {opt}\n"
                continue
            config += f"  {key} = {value}\n"
    return config


GERRIT_STARTUP_TIMEOUT = 240

DEFAULT_GERRIT_CONFIG = {
    "auth": {
        "type": "LDAP",
    },
    "container": {
        "user": "gerrit",
        "javaHome": "/usr/lib/jvm/java-11-openjdk",
        "javaOptions": [
            "-Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore",
            "-Xms200m",
            "-Xmx4g",
        ],
    },
    "gerrit": {
        "basePath": "git",
        "canonicalWebUrl": "http://example.com/",
        "serverId": "gerrit-1",
    },
    "httpd": {
        "listenUrl": "proxy-https://*:8080/",
        "requestLog": True,
        "gracefulStopTimeout": "1m",
    },
    "index": {"type": "LUCENE", "onlineUpgrade": False},
    "ldap": {
        "server": "ldap://openldap.openldap.svc.cluster.local:1389",
        "accountbase": "dc=example,dc=org",
        "username": "cn=admin,dc=example,dc=org",
    },
    "sshd": {"listenAddress": "off"},
}

DEFAULT_VALUES = {
    "gitRepositoryStorage": {"externalPVC": {"use": True, "name": "repo-storage"}},
    "gitGC": {"logging": {"persistence": {"enabled": False}}},
    "gerrit": {
        "etc": {"config": {"gerrit.config": dict_to_git_config(DEFAULT_GERRIT_CONFIG)}}
    },
}


# pylint: disable=R0902
class GerritDeployment(AbstractDeployment):
    def __init__(
        self,
        tmp_dir,
        cluster,
        storageclass,
        container_registry,
        container_org,
        container_version,
        ingress_url,
        ldap_admin_credentials,
        ldap_credentials,
    ):
        super().__init__(tmp_dir)
        self.cluster = cluster
        self.storageclass = storageclass
        self.ldap_credentials = ldap_credentials

        self.chart_name = "gerrit-" + self.namespace
        self.chart_path = os.path.join(
            # pylint: disable=E1101
            Path(git.discover_repository(os.path.realpath(__file__))).parent.absolute(),
            "helm-charts",
            "gerrit",
        )

        self.gerrit_config = deepcopy(DEFAULT_GERRIT_CONFIG)
        self.chart_opts = deepcopy(DEFAULT_VALUES)

        self._configure_container_images(
            container_registry, container_org, container_version
        )
        self.hostname = f"{self.namespace}.{ingress_url}"
        self._configure_ingress()
        self.set_gerrit_config_value(
            "gerrit", "canonicalWebUrl", f"http://{self.hostname}"
        )
        # pylint: disable=W1401
        self.set_helm_value(
            "gerrit.etc.secret.secure\.config",
            dict_to_git_config({"ldap": {"password": ldap_admin_credentials[1]}}),
        )

    def install(self, wait=True):
        if self.cluster.helm.is_installed(self.namespace, self.chart_name):
            self.update()
            return

        with open(self.values_file, "w", encoding="UTF-8") as f:
            yaml.dump(self.chart_opts, f)

        self.cluster.create_namespace(self.namespace)
        self._create_pvc()

        self.cluster.helm.install(
            self.chart_path,
            self.chart_name,
            values_file=self.values_file,
            fail_on_err=True,
            namespace=self.namespace,
            wait=wait,
        )

    def create_admin_account(self):
        self.wait_until_ready()
        chromedriver_autoinstaller.install()
        options = webdriver.ChromeOptions()
        options.add_argument("--headless")
        options.add_argument("--no-sandbox")
        options.add_argument("--ignore-certificate-errors")
        options.set_capability("acceptInsecureCerts", True)
        driver = webdriver.Chrome(
            options=options,
        )
        driver.get(f"http://{self.hostname}/login")
        user_input = driver.find_element(By.ID, "f_user")
        user_input.send_keys("gerrit-admin")

        pwd_input = driver.find_element(By.ID, "f_pass")
        pwd_input.send_keys(self.ldap_credentials["gerrit-admin"])

        submit_btn = driver.find_element(By.ID, "b_signin")
        submit_btn.click()

        driver.close()

    def update(self):
        with open(self.values_file, "w", encoding="UTF-8") as f:
            yaml.dump(self.chart_opts, f)

        self.cluster.helm.upgrade(
            self.chart_path,
            self.chart_name,
            values_file=self.values_file,
            fail_on_err=True,
            namespace=self.namespace,
        )

    def wait_until_ready(self):
        pod_labels = f"app=gerrit,release={self.chart_name}"
        finished_in_time = self._wait_for_pod_readiness(
            pod_labels, timeout=GERRIT_STARTUP_TIMEOUT
        )

        if not finished_in_time:
            raise TimeOutException(
                f"Gerrit pod was not ready in time ({GERRIT_STARTUP_TIMEOUT} s)."
            )

    def uninstall(self):
        self.cluster.helm.delete(self.chart_name, namespace=self.namespace)
        self.cluster.delete_namespace(self.namespace)

    def set_gerrit_config_value(self, section, key, value):
        if isinstance(self.gerrit_config[section][key], list):
            self.gerrit_config[section][key].append(value)
        else:
            self.gerrit_config[section][key] = value
        # pylint: disable=W1401
        self.set_helm_value(
            "gerrit.etc.config.gerrit\.config", dict_to_git_config(self.gerrit_config)
        )

    def _set_values_file(self):
        return os.path.join(self.tmp_dir, "values.yaml")

    def _configure_container_images(
        self, container_registry, container_org, container_version
    ):
        self.set_helm_value("images.registry.name", container_registry)
        self.set_helm_value("gitGC.image", f"{container_org}/git-gc")
        self.set_helm_value("gerrit.images.gerritInit", f"{container_org}/gerrit-init")
        self.set_helm_value("gerrit.images.gerrit", f"{container_org}/gerrit")
        self.set_helm_value("images.version", container_version)

    def _configure_ingress(self):
        self.set_helm_value("ingress.enabled", True)
        self.set_helm_value("ingress.host", self.hostname)

    def _create_pvc(self):
        core_v1 = client.CoreV1Api()
        core_v1.create_namespaced_persistent_volume_claim(
            self.namespace,
            body=client.V1PersistentVolumeClaim(
                kind="PersistentVolumeClaim",
                api_version="v1",
                metadata=client.V1ObjectMeta(name="repo-storage"),
                spec=client.V1PersistentVolumeClaimSpec(
                    access_modes=["ReadWriteMany"],
                    storage_class_name=self.storageclass,
                    resources=client.V1ResourceRequirements(
                        requests={"storage": "1Gi"}
                    ),
                ),
            ),
        )


@pytest.fixture(scope="class")
def gerrit_deployment(
    request, tmp_path_factory, test_cluster, ldap_admin_credentials, ldap_credentials
):
    deployment = GerritDeployment(
        tmp_path_factory.mktemp("gerrit_deployment"),
        test_cluster,
        request.config.getoption("--rwm-storageclass").lower(),
        request.config.getoption("--registry"),
        request.config.getoption("--org"),
        request.config.getoption("--tag"),
        request.config.getoption("--ingress-url"),
        ldap_admin_credentials,
        ldap_credentials,
    )

    yield deployment

    deployment.uninstall()


@pytest.fixture(scope="class")
def default_gerrit_deployment(gerrit_deployment):
    gerrit_deployment.install()
    gerrit_deployment.create_admin_account()

    yield gerrit_deployment
