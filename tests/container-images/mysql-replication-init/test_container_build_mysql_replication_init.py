import pytest

def test_build_mysql_replication_init(mysql_replication_init_image,
    tag_of_cached_container):
  if tag_of_cached_container:
    pytest.skip("Cached image used for testing. Build will not be tested.")
  assert mysql_replication_init_image.id is not None
