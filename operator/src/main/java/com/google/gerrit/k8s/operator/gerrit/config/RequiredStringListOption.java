package com.google.gerrit.k8s.operator.gerrit.config;

import java.util.List;
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
    cfg.setStringList(section, subSection, key, expected);
  }
}
