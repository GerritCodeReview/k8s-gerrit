package com.google.gerrit.k8s.operator.cluster;

import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class GerritIstioVirtualServiceDiscriminator
    implements ResourceDiscriminator<VirtualService, GerritCluster> {

  @Override
  public Optional<VirtualService> distinguish(
      Class<VirtualService> resource, GerritCluster gerritCluster, Context<GerritCluster> context) {
    InformerEventSource<VirtualService, GerritCluster> ies =
        (InformerEventSource<VirtualService, GerritCluster>)
            context.eventSourceRetriever().getResourceEventSourceFor(VirtualService.class);

    return ies.get(
        new ResourceID(
            GerritIstioVirtualService.getName(gerritCluster),
            gerritCluster.getMetadata().getNamespace()));
  }
}
