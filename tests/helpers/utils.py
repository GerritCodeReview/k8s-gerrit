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
import time

from kubernetes import client

class TimeOutException(Exception):
  """ Exception to be raised, if some action does not finish in time. """

def exec_fn_with_timeout(func, limit=60):
  """Helper function that executes a given function until it returns True or a
     given time limit is reached.

  Arguments:
    func {function} -- Function to execute. The function can return some output
                    (or None) and as a second return value a boolean indicating,
                    whether the event the function was waiting for has happened.

  Keyword Arguments:
    limit {int} -- Maximum time in seconds to wait for a positive response of
                   the function (default: {60})

  Returns:
    boolean -- False, if the timeout was reached
    any -- Last output of fn
  """

  timeout = time.time() + limit
  while time.time() < timeout:
    is_finished = func()
    if is_finished:
      return True
  return False

def wait_for_pod_readiness(pod_labels, timeout=180):
  """Helper function that can be used to wait for all pods with a given set of
     labels to be ready.

  Arguments:
    pod_labels {str} -- Label selector string to be used to select pods.
      (https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#label-selectors)

  Keyword Arguments:
    timeout {int} -- Seconds until stop waiting (default: {180})

  Returns:
    boolean -- Whether pods were ready in time.
  """

  def check_pod_readiness():
    core_v1 = client.CoreV1Api()
    pod_list = core_v1.list_pod_for_all_namespaces(
      watch=False, label_selector=pod_labels)
    for pod in pod_list.items:
      for condition in pod.status.conditions:
        if condition.type != "Ready" and condition.status != "True":
          return False
    return True

  return exec_fn_with_timeout(check_pod_readiness, limit=timeout)

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
    contains_tag = layer['Tags'] is not None and ancestor in layer['Tags']
    if contains_tag:
      break
  return contains_tag

def create_random_string(length=8):
  return "".join(
    [random.choice(string.ascii_letters) for n in range(length)]).lower()
