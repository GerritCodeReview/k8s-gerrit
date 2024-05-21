# Copyright (C) 2024 The Android Open Source Project
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

from .init import GerritInit


class GerritInitMultisite(GerritInit):
    def __init__(self, site, config):
        super().__init__(site, config)

    def _symlink_mounted_site_components(self):
        self._symlink_index()
        self._symlink_or_make_data_dir()
