package com.google.gerrit.k8s.operator.gerrit.config;

import org.eclipse.jgit.lib.Config;

public class RequiredStringOption extends RequiredOption<String> {
  public RequiredStringOption(String section, String subSection, String key, String expected) {
    super(section, subSection, key, expected);
  }

  public RequiredStringOption(String section, String key, String expected) {
    super(section, key, expected);
  }

  @Override
  public void set(Config cfg) {
    cfg.setString(section, subSection, key, expected);
  }
}
