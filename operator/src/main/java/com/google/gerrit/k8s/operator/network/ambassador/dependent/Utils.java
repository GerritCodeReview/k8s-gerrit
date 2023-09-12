package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.getambassador.v2.MappingSpec;
import io.getambassador.v2.MappingSpecBuilder;

public class Utils {

  static String UPLOAD_PACK_URL_PATTERN = "/.*/git-upload-pack";
  static String HOST = "gerrit-via-operator.dev.corp.arista.io";


  static ObjectMeta getCommonMetadata(GerritNetwork gerritnetwork, String name, String className) {
    ObjectMeta metadata =
        new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(gerritnetwork.getMetadata().getNamespace())
            .withLabels(
                GerritCluster.getLabels(
                    gerritnetwork.getMetadata().getName(), name, className))
            .build();
    return metadata;
  }

  static MappingSpec getCommonSpec(GerritNetwork gerritnetwork, String serviceName) {
    MappingSpec spec =
        new MappingSpecBuilder()
            .withAmbassadorId("GIBBERISH")
            .withHost(HOST)
            .withPrefix("/")
            .withService(serviceName)
            .withBypassAuth(true)
            .build();
    return spec;
  }
}