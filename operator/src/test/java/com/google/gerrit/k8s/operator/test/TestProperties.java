package com.google.gerrit.k8s.operator.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class TestProperties {
  private final Properties props = getProperties();

  private static Properties getProperties() {
    String propertiesPath = System.getProperty("properties", "test.properties");
    Properties props = new Properties();
    try {
      props.load(new FileInputStream(propertiesPath));
    } catch (IOException e) {
      throw new IllegalStateException("Could not load properties file.");
    }
    return props;
  }

  public String getRWMStorageClass() {
    return props.getProperty("rwmStorageClass", "nfs-client");
  }

  public String getRegistry() {
    return props.getProperty("registry", "");
  }

  public String getRegistryOrg() {
    return props.getProperty("registryOrg", "k8sgerrit");
  }

  public String getRegistryUser() {
    return props.getProperty("registryUser", "");
  }

  public String getRegistryPwd() {
    return props.getProperty("registryPwd", "");
  }

  public String getTag() {
    return props.getProperty("tag", "");
  }

  public String getIngressDomain() {
    return props.getProperty("ingressDomain", "");
  }

  public String getLdapAdminPwd() {
    return props.getProperty("ldapAdminPwd", "");
  }
}
