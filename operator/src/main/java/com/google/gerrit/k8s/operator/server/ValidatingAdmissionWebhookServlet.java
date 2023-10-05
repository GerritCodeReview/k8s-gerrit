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
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class ValidatingAdmissionWebhookServlet extends AdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public abstract Status validate(HasMetadata resource);

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    AdmissionReview admissionReq =
        objectMapper.readValue(request.getInputStream(), AdmissionReview.class);

    logger.atFine().log("Admission request received: %s", admissionReq.toString());

    response.setContentType("application/json");
    AdmissionResponseBuilder admissionRespBuilder =
        new AdmissionResponseBuilder().withUid(admissionReq.getRequest().getUid());
    Status validationStatus = validate((HasMetadata) admissionReq.getRequest().getObject());
    response.setStatus(HttpServletResponse.SC_OK);
    if (validationStatus.getCode() < 400) {
      admissionRespBuilder = admissionRespBuilder.withAllowed(true);
    } else {
      admissionRespBuilder = admissionRespBuilder.withAllowed(false).withStatus(validationStatus);
    }
    admissionReq.setResponse(admissionRespBuilder.build());
    objectMapper.writeValue(response.getWriter(), admissionReq);
    logger.atFine().log(
        "Admission request responded with %s", admissionReq.getResponse().toString());
  }

  @Override
  public String getURI() {
    return String.format("/admission/%s/%s", getVersion(), getName());
  }
}
