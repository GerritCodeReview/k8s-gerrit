import pytest

def test_build_apache_git_http_backend_image(apache_git_http_backend_image,
    tag_of_cached_container):
  if tag_of_cached_container:
    pytest.skip("Cached image used for testing. Build will not be tested.")
  assert apache_git_http_backend_image.id is not None
