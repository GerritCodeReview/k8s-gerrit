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

package com.google.gerrit.k8s.operator.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@Singleton
public class HttpServer {
  public static final String KEYSTORE_PATH = "/operator/keystore.jks";
  public static final String KEYSTORE_PWD_FILE = "/operator/keystore.password";
  public static final int PORT = 8080;

  private final Server server = new Server();
  private final KeyStoreProvider keyStoreProvider;
  private final Set<AdmissionWebhookServlet> admissionWebhookServlets;

  @Inject
  public HttpServer(
      KeyStoreProvider keyStoreProvider, Set<AdmissionWebhookServlet> admissionWebhookServlets) {
    this.keyStoreProvider = keyStoreProvider;
    this.admissionWebhookServlets = admissionWebhookServlets;
  }

  public void start() throws Exception {
    SslContextFactory.Server ssl = new SslContextFactory.Server();
    ssl.setKeyStorePath(keyStoreProvider.getKeyStorePath().toString());
    ssl.setTrustStorePath(keyStoreProvider.getKeyStorePath().toString());
    ssl.setKeyStorePassword(keyStoreProvider.getKeyStorePassword());
    ssl.setTrustStorePassword(keyStoreProvider.getKeyStorePassword());
    ssl.setSniRequired(false);

    HttpConfiguration sslConfiguration = new HttpConfiguration();
    sslConfiguration.addCustomizer(new SecureRequestCustomizer(false));
    HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(sslConfiguration);

    ServerConnector connector = new ServerConnector(server, ssl, httpConnectionFactory);
    connector.setPort(PORT);
    server.setConnectors(new Connector[] {connector});

    ServletHandler servletHandler = new ServletHandler();
    for (AdmissionWebhookServlet servlet : admissionWebhookServlets) {
      servletHandler.addServletWithMapping(new ServletHolder(servlet), servlet.getURI());
    }
    servletHandler.addServletWithMapping(HealthcheckServlet.class, "/health");
    server.setHandler(servletHandler);
    server.setRequestLog(new CustomRequestLog());

    server.start();
  }
}
