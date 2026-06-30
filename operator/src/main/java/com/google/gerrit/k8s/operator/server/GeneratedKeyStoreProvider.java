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

import static com.google.gerrit.k8s.operator.config.dependent.GerritOperatorService.SERVICE_NAME;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

@Singleton
public class GeneratedKeyStoreProvider extends AbstractKeyStoreProvider {
  private static final Path KEYSTORE_PATH = Path.of("/tmp/keystore.jks");

  private final String namespace;
  private final String password;

  @Inject
  public GeneratedKeyStoreProvider(@Named("Namespace") String namespace) {
    this.namespace = namespace;
    this.password = generatePassword();
    generateKeyStore();
  }

  @Override
  public Path getKeyStorePath() {
    return KEYSTORE_PATH;
  }

  @Override
  public String getKeyStorePassword() {
    return password;
  }

  private String getCN() {
    return String.format("%s.%s.svc", SERVICE_NAME, namespace);
  }

  private String generatePassword() {
    return RandomStringUtils.randomAlphabetic(10);
  }

  private Certificate generateCertificate(KeyPair keyPair)
      throws OperatorCreationException, CertificateException, CertIOException {
    BouncyCastleProvider bcProvider = new BouncyCastleProvider();
    Security.addProvider(bcProvider);

    Instant start = Instant.now();
    X500Name dnName = new X500Name(String.format("cn=%s", getCN()));
    DERSequence subjectAlternativeNames =
        new DERSequence(new ASN1Encodable[] {new GeneralName(GeneralName.dNSName, getCN())});

    X509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
                dnName,
                BigInteger.valueOf(start.toEpochMilli()),
                Date.from(start),
                Date.from(start.plus(365, ChronoUnit.DAYS)),
                dnName,
                keyPair.getPublic())
            .addExtension(Extension.subjectAlternativeName, true, subjectAlternativeNames);

    ContentSigner contentSigner =
        new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
    return new JcaX509CertificateConverter()
        .setProvider(bcProvider)
        .getCertificate(certBuilder.build(contentSigner));
  }

  private void generateKeyStore() {
    KEYSTORE_PATH.getParent().toFile().mkdirs();
    try (FileOutputStream fos = new FileOutputStream(KEYSTORE_PATH.toFile())) {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(4096);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();

      Certificate[] chain = {generateCertificate(keyPair)};

      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setKeyEntry(getAlias(), keyPair.getPrivate(), password.toCharArray(), chain);
      keyStore.store(fos, password.toCharArray());
    } catch (IOException
        | NoSuchAlgorithmException
        | CertificateException
        | KeyStoreException
        | OperatorCreationException e) {
      throw new IllegalStateException("Failed to create keystore.", e);
    }
  }
}
