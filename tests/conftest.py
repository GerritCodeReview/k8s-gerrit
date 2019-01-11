#!/usr/bin/python3

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

import docker
import git
import os
import pytest

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
def docker_build(docker_client):

  def docker_build(image, tag):
    build = docker_client.images.build(
        path=image,
        nocache=True,
        rm=True,
        tag=tag
      )
    return build[0]

  return docker_build

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
