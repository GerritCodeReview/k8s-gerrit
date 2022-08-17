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

package com.google.gerrit.k8s.operator.gerrit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.net.URL;

class GerritPlugin implements Serializable {
  private static final long serialVersionUID = 1L;

  private String name;
  private URL url;
  private String sha1;
  private boolean installAsLibrary = false;

  public GerritPlugin() {}

  public GerritPlugin(String name) {
    this.name = name;
  }

  public GerritPlugin(String name, URL url, String sha1) {
    this.name = name;
    this.url = url;
    this.sha1 = sha1;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public URL getUrl() {
    return url;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  public String getSha1() {
    return sha1;
  }

  public void setSha1(String sha1) {
    this.sha1 = sha1;
  }

  public boolean isInstallAsLibrary() {
    return installAsLibrary;
  }

  public void setInstallAsLibrary(boolean installAsLibrary) {
    this.installAsLibrary = installAsLibrary;
  }

  @JsonIgnore
  public boolean isPackagedPlugin() {
    return this.url == null;
  }
}
