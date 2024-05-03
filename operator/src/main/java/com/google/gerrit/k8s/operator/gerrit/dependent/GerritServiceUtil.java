package com.google.gerrit.k8s.operator.gerrit.dependent;

import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.SSH_PORT;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import java.util.ArrayList;
import java.util.List;

public class GerritServiceUtil {

  public static final String HTTP_PORT_NAME = "http";
  public static final String SSH_PORT_NAME = "ssh";

  public static List<ServicePort> getServicePorts(Gerrit gerrit) {
    List<ServicePort> ports = new ArrayList<>();
    ports.add(
        new ServicePortBuilder()
            .withName(HTTP_PORT_NAME)
            .withPort(gerrit.getSpec().getService().getHttpPort())
            .withNewTargetPort(HTTP_PORT)
            .build());
    if (gerrit.isSshEnabled()) {
      ports.add(
          new ServicePortBuilder()
              .withName(SSH_PORT_NAME)
              .withPort(gerrit.getSpec().getService().getSshPort())
              .withNewTargetPort(SSH_PORT)
              .build());
    }
    return ports;
  }
}
