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

import java.util.Set;
import org.eclipse.jgit.lib.Config;

public class GerritConfigValidator {
  @SuppressWarnings("rawtypes")
  private final Set<RequiredOption> requiredOptions;

  @SuppressWarnings("rawtypes")
  public GerritConfigValidator(Set<RequiredOption> requiredOptions) {
    this.requiredOptions = requiredOptions;
  }

  public void check(Config cfg) {
    for (RequiredOption<?> opt : requiredOptions) {
      checkOption(cfg, opt);
    }
  }

  private void checkOption(Config cfg, RequiredOption<?> opt) {
    if (!optionExists(cfg, opt)) {
      return;
    }
    if (opt.getExpected() instanceof Set) {
      return;
    } else {
      String value = cfg.getString(opt.getSection(), opt.getSubSection(), opt.getKey());
      if (isExpectedValue(value, opt)) {
        return;
      }
      throw new IllegalStateException(
          String.format(
              "Option %s.%s.%s set to unsupported value %s. Expected %s.",
              opt.getSection(), opt.getSubSection(), opt.getKey(), value, opt.getExpected()));
    }
  }

  private boolean optionExists(Config cfg, RequiredOption<?> opt) {
    return cfg.getNames(opt.getSection(), opt.getSubSection()).contains(opt.getKey());
  }

  private boolean isExpectedValue(String value, RequiredOption<?> opt) {
    return value.equals(opt.getExpected().toString());
  }
}
