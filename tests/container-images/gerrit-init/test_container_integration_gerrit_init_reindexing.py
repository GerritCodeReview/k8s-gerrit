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

import os

import pytest


@pytest.fixture(scope="function")
def temp_site(tmp_path_factory):
    return tmp_path_factory.mktemp("gerrit-index-test")


@pytest.fixture(scope="function")
def container_run_endless(request, docker_client, gerrit_init_image, temp_site):
    container_run = docker_client.containers.run(
        image=gerrit_init_image.id,
        entrypoint="/bin/ash",
        command=["-c", "tail -f /dev/null"],
        volumes={str(temp_site): {"bind": "/var/gerrit", "mode": "rw"}},
        user="gerrit",
        detach=True,
        auto_remove=True,
    )

    def stop_container():
        container_run.stop(timeout=1)

    request.addfinalizer(stop_container)

    return container_run


@pytest.mark.incremental
class TestGerritReindex:
    def _get_indices(self, container):
        _, indices = container.exec_run(
            "git config -f /var/gerrit/index/gerrit_index.config "
            + "--name-only "
            + "--get-regexp index"
        )
        indices = indices.decode().strip().splitlines()
        return [index.split(".")[1] for index in indices]

    def test_gerrit_init_skips_reindexing_on_fresh_site(
        self, temp_site, container_run_endless
    ):
        assert not os.path.exists(
            os.path.join(temp_site, "index", "gerrit_index.config")
        )
        exit_code, _ = container_run_endless.exec_run(
            "/var/tools/gerrit_init.py -s /var/gerrit -c /var/config/default.config.yaml"
        )
        assert exit_code == 0
        expected_files = ["gerrit_index.config"] + self._get_indices(
            container_run_endless
        )
        for expected_file in expected_files:
            assert os.path.exists(os.path.join(temp_site, "index", expected_file))

        timestamp_index_dir = os.path.getctime(os.path.join(temp_site, "index"))

        exit_code, _ = container_run_endless.exec_run(
            "/var/tools/gerrit_reindex.py -s /var/gerrit -c /var/config/default.config.yaml"
        )
        assert exit_code == 0
        assert timestamp_index_dir == os.path.getctime(os.path.join(temp_site, "index"))

    def test_gerrit_init_fixes_missing_index_config(
        self, container_run_endless, temp_site
    ):
        container_run_endless.exec_run(
            "/var/tools/gerrit_init.py -s /var/gerrit -c /var/config/default.config.yaml"
        )
        os.remove(os.path.join(temp_site, "index", "gerrit_index.config"))

        exit_code, _ = container_run_endless.exec_run(
            "/var/tools/gerrit_reindex.py -s /var/gerrit -c /var/config/default.config.yaml"
        )
        assert exit_code == 0

        exit_code, _ = container_run_endless.exec_run("/var/gerrit/bin/gerrit.sh start")
        assert exit_code == 0

    def test_gerrit_init_fixes_unready_indices(self, container_run_endless):
        container_run_endless.exec_run(
            "/var/tools/gerrit_init.py -s /var/gerrit -c /var/config/default.config.yaml"
        )

        indices = self._get_indices(container_run_endless)
        assert indices
        container_run_endless.exec_run(
            "git config -f /var/gerrit/index/gerrit_index.config %s false" % indices[0]
        )

        exit_code, _ = container_run_endless.exec_run(
            "/var/tools/gerrit_reindex.py -s /var/gerrit -c /var/config/default.config.yaml"
        )
        assert exit_code == 0

        exit_code, _ = container_run_endless.exec_run("/var/gerrit/bin/gerrit.sh start")
        assert exit_code == 0

    def test_gerrit_init_fixes_outdated_indices(self, container_run_endless, temp_site):
        container_run_endless.exec_run(
            "/var/tools/gerrit_init.py -s /var/gerrit -c /var/config/default.config.yaml"
        )

        index = self._get_indices(container_run_endless)[0]
        (name, version) = index.split("_")
        os.rename(
            os.path.join(temp_site, "index", index),
            os.path.join(
                temp_site, "index", "{name}_{0:04d}".format(int(version) - 1, name=name)
            ),
        )

        exit_code, _ = container_run_endless.exec_run(
            "/var/tools/gerrit_reindex.py -s /var/gerrit -c /var/config/default.config.yaml"
        )
        assert exit_code == 0

        exit_code, _ = container_run_endless.exec_run("/var/gerrit/bin/gerrit.sh start")
        assert exit_code == 0
