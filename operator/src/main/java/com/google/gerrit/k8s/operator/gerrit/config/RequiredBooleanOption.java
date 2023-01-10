package com.google.gerrit.k8s.operator.gerrit.config;

import org.eclipse.jgit.lib.Config;

public class RequiredBooleanOption extends RequiredOption<Boolean> {
  public RequiredBooleanOption(String section, String subSection, String key, boolean expected) {
    super(section, subSection, key, expected);
  }

  public RequiredBooleanOption(String section, String key, boolean expected) {
    super(section, key, expected);
  }

  @Override
  public void set(Config cfg) {
    cfg.setBoolean(section, subSection, key, expected);
  }
}
