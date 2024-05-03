// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.admission.validators;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class GerritClusterValidator implements Validator<GerritCluster> {
  @Override
  public void validate(GerritCluster gerritCluster, Operation operation) {
    if (multiplePrimaryGerritInCluster(gerritCluster)) {
      throw new NotAllowedException(
          "Only a single primary Gerrit is allowed per Gerrit Cluster.", 409);
    }

    if (primaryGerritAndReceiverInCluster(gerritCluster)) {
      throw new NotAllowedException(
          "A primary Gerrit cannot be in the same Gerrit Cluster as a Receiver.", 409);
    }

    if (multipleGerritReplicaInCluster(gerritCluster)) {
      throw new NotAllowedException(
          "Only a single Gerrit Replica is allowed per Gerrit Cluster.", 409);
    }

    if (gerritsHaveSameMetadataName(gerritCluster)) {
      throw new NotAllowedException(
          "Gerrit Primary and Replica must have different metadata.name.", 409);
    }

    GerritValidator gerritValidator = new GerritValidator();
    for (GerritTemplate gerrit : gerritCluster.getSpec().getGerrits()) {
      gerritValidator.validate(gerrit.toGerrit(gerritCluster), operation);
    }
  }

  private boolean multiplePrimaryGerritInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode() == GerritMode.PRIMARY)
            .count()
        > 1;
  }

  private boolean primaryGerritAndReceiverInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .anyMatch(g -> g.getSpec().getMode() == GerritMode.PRIMARY)
        && gerritCluster.getSpec().getReceiver() != null;
  }

  private boolean multipleGerritReplicaInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode() == GerritMode.REPLICA)
            .count()
        > 1;
  }

  private boolean gerritsHaveSameMetadataName(GerritCluster gerritCluster) {
    List<String> names =
        gerritCluster.getSpec().getGerrits().stream()
            .map(GerritTemplate::getMetadata)
            .map(ObjectMeta::getName)
            .collect(Collectors.toList());
    Set<String> duplicates =
        names.stream()
            .filter(itr -> Collections.frequency(names, itr) > 1)
            .collect(Collectors.toSet());
    return duplicates.size() > 0;
  }
}
