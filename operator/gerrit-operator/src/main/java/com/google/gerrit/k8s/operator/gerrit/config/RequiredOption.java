// Copyright (C) 2022 The Android Open Source Project
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

public class RequiredOption<T> {
  private final String section;
  private final String subSection;
  private final String key;
  private final T expected;

  public RequiredOption(String section, String subSection, String key, T expected) {
    this.section = section;
    this.subSection = subSection;
    this.key = key;
    this.expected = expected;
  }

  public RequiredOption(String section, String key, T expected) {
    this(section, null, key, expected);
  }

  public String getSection() {
    return section;
  }

  public String getSubSection() {
    return subSection;
  }

  public String getKey() {
    return key;
  }

  public T getExpected() {
    return expected;
  }
}
