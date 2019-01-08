import os

def test_build_images(repository_root, build_images):
  images = os.listdir(os.path.join(repository_root, 'container-images'))
  for image in images:
    assert image in build_images.keys()
    assert build_images[image] is not None
