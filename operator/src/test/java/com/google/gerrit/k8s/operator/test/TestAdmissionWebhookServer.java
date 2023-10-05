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

package com.google.gerrit.k8s.operator.test;

import com.google.gerrit.k8s.operator.server.AdmissionWebhookServlet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class TestAdmissionWebhookServer {
  public static final String KEYSTORE_PATH = "/operator/keystore.jks";
  public static final String KEYSTORE_PWD_FILE = "/operator/keystore.password";
  public static final int PORT = 8080;

  private final Server server = new Server();
  private final ServletHandler servletHandler = new ServletHandler();

  public void start() throws Exception {
    HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory();

    ServerConnector connector = new ServerConnector(server, httpConnectionFactory);
    connector.setPort(PORT);
    server.setConnectors(new Connector[] {connector});
    server.setHandler(servletHandler);

    server.start();
  }

  public void registerWebhook(AdmissionWebhookServlet webhook) {
    servletHandler.addServletWithMapping(
        new ServletHolder(webhook), "/admission/" + webhook.getVersion() + "/" + webhook.getName());
  }

  public void stop() throws Exception {
    server.stop();
  }
}
