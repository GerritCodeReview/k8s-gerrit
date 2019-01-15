import pytest

def test_build_gerrit_base(gerrit_base_image,
    tag_of_cached_container):
  if tag_of_cached_container:
    pytest.skip("Cached image used for testing. Build will not be tested.")
  assert gerrit_base_image.id is not None
