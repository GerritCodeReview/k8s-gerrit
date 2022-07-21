package com.google.gerrit.k8s.operator.gerrit;

import java.util.Map;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=gerrit-configmap")
public class GerritInitConfigMapDependentResource extends CRUDKubernetesDependentResource<ConfigMap, Gerrit> {
	private static final String DEFAULT_HEALTHCHECK_CONFIG = "[healthcheck \"auth\"]\nenabled = false\n[healthcheck \"querychanges\"]\nenabled = false";

	public GerritInitConfigMapDependentResource() {
		super(ConfigMap.class);
	}

	@Override
	protected ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
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
				gerritCluster.getLabels("gerrit-configmap", this.getClass().getSimpleName());
		
		Map<String, String> configFiles = gerrit.getSpec().getConfigFiles();
		
		if (!configFiles.containsKey("healthcheck.config")) {
			configFiles.put("healthcheck.config", DEFAULT_HEALTHCHECK_CONFIG);
		}

		return new ConfigMapBuilder()
				.withApiVersion("v1")
				.withNewMetadata()
				.withName("gerrit-configmap")
				.withLabels(gerritLabels)
				.endMetadata()
				.withData(configFiles)
				.build();
	}

}