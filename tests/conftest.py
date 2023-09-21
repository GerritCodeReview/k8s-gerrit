# pylint: disable=W0613, W0212

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

import argparse
import getpass
import os
import sys

from pathlib import Path

import docker
import pygit2 as git
import pytest

sys.path.append(os.path.join(os.path.dirname(__file__), "helpers"))

# pylint: disable=C0103
pytest_plugins = ["fixtures.credentials", "fixtures.cluster", "fixtures.helm.gerrit"]

# Base images that are not published and thus only tagged with "latest"
BASE_IMGS = ["base", "gerrit-base"]


# pylint: disable=W0622
class PasswordPromptAction(argparse.Action):
    def __init__(
        self,
        option_strings,
        dest=None,
        nargs=0,
        default=None,
        required=False,
        type=None,
        metavar=None,
        help=None,
    ):
        super().__init__(
            option_strings=option_strings,
            dest=dest,
            nargs=nargs,
            default=default,
            required=required,
            metavar=metavar,
            type=type,
            help=help,
        )

    def __call__(self, parser, args, values, option_string=None):
        password = getpass.getpass()
        setattr(args, self.dest, password)


def pytest_addoption(parser):
    parser.addoption(
        "--registry",
        action="store",
        default="",
        help="Container registry to push (if --push=true) and pull container images"
        + "from for tests on Kubernetes clusters (default: '')",
    )
    parser.addoption(
        "--registry-user",
        action="store",
        default="",
        help="Username for container registry (default: '')",
    )
    parser.addoption(
        "--registry-pwd",
        action="store",
        default="",
        help="Password for container registry (default: '')",
    )
    parser.addoption(
        "--org",
        action="store",
        default="k8sgerrit",
        help="Docker organization (default: 'k8sgerrit')",
    )
    parser.addoption(
        "--push",
        action="store_true",
        help="If set, the docker images will be pushed to the registry configured"
        + "by --registry (default: false)",
    )
    parser.addoption(
        "--tag",
        action="store",
        default=None,
        help="Tag of cached container images to test. Missing images will be built."
        + "(default: All container images will be built)",
    )
    parser.addoption(
        "--build-cache",
        action="store_true",
        help="If set, the docker cache will be used when building container images.",
    )
    parser.addoption(
        "--kubeconfig",
        action="store",
        default=None,
        help="Kubeconfig to use for cluster connection. If none is given the currently"
        + "configured context is used.",
    )
    parser.addoption(
        "--rwm-storageclass",
        action="store",
        default="shared-storage",
        help="Name of the storageclass used for ReadWriteMany access."
        + "(default: shared-storage)",
    )
    parser.addoption(
        "--ingress-url",
        action="store",
        default=None,
        help="URL of the ingress domain used by the cluster.",
    )
    parser.addoption(
        "--gerrit-user",
        action="store",
        default="admin",
        help="Gerrit admin username to be used for smoke tests. (default: admin)",
    )
    parser.addoption(
        "--gerrit-pwd",
        action=PasswordPromptAction,
        default="secret",
        help="Gerrit admin password to be used for smoke tests. (default: secret)",
    )
    parser.addoption(
        "--skip-slow", action="store_true", help="If set, skip slow tests."
    )


def pytest_collection_modifyitems(config, items):
    if config.getoption("--skip-slow"):
        skip_slow = pytest.mark.skip(reason="--skip-slow was set.")
        for item in items:
            if "slow" in item.keywords:
                item.add_marker(skip_slow)


def pytest_runtest_makereport(item, call):
    if "incremental" in item.keywords:
        if call.excinfo is not None:
            parent = item.parent
            parent._previousfailed = item


def pytest_runtest_setup(item):
    if "incremental" in item.keywords:
        previousfailed = getattr(item.parent, "_previousfailed", None)
        if previousfailed is not None:
            pytest.xfail(f"previous test failed ({previousfailed.name})")


@pytest.fixture(scope="session")
def tag_of_cached_container(request):
    return request.config.getoption("--tag")


@pytest.fixture(scope="session")
def docker_client():
    return docker.from_env()


@pytest.fixture(scope="session")
def repository_root():
    return Path(git.discover_repository(os.path.realpath(__file__))).parent.absolute()


