def test_build_gerrit_base(gerrit_base_image):
  assert gerrit_base_image.id is not None
