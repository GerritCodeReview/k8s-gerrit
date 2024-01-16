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

package com.google.gerrit.k8s.operator.v1beta3.api.model.shared;

public class FluentBitSidecarConfig {
  private String image = "fluent/fluent-bit:latest";
  private String outputHost;
  private int outputPort;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public int getOutputPort() {
    return outputPort;
  }

  public void setOutputPort(int outputPort) {
    this.outputPort = outputPort;
  }

  public String getOutputHost() {
    return outputHost;
  }

  public void setOutputHost(String outputHost) {
    this.outputHost = outputHost;
  }
}
