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

package com.google.gerrit.k8s.operator.util;

import com.google.common.flogger.FluentLogger;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class CRUDKubernetesDependentPVCResource<P extends HasMetadata>
    extends CRUDReconcileAddKubernetesDependentResource<PersistentVolumeClaim, P> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public CRUDKubernetesDependentPVCResource() {
    super(PersistentVolumeClaim.class);
  }

  @Override
  protected final PersistentVolumeClaim desired(P primary, Context<P> context) {
    PersistentVolumeClaim pvc = desiredPVC(primary, context);
    PersistentVolumeClaim existingPvc =
        context
            .getClient()
            .persistentVolumeClaims()
            .inNamespace(pvc.getMetadata().getNamespace())
            .withName(pvc.getMetadata().getName())
            .get();
    String volumeName = pvc.getSpec().getVolumeName();
    if (existingPvc != null && (volumeName == null || volumeName.isEmpty())) {
      logger.atFine().log(
          "PVC %s/%s already has bound a PV. Keeping volumeName reference.",
          pvc.getMetadata().getNamespace(), pvc.getMetadata().getName());
      PersistentVolumeClaimSpec pvcSpec = pvc.getSpec();
      pvcSpec.setVolumeName(existingPvc.getSpec().getVolumeName());
      pvc.setSpec(pvcSpec);
    }
    return pvc;
  }

  protected abstract PersistentVolumeClaim desiredPVC(P primary, Context<P> context);
}
