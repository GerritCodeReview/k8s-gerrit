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
import socket

import pytest


class GerritContainer:
    def __init__(self, docker_client, docker_network, tmp_dir, image, configs, port):
        self.docker_client = docker_client
        self.docker_network = docker_network
        self.tmp_dir = tmp_dir
        self.image = image
        self.configs = configs
        self.port = port

        self.container = None

    def _create_config_files(self):
        tmp_config_dir = os.path.join(self.tmp_dir, "configs")
        if not os.path.isdir(tmp_config_dir):
            os.mkdir(tmp_config_dir)
        config_paths = {}
        for filename, content in self.configs.items():
            gerrit_config_file = os.path.join(tmp_config_dir, filename)
            with open(gerrit_config_file, "w", encoding="utf-8") as config_file:
                config_file.write(content)
            config_paths[filename] = gerrit_config_file
        return config_paths

    def _define_volume_mounts(self):
        volumes = {
            v: {"bind": f"/var/gerrit/etc/{k}", "mode": "rw"}
            for (k, v) in self._create_config_files().items()
        }
        volumes[os.path.join(self.tmp_dir, "lib")] = {
            "bind": "/var/gerrit/lib",
            "mode": "rw",
        }
        return volumes

    def start(self):
        self.container = self.docker_client.containers.run(
            image=self.image.id,
            user="gerrit",
            volumes=self._define_volume_mounts(),
            ports={8080: str(self.port)},
            network=self.docker_network.name,
            detach=True,
            auto_remove=True,
            platform="linux/amd64",
        )

    def stop(self):
        self.container.stop(timeout=1)


@pytest.fixture(scope="session")
def gerrit_container_factory():
    def get_gerrit_container(
        docker_client, docker_network, tmp_dir, image, gerrit_config, port
    ):
        return GerritContainer(
            docker_client, docker_network, tmp_dir, image, gerrit_config, port
        )

    return get_gerrit_container


@pytest.fixture(scope="session")
def container_endless_run_factory():
    def get_container(docker_client, image):
        return docker_client.containers.run(
            image=image.id,
            entrypoint="/bin/ash",
            command=["-c", "tail -f /dev/null"],
            user="gerrit",
            detach=True,
            auto_remove=True,
            platform="linux/amd64",
        )

    return get_container


@pytest.fixture(scope="session")
def free_port():
    skt = socket.socket()
    skt.bind(("", 0))
    port = skt.getsockname()[1]
    skt.close()
    return port
