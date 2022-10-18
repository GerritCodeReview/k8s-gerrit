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
