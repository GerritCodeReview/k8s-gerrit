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

package com.google.gerrit.k8s.operator.server;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Base64;

public abstract class AbstractKeyStoreProvider implements KeyStoreProvider {
  private static final String ALIAS = "operator";
  private static final String CERT_PREFIX = "-----BEGIN CERTIFICATE-----";
  private static final String CERT_SUFFIX = "-----END CERTIFICATE-----";

  final String getAlias() {
    return ALIAS;
  }

  @Override
  public final String getCertificate()
      throws CertificateEncodingException,
          KeyStoreException,
          NoSuchAlgorithmException,
          CertificateException,
          IOException {
    StringBuilder cert = new StringBuilder();
    cert.append(CERT_PREFIX);
    cert.append("\n");
    cert.append(
        Base64.getEncoder().encodeToString(getKeyStore().getCertificate(getAlias()).getEncoded()));
    cert.append("\n");
    cert.append(CERT_SUFFIX);
    return cert.toString();
  }

  private final KeyStore getKeyStore()
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
    return KeyStore.getInstance(getKeyStorePath().toFile(), getKeyStorePassword().toCharArray());
  }
}