@pytest.fixture(scope="session")
def container_images(repository_root):
    image_paths = {}
    for directory in os.listdir(os.path.join(repository_root, "container-images")):
        image_paths[directory] = os.path.join(
            repository_root, "container-images", directory
        )
    return image_paths


@pytest.fixture(scope="session")
def docker_registry(request):
    registry = request.config.getoption("--registry")
    if registry and not registry[-1] == "/":
        registry += "/"
    return registry


@pytest.fixture(scope="session")
def docker_org(request):
    org = request.config.getoption("--org")
    if org and not org[-1] == "/":
        org += "/"
    return org


@pytest.fixture(scope="session")
def docker_tag(tag_of_cached_container, repository_root):
    if tag_of_cached_container:
        return tag_of_cached_container
    return git.Repository(repository_root).describe(dirty_suffix="-dirty")


@pytest.fixture(scope="session")
def docker_build(
    request,
    docker_client,
    tag_of_cached_container,
    docker_registry,
    docker_org,
    docker_tag,
):
    def docker_build(image, name):
        if name in BASE_IMGS:
            image_name = f"{name}:latest"
        else:
            image_name = f"{docker_registry}{docker_org}{name}:{docker_tag}"

        if tag_of_cached_container:
            try:
                return docker_client.images.get(image_name)
            except docker.errors.ImageNotFound:
                print(f"Image {image_name} could not be loaded. Building it now.")

        no_cache = not request.config.getoption("--build-cache")

        build = docker_client.images.build(
            path=image,
            nocache=no_cache,
            rm=True,
            tag=image_name,
            platform="linux/amd64",
        )
        return build[0]

    return docker_build


@pytest.fixture(scope="session")
def docker_login(request, docker_client, docker_registry):
    username = request.config.getoption("--registry-user")
    if username:
        docker_client.login(
            username=username,
            password=request.config.getoption("--registry-pwd"),
            registry=docker_registry,
        )


@pytest.fixture(scope="session")
def docker_push(
    request, docker_client, docker_registry, docker_login, docker_org, docker_tag
):
    def docker_push(image):
        docker_repository = f"{docker_registry}{docker_org}{image}"
        docker_client.images.push(docker_repository, tag=docker_tag)

    return docker_push


@pytest.fixture(scope="session")
def docker_network(request, docker_client):
    network = docker_client.networks.create(
        name="k8sgerrit-test-network", scope="local"
    )

    yield network

    network.remove()


@pytest.fixture(scope="session")
def base_image(container_images, docker_build):
    return docker_build(container_images["base"], "base")


@pytest.fixture(scope="session")
def gerrit_base_image(container_images, docker_build, base_image):
    return docker_build(container_images["gerrit-base"], "gerrit-base")


@pytest.fixture(scope="session")
def gitgc_image(request, container_images, docker_build, docker_push, base_image):
    gitgc_image = docker_build(container_images["git-gc"], "git-gc")
    if request.config.getoption("--push"):
        docker_push("git-gc")
    return gitgc_image


@pytest.fixture(scope="session")
def apache_git_http_backend_image(
    request, container_images, docker_build, docker_push, base_image
):
    apache_git_http_backend_image = docker_build(
        container_images["apache-git-http-backend"], "apache-git-http-backend"
    )
    if request.config.getoption("--push"):
        docker_push("apache-git-http-backend")
    return apache_git_http_backend_image


@pytest.fixture(scope="session")
def gerrit_image(
    request, container_images, docker_build, docker_push, base_image, gerrit_base_image
):
    gerrit_image = docker_build(container_images["gerrit"], "gerrit")
    if request.config.getoption("--push"):
        docker_push("gerrit")
    return gerrit_image


@pytest.fixture(scope="session")
def gerrit_init_image(
    request, container_images, docker_build, docker_push, base_image, gerrit_base_image
):
    gerrit_init_image = docker_build(container_images["gerrit-init"], "gerrit-init")
    if request.config.getoption("--push"):
        docker_push("gerrit-init")
    return gerrit_init_image


@pytest.fixture(scope="session")
def required_plugins(request):
    return ["healthcheck"]
