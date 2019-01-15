import docker
import git
import os
import pytest

# Base images that are not published and thus only tagged with "latest"
BASE_IMGS = ["base", "gerrit-base"]
DOCKER_ORG = "k8sgerrit"

def pytest_addoption(parser):
  parser.addoption(
    "--tag", action="store", default=None,
    help="Tag of chached container images to test. Missing images will be built." +
         "(default: All container images will be built)"
  )

@pytest.fixture(scope="session")
def tag_of_cached_container(request):
  return request.config.getoption("--tag")

@pytest.fixture(scope="session")
def docker_client():
  return docker.from_env()

@pytest.fixture(scope="session")
def repository_root():
  git_repo = git.Repo('.', search_parent_directories=True)
  return git_repo.git.rev_parse("--show-toplevel")

@pytest.fixture(scope="session")
def container_images(repository_root):
  image_paths = dict()
  for directory in os.listdir(os.path.join(repository_root, 'container-images')):
    image_paths[directory] = os.path.join(
      repository_root,
      'container-images',
      directory)
  return image_paths

@pytest.fixture(scope="session")
def docker_build(docker_client, tag_of_cached_container):

  def docker_build(image, name):
    image_name = "%s:latest" % name
    if tag_of_cached_container:
      if name not in BASE_IMGS:
        image_name = "%s:%s" % (name, tag_of_cached_container)
        if DOCKER_ORG:
          image_name = "%s/%s" % (DOCKER_ORG, image_name)
      try:
        return docker_client.images.get(image_name)
      except:
        print("Image %s could not be loaded. Building it now." % image_name)

    build = docker_client.images.build(
        path=image,
        nocache=True,
        rm=True,
        tag=image_name
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
