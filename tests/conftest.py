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

sys.path.append(os.path.join(os.path.dirname(__file__), 'helpers'))

# Base images that are not published and thus only tagged with "latest"
BASE_IMGS = ["base", "gerrit-base"]
DOCKER_ORG = "k8sgerrit"

def pytest_addoption(parser):
  parser.addoption(
    "--tag", action="store", default=None,
    help= \
      "Tag of cached container images to test. Missing images will be built." + \
      "(default: All container images will be built)"
  )
  parser.addoption(
    "--build-cache", action="store_true",
    help="If set, the docker cache will be used when building container images."
  )
  parser.addoption(
    "--skip-slow", action="store_true",
    help="If set, skip slow tests."
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
      repository_root,
      "container-images",
      directory)
  return image_paths

@pytest.fixture(scope="session")
def docker_build(request, docker_client, tag_of_cached_container):

  def docker_build(image, name):
    image_name = "%s:latest" % name
    if tag_of_cached_container:
      if name not in BASE_IMGS:
        image_name = "%s:%s" % (name, tag_of_cached_container)
        if DOCKER_ORG:
          image_name = "%s/%s" % (DOCKER_ORG, image_name)
      try:
        return docker_client.images.get(image_name)
      except docker.errors.ImageNotFound:
        print("Image %s could not be loaded. Building it now." % image_name)

    no_cache = not request.config.getoption("--build-cache")

    build = docker_client.images.build(
      path=image,
      nocache=no_cache,
      rm=True,
      tag=image_name)
    return build[0]

  return docker_build

@pytest.fixture(scope="session")
def docker_network(request, docker_client):
  network = docker_client.networks.create(
    name="k8sgerrit-test-network",
    scope="local"
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
def gitgc_image(container_images, docker_build, base_image):
  return docker_build(container_images["git-gc"], "git-gc")

@pytest.fixture(scope="session")
def apache_git_http_backend_image(container_images, docker_build, base_image):
  return docker_build(
    container_images["apache-git-http-backend"],
    "apache-git-http-backend")

@pytest.fixture(scope="session")
def gerrit_master_image(container_images, docker_build, base_image,
                        gerrit_base_image):
  return docker_build(container_images["gerrit-master"], "gerrit-master")

@pytest.fixture(scope="session")
def gerrit_slave_image(container_images, docker_build, base_image,
                       gerrit_base_image):
  return docker_build(container_images["gerrit-slave"], "gerrit-slave")

@pytest.fixture(scope="session")
def gerrit_init_image(container_images, docker_build, base_image,
                      gerrit_base_image):
  return docker_build(container_images["gerrit-init"], "gerrit-init")

@pytest.fixture(scope="session")
def mysql_replication_init_image(container_images, docker_build):
  return docker_build(
    container_images["mysql-replication-init"],
    "mysql-replication-init")
