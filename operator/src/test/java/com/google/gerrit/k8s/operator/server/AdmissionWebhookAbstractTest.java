package com.google.gerrit.k8s.operator.server;

import static com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer.PORT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GerritClusterAdmissionWebhook;
import com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class AdmissionWebhookAbstractTest {

  protected static final String NAMESPACE = "test";
  protected TestAdmissionWebhookServer server;

  @Rule protected KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    server = new TestAdmissionWebhookServer();

    kubernetesServer.before();

    server.registerWebhook(new GerritClusterAdmissionWebhook(getClusterMode()));
    server.registerWebhook(new GerritAdmissionWebhook(getClusterMode()));
    server.start();
  }

  @AfterAll
  public void shutdown() throws Exception {
    server.stop();
  }

  protected abstract String getCustomResource();

  protected HttpURLConnection sendAdmissionRequest(CustomResource customResource)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection)
            new URL("http://localhost:" + PORT + "/admission/v1beta1/" + getCustomResource())
                .openConnection();
    http.setRequestMethod(HttpMethod.POST.asString());
    http.setRequestProperty("Content-Type", "application/json");
    http.setDoOutput(true);

    AdmissionRequest admissionReq = new AdmissionRequest();
    admissionReq.setObject(customResource);
    AdmissionReview admissionReview = new AdmissionReview();
    admissionReview.setRequest(admissionReq);

    try (OutputStream os = http.getOutputStream()) {
      byte[] input = new ObjectMapper().writer().writeValueAsBytes(admissionReview);
      os.write(input, 0, input.length);
    }
    return http;
  }

  protected abstract ClusterMode getClusterMode();


}
