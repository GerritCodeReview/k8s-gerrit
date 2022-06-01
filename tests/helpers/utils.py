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

import random
import string


def check_if_ancestor_image_is_inherited(image, ancestor):
    """Helper function that looks for a given ancestor image in the layers of a
      provided image. It can be used to check, whether an image uses the expected
      FROM-statement

    Arguments:
      image {docker.images.Image} -- Docker image object to be checked
      ancestor {str} -- Complete name of the expected ancestor image

    Returns:
      boolean -- True, if ancestor is inherited by image
    """

    contains_tag = False
    for layer in image.history():
        contains_tag = layer["Tags"] is not None and ancestor in layer["Tags"]
        if contains_tag:
            break
    return contains_tag


def create_random_string(length=8):
    return "".join([random.choice(string.ascii_letters) for n in range(length)]).lower()
