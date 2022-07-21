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

package com.google.gerrit.k8s.operator.gerrit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=gerrit-statefulset")
public class StatefulSetDependentResource extends CRUDKubernetesDependentResource<StatefulSet, Gerrit> {
	
	private static final String SITE_VOLUME_NAME = "gerrit-site";
	public static final int HTTP_PORT = 8080;
	public static final int SSH_PORT = 29418;

	public StatefulSetDependentResource() {
		super(StatefulSet.class);
	}

	@Override
	protected StatefulSet desired(Gerrit gerrit, Context<Gerrit> context) {
		//TODO(Thomas): data files,  image version, CA cert
		
	    GerritCluster gerritCluster =
	            client
	                .resources(GerritCluster.class)
	                .inNamespace(gerrit.getMetadata().getNamespace())
	                .withName(gerrit.getSpec().getCluster())
	                .get();

	        if (gerritCluster == null) {
	          throw new IllegalStateException("The Gerrit cluster could not be found.");
	        }
	       
		Map<String, String> labels = gerritCluster.getLabels("gerrit-statefulset", this.getClass().getSimpleName());
		
		StatefulSetBuilder stsBuilder = new StatefulSetBuilder();
		
		stsBuilder
			.withApiVersion("apps/v1")
			.withMetadata(buildStsMeta(gerrit))
			.withNewSpec()
			.withServiceName(String.format("%s-service", gerrit.getMetadata().getName()))
			.withReplicas(gerrit.getSpec().getReplicas())
			.withNewUpdateStrategy()
			.withNewRollingUpdate(gerrit.getSpec().getUpdatePartition())
			.endUpdateStrategy()
			.withNewSelector()
			.withMatchLabels(labels)
			.endSelector()
			.withNewTemplate()
			.withNewMetadata()
			.withLabels(labels)
			.endMetadata()
			.withNewSpec()
			.withTolerations(gerrit.getSpec().getTolerations())
			.withTopologySpreadConstraints(gerrit.getSpec().getTopologySpreadConstraints())
			.withAffinity(gerrit.getSpec().getAffinity())
			.withPriorityClassName(gerrit.getSpec().getPriorityClassName())
			.withTerminationGracePeriodSeconds(gerrit.getSpec().getGracefulStopTimeout())
			.addAllToImagePullSecrets(gerritCluster.getSpec().getImagePullSecrets())
			.withNewSecurityContext()
			.withFsGroup(100L)
			.endSecurityContext()
			.addNewInitContainer()
			.withName("gerrit-init")
			.withImagePullPolicy(gerritCluster.getSpec().getImagePullPolicy())
			.withImage(gerritCluster.getSpec().getGerritImages().getFullImageName("gerrit-init"))
			.withResources(gerrit.getSpec().getResources())
			.addAllToVolumeMounts(getVolumeMounts(gerrit, gerritCluster, true))
			.endInitContainer()
			.addNewContainer()
			.withName("gerrit")
			.withImagePullPolicy(gerritCluster.getSpec().getImagePullPolicy())
			.withImage(gerritCluster.getSpec().getGerritImages().getFullImageName("gerrit"))
			.withNewLifecycle()
			.withNewPreStop()
			.withNewExec()
			.withCommand("/bin/ash/", "-c", "kill -2 $(pidof java) && tail --pid=$(pidof java) -f /dev/null")
			.endExec()
			.endPreStop()
			.endLifecycle()
			.withPorts(getContainerPorts(gerrit))
			.withResources(gerrit.getSpec().getResources())
			.withStartupProbe(gerrit.getSpec().getStartupProbe())
			.withReadinessProbe(gerrit.getSpec().getReadinessProbe())
			.withLivenessProbe(gerrit.getSpec().getLivenessProbe())
			.addAllToVolumeMounts(getVolumeMounts(gerrit, gerritCluster, false))
			.endContainer()
			.addAllToVolumes(getVolumes(gerrit, gerritCluster))
			.endSpec()
			.endTemplate()
			.addNewVolumeClaimTemplate()
			.withNewMetadata()
			.withName(SITE_VOLUME_NAME)
			.withLabels(labels)
			.endMetadata()
			.withNewSpec()
			.withAccessModes("ReadWriteOnce")
			.withNewResources()
			.withRequests(Map.of("storage", gerrit.getSpec().getSite().getSize()))
			.endResources()
			.withStorageClassName("default")
			.endSpec()
			.endVolumeClaimTemplate()
			.endSpec();
		
		return stsBuilder.build();
	}

