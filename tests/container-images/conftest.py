import docker
import git
import pytest

@pytest.fixture(scope="module")
def docker_client():
  return docker.from_env()

@pytest.fixture(scope="module")
def repository_root():
  git_repo = git.Repo('.', search_parent_directories=True)
  return git_repo.git.rev_parse("--show-toplevel")
