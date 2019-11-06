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

import os
import sys

import docker
import git
import pytest

sys.path.append(os.path.join(os.path.dirname(__file__), "helpers"))

# Base images that are not published and thus only tagged with "latest"
BASE_IMGS = ["base", "gerrit-base"]


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
            pytest.xfail("previous test failed (%s)" % previousfailed.name)


@pytest.fixture(scope="session")
def tag_of_cached_container(request):
    return request.config.getoption("--tag")


@pytest.fixture(scope="session")
def docker_client():
    return docker.from_env()


@pytest.fixture(scope="session")
def repository_root():
    git_repo = git.Repo(".", search_parent_directories=True)
    return git_repo.git.rev_parse("--show-toplevel")


@pytest.fixture(scope="session")
def container_images(repository_root):
    image_paths = dict()
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
def docker_tag(tag_of_cached_container):
    if tag_of_cached_container:
        return tag_of_cached_container
    return git.Repo(".", search_parent_directories=True).git.describe(dirty=True)


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
            image_name = "{image}:latest".format(image=name)
        else:
            image_name = "{registry}{org}{image}:{tag}".format(
                registry=docker_registry, org=docker_org, image=name, tag=docker_tag
            )

        if tag_of_cached_container:
            try:
                return docker_client.images.get(image_name)
            except docker.errors.ImageNotFound:
                print("Image %s could not be loaded. Building it now." % image_name)

        no_cache = not request.config.getoption("--build-cache")

        build = docker_client.images.build(
            path=image, nocache=no_cache, rm=True, tag=image_name
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
        docker_repository = "{registry}{org}{image}".format(
            registry=docker_registry, org=docker_org, image=image
        )
        docker_client.images.push(docker_repository, tag=docker_tag)

    return docker_push


@pytest.fixture(scope="session")
def docker_network(request, docker_client):
    network = docker_client.networks.create(
        name="k8sgerrit-test-network", scope="local"
    )

    def remove_network():
        network.remove()

    request.addfinalizer(remove_network)

    return network


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
def gerrit_master_image(
    request, container_images, docker_build, docker_push, base_image, gerrit_base_image
):
    gerrit_master_image = docker_build(
        container_images["gerrit-master"], "gerrit-master"
    )
    if request.config.getoption("--push"):
        docker_push("gerrit-master")
    return gerrit_master_image


@pytest.fixture(scope="session")
def gerrit_slave_image(
    request, container_images, docker_build, docker_push, base_image, gerrit_base_image
):
    gerrit_slave_image = docker_build(container_images["gerrit-slave"], "gerrit-slave")
    if request.config.getoption("--push"):
        docker_push("gerrit-slave")
    return gerrit_slave_image


@pytest.fixture(scope="session")
def gerrit_init_image(
    request, container_images, docker_build, docker_push, base_image, gerrit_base_image
):
    gerrit_init_image = docker_build(container_images["gerrit-init"], "gerrit-init")
    if request.config.getoption("--push"):
        docker_push("gerrit-init")
    return gerrit_init_image
