// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.k8s.operator.gerrit.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

public class RequiredStringListOption extends RequiredOption<List<String>> {
  public RequiredStringListOption(
      String section, String subSection, String key, List<String> expected) {
    super(section, subSection, key, expected);
  }

  public RequiredStringListOption(String section, String key, List<String> expected) {
    super(section, key, expected);
  }

  @Override
  public void set(Config cfg) {
    Set<String> s = new HashSet<>();
    s.addAll(List.of(cfg.getStringList(section, subSection, key)));
    s.addAll(expected);
    cfg.setStringList(section, subSection, key, List.copyOf(s));
  }
}
