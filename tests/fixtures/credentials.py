# pylint: disable=W0613

# Copyright (C) 2022 The Android Open Source Project
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

import os

import pytest

from passlib.apache import HtpasswdFile

import utils


@pytest.fixture(scope="session")
def credentials_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("creds")


@pytest.fixture(scope="session")
def htpasswd(credentials_dir):
    basic_auth_creds = {"user": "admin", "password": utils.create_random_string(16)}
    htpasswd_file = HtpasswdFile(os.path.join(credentials_dir, ".htpasswd"), new=True)
    htpasswd_file.set_password(basic_auth_creds["user"], basic_auth_creds["password"])
    htpasswd_file.save()
    basic_auth_creds["htpasswd_string"] = htpasswd_file.to_string()
    basic_auth_creds["htpasswd_file"] = credentials_dir
    yield basic_auth_creds