	private ObjectMeta buildStsMeta(Gerrit gerrit) {
	    Map<String, String> labels = new HashMap<>();
	    labels.put("app", "gerrit");
	    labels.put("component", "gerrit");
		
		return new ObjectMetaBuilder()
				.withName(gerrit.getMetadata().getName())
				.withNamespace(gerrit.getMetadata().getNamespace())
				.withLabels(labels)
				.build();
	}
	
	private Set<Volume> getVolumes(Gerrit gerrit, GerritCluster gerritCluster) {
		Set<Volume> volumes = new HashSet<>();
		
		volumes.add(gerritCluster.getGitRepositoriesVolume());
		
		volumes.add(new VolumeBuilder()
				.withName("gerrit-init-config")
				.withNewConfigMap()
				.withName("gerrit-init")
				.endConfigMap()
				.build());
		
		volumes.add(new VolumeBuilder()
				.withName("gerrit-config")
				.withNewConfigMap()
				.withName("gerrit-config")
				.endConfigMap()
				.build());
		
		volumes.add(new VolumeBuilder()
				.withName("gerrit-secure-config")
				.withNewConfigMap()
				.withName("gerrit-secure-config")
				.endConfigMap()
				.build());
		
		if (gerrit.getSpec().getIndex().equals(GerritIndex.ELASTICSEARCH)) {
			volumes.add(new VolumeBuilder()
					.withName("gerrit-index-config")
					.withNewPersistentVolumeClaim()
					.withClaimName("gerrit-index-config-pvc")
					.endPersistentVolumeClaim()
					.build());
		}
		
		if (gerrit.getSpec().getPlugins().getCacheConfig().isEnabled() && !gerrit.getSpec().getPlugins().getDownloadedPlugins().isEmpty()) {
			volumes.add(new VolumeBuilder()
					.withName("gerrit-plugin-cache")
					.withNewPersistentVolumeClaim()
					.withClaimName("plugin-cache-pvc")
					.endPersistentVolumeClaim()
					.build());
		}
		
		return volumes;
	}
	
	private Set<VolumeMount> getVolumeMounts(Gerrit gerrit, GerritCluster gerritCluster, boolean isInitContainer) {
		Set<VolumeMount> volumeMounts = new HashSet<>();
		volumeMounts.add(new VolumeMountBuilder()
				.withName(SITE_VOLUME_NAME)
				.withMountPath("/var/gerrit")
				.build());
		volumeMounts.add(gerritCluster.getGitRepositoriesVolumeMount());
		volumeMounts.add(new VolumeMountBuilder()
				.withName("gerrit-config")
				.withMountPath("/var/mnt/etc/config")
				.build());
		volumeMounts.add(new VolumeMountBuilder()
				.withName("gerrit-secure-config")
				.withMountPath("/var/mnt/etc/secret")
				.build());
		
		if (gerrit.getSpec().getIndex().equals(GerritIndex.ELASTICSEARCH)) {
			volumeMounts.add(new VolumeMountBuilder()
					.withName("gerrit-index-config")
					.withMountPath("/var/mnt/index")
					.build());
		}
		
		if (isInitContainer) {
			volumeMounts.add(new VolumeMountBuilder()
					.withName("gerrit-init-config")
					.withMountPath("/var/config/gerrit-init.yaml")
					.withSubPath("gerrit-init.yaml")
					.build());

			if (gerrit.getSpec().getPlugins().getCacheConfig().isEnabled() && !gerrit.getSpec().getPlugins().getDownloadedPlugins().isEmpty()) {
				volumeMounts.add(new VolumeMountBuilder()
						.withName("gerrit-plugin-cache")
						.withMountPath("/var/mnt/plugins")
						.build());
			}
		}
		return volumeMounts;
	}
	
	private List<ContainerPort> getContainerPorts(Gerrit gerrit) {
		List<ContainerPort> containerPorts = new ArrayList<>();
		containerPorts.add(new ContainerPort(HTTP_PORT, null, null, "http", null));
		
		if (gerrit.getSpec().getService().getSshPort() != null) {
			containerPorts.add(new ContainerPort(SSH_PORT, null, null, "ssh", null));
		}
		
		return containerPorts;
	}
}
