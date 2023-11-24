package com.google.gerrit.k8s.operator.v1beta2.api.model.gerrit;

public class GerritModuleData {
  private String secretRef;

  public GerritModuleData(String secretRef) {
    this.secretRef = secretRef;
  }

  public String getSecretRef() {
    return secretRef;
  }

  public void setSecretRef(String secretRef) {
    this.secretRef = secretRef;
  }
}
