package com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit;

public class GerritModuleData {
  private String secretRef;

  public String getSecretRef() {
    return secretRef;
  }

  public void setSecretRef(String secretRef) {
    this.secretRef = secretRef;
  }
}
