# pylint: disable=E1101

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

import os.path
import re

from docker.errors import NotFound

import pytest
import yaml


@pytest.fixture(scope="class")
def container_run_default(request, docker_client, gerrit_init_image, tmp_path_factory):
    tmp_site_dir = tmp_path_factory.mktemp("gerrit_site")
    container_run = docker_client.containers.run(
        image=gerrit_init_image.id,
        user="gerrit",
        volumes={tmp_site_dir: {"bind": "/var/gerrit", "mode": "rw"}},
        detach=True,
        auto_remove=True,
    )

    def stop_container():
        try:
            container_run.stop(timeout=1)
        except NotFound:
            print("Container already stopped.")

    request.addfinalizer(stop_container)

    return container_run


@pytest.fixture(scope="class")
def init_config_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("init_config")


@pytest.fixture(scope="class")
def tmp_site_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("gerrit_site")


@pytest.fixture(scope="class")
def container_run_endless(
    docker_client, gerrit_init_image, init_config_dir, tmp_site_dir
):
    container_run = docker_client.containers.run(
        image=gerrit_init_image.id,
        entrypoint="/bin/ash",
        command=["-c", "tail -f /dev/null"],
        user="gerrit",
        volumes={
            tmp_site_dir: {"bind": "/var/gerrit", "mode": "rw"},
            init_config_dir: {"bind": "/var/config", "mode": "rw"},
        },
        detach=True,
        auto_remove=True,
    )

    yield container_run
    container_run.stop(timeout=1)


@pytest.mark.docker
@pytest.mark.incremental
@pytest.mark.integration
class TestGerritInitEmptySite:
    @pytest.mark.timeout(60)
    def test_gerrit_init_gerrit_is_initialized(self, container_run_default):
        def wait_for_init_success_message():
            log = container_run_default.logs().decode("utf-8")
            return log, re.search(r"Initialized /var/gerrit", log)

        while not wait_for_init_success_message():
            continue

    @pytest.mark.timeout(60)
    def test_gerrit_init_exits_after_init(self, container_run_default):
        def wait_for_container_exit():
            try:
                container_run_default.reload()
                return False
            except NotFound:
                return True

        while not wait_for_container_exit():
            continue

        assert container_run_default.attrs["State"]["ExitCode"] == 0


@pytest.fixture(
    scope="function",
    params=[
        ["replication", "reviewnotes"],
        ["replication", "reviewnotes", "hooks"],
        ["download-commands"],
        [],
    ],
)
def plugins_to_install(request):
    return request.param


@pytest.mark.docker
@pytest.mark.incremental
@pytest.mark.integration
class TestGerritInitPluginInstallation:
    def _configure_packaged_plugins(self, file_path, plugins):
        with open(file_path, "w") as f:
            yaml.dump({"packagedPlugins": plugins}, f, default_flow_style=False)

    def test_gerrit_init_plugins_are_installed(
        self, container_run_endless, init_config_dir, plugins_to_install, tmp_site_dir
    ):
        self._configure_packaged_plugins(
            os.path.join(init_config_dir, "init.yaml"), plugins_to_install
        )

        exit_code, _ = container_run_endless.exec_run(
            "/var/tools/gerrit_init.py -s /var/gerrit -c /var/config/init.yaml"
        )
        assert exit_code == 0

        plugins_path = os.path.join(tmp_site_dir, "plugins")

        for plugin in plugins_to_install:
            assert os.path.exists(os.path.join(plugins_path, "%s.jar" % plugin))

        installed_plugins = os.listdir(plugins_path)
        for plugin in installed_plugins:
            assert os.path.splitext(plugin)[0] in plugins_to_install
