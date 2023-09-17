package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.getambassador.v2.MappingSpec;
import io.getambassador.v2.MappingSpecBuilder;
import java.util.List;

public class Utils {

  static String UPLOAD_PACK_URL_PATTERN = "/.*/git-upload-pack";

  static ObjectMeta getCommonMetadata(GerritNetwork gerritnetwork, String name, String className) {
    ObjectMeta metadata =
        new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(gerritnetwork.getMetadata().getNamespace())
            .withLabels(
                GerritCluster.getLabels(gerritnetwork.getMetadata().getName(), name, className))
            .build();
    return metadata;
  }

  static MappingSpec getCommonSpec(GerritNetwork gerritnetwork, String serviceName) {
    MappingSpec spec =
        new MappingSpecBuilder()
            .withAmbassadorId(getAmbassadorIds(gerritnetwork))
            .withHost(gerritnetwork.getSpec().getIngress().getHost())
            .withPrefix("/")
            .withService(serviceName)
            .withBypassAuth(false)
            .build();
    return spec;
  }

  static List<String> getAmbassadorIds(GerritNetwork gerritnetwork) {
    return gerritnetwork.getSpec().getIngress().getAmbassadorId();
  }
}
