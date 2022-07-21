package com.google.gerrit.k8s.operator.gerrit;

import static com.google.gerrit.k8s.operator.gerrit.StatefulSetDependentResource.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.StatefulSetDependentResource.SSH_PORT;

import java.util.Map;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=gerrit-service")
public class ServiceDependentResource extends CRUDKubernetesDependentResource<Service, Gerrit> {

	public ServiceDependentResource() {
		super(Service.class);
	}

	@Override
	protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
		 GerritCluster gerritCluster =
		            client
		                .resources(GerritCluster.class)
		                .inNamespace(gerrit.getMetadata().getNamespace())
		                .withName(gerrit.getSpec().getCluster())
		                .get();
		if (gerritCluster == null) {
			throw new IllegalStateException("The Gerrit cluster could not be found.");
		}

		Map<String, String> gerritLabels =
				gerritCluster.getLabels("gerrit-service", this.getClass().getSimpleName());

		return new ServiceBuilder()
				.withApiVersion("v1")
				.withNewMetadata()
				.withName("gerrit-service")
				.withLabels(gerritLabels)
				.endMetadata()
				.withNewSpec()
				.withType(gerrit.getSpec().getService().getType())
				.withPorts(new ServicePortBuilder().withName("http").withPort(gerrit.getSpec().getService().getHttpPort()).withNewTargetPort(HTTP_PORT).build(), new ServicePortBuilder().withName("ssh").withPort(gerrit.getSpec().getService().getSshPort()).withNewTargetPort(SSH_PORT).build())
				.withSelector(gerritLabels)
				.endSpec()
				.build();
	}

}