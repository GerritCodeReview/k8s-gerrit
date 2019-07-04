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

import re

import pytest


@pytest.fixture(scope="module")
def container_run(docker_client, container_endless_run_factory, gerrit_base_image):
    container_run = container_endless_run_factory(docker_client, gerrit_base_image)
    yield container_run
    container_run.stop(timeout=1)


def test_gerrit_base_inherits_from_base(gerrit_base_image):
    contains_tag = False
    for layer in gerrit_base_image.history():
        contains_tag = layer["Tags"] is not None and "base:latest" in layer["Tags"]
        if contains_tag:
            break
    assert contains_tag


@pytest.mark.docker
def test_gerrit_base_contains_java8(container_run):
    _, output = container_run.exec_run("java -version")
    output = output.strip().decode("utf-8")
    assert re.search(re.compile('openjdk version "1.8.[0-9]_[0-9]+"'), output)


@pytest.mark.docker
def test_gerrit_base_java_path(container_run):
    exit_code, output = container_run.exec_run(
        '/bin/ash -c "readlink -f $(which java)"'
    )
    output = output.strip().decode("utf-8")
    assert exit_code == 0
    assert output == "/usr/lib/jvm/java-1.8-openjdk/jre/bin/java"


@pytest.mark.docker
def test_gerrit_base_contains_gerrit_war(container_run):
    exit_code, _ = container_run.exec_run("test -f /var/war/gerrit.war")
    assert exit_code == 0

    exit_code, _ = container_run.exec_run("test -f /var/gerrit/bin/gerrit.war")
    assert exit_code == 0


@pytest.mark.docker
def test_gerrit_base_war_contains_gerrit(container_run):
    exit_code, output = container_run.exec_run("java -jar /var/war/gerrit.war version")
    assert exit_code == 0
    output = output.strip().decode("utf-8")
    assert re.search(re.compile("gerrit version.*"), output)

    exit_code, output = container_run.exec_run(
        "java -jar /var/gerrit/bin/gerrit.war version"
    )
    assert exit_code == 0
    output = output.strip().decode("utf-8")
    assert re.search(re.compile("gerrit version.*"), output)


@pytest.mark.docker
def test_gerrit_base_site_permissions(container_run):
    exit_code, _ = container_run.exec_run("test -O /var/gerrit")
    assert exit_code == 0


@pytest.mark.docker
def test_gerrit_base_war_dir_permissions(container_run):
    exit_code, _ = container_run.exec_run("test -O /var/war")
    assert exit_code == 0


def test_gerrit_base_has_entrypoint(gerrit_base_image):
    entrypoint = gerrit_base_image.attrs["ContainerConfig"]["Entrypoint"]
    assert "/var/tools/start" in entrypoint
