#!/usr/bin/python3

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

class Logger:

  def error(self, msg):
    write(self, "ERROR", msg)

  def warn(self, msg):
    write(self, "WARN", msg)

  def info(self, msg):
    write(self, "INFO", msg)

  def debug(self, msg):
    write(self, "DEBUG", msg)

  def trace(self, msg):
    write(self, "TRACE", msg)

  def write(self, level, msg):
    print("[%s] %s %s" % (time.ctime(), level, msg))
