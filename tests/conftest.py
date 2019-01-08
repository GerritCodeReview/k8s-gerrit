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
