// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.flogger.FluentLogger;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.javaoperatorsdk.webhook.admission.AdmissionController;
import io.javaoperatorsdk.webhook.admission.validation.Validator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class ValidatingAdmissionWebhookServlet<T extends KubernetesResource>
    extends AdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Validator<T> validator;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ValidatingAdmissionWebhookServlet(Validator<T> validator) {
    this.validator = validator;
  }

  public AdmissionReview validate(AdmissionReview admissionReview) {
    return new AdmissionController<>(validator).handle(admissionReview);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    AdmissionReview admissionReview =
        objectMapper.readValue(request.getInputStream(), AdmissionReview.class);
    objectMapper.writeValue(response.getWriter(), validate(admissionReview));
    logger.atFine().log(
        "Admission request responded with %s", admissionReview.getResponse().toString());
  }

  @Override
  public String getURI() {
    return String.format("/admission/%s/%s", getVersion(), getName());
  }
}
