import os

def test_build_gitgc(gitgc_image):
  assert gitgc_image.id is not None
