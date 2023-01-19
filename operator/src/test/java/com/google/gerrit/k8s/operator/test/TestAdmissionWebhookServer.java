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
        new ServletHolder(webhook), "/admission/" + webhook.getName());
  }

  public void stop() throws Exception {
    server.stop();
  }
}
