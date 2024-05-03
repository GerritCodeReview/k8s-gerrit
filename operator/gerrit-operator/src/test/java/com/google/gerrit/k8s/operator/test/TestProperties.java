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

  public String getIstioDomain() {
    return props.getProperty("istioDomain", "");
  }

  public String getLdapAdminPwd() {
    return props.getProperty("ldapAdminPwd", "");
  }

  public String getGerritUser() {
    return props.getProperty("gerritUser", "");
  }

  public String getGerritPwd() {
    return props.getProperty("gerritPwd", "");
  }
}
