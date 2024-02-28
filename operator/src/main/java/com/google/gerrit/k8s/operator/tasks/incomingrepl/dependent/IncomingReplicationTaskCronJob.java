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

package com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Collections;
import java.util.Map;

public class IncomingReplicationTaskCronJob
    extends CRUDReconcileAddKubernetesDependentResource<CronJob, IncomingReplicationTask> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public IncomingReplicationTaskCronJob() {
    super(CronJob.class);
  }

  @Override
  protected CronJob desired(
      IncomingReplicationTask incomingReplTask, Context<IncomingReplicationTask> context) {
    String ns = incomingReplTask.getMetadata().getNamespace();
    String name = incomingReplTask.getMetadata().getName();

    JobTemplateSpec gitGcJobTemplate =
        new JobTemplateSpecBuilder()
            .withNewSpec()
            .withNewTemplate()
            .withNewMetadata()
            .withAnnotations(
                Map.of(
                    "sidecar.istio.io/inject",
                    "false",
                    "cluster-autoscaler.kubernetes.io/safe-to-evict",
                    "false"))
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("OnFailure")
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    return new CronJobBuilder()
        .withApiVersion("batch/v1")
        .withNewMetadata()
        .withNamespace(ns)
        .withName(name)
        .withAnnotations(
            Collections.singletonMap("app.kubernetes.io/managed-by", "gerrit-operator"))
        .endMetadata()
        .withNewSpec()
        .withSchedule(incomingReplTask.getSpec().getSchedule())
        .withConcurrencyPolicy("Forbid")
        .withJobTemplate(gitGcJobTemplate)
        .endSpec()
        .build();
  }
}
