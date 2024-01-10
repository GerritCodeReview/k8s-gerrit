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

package com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.GRPCAction;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.TCPSocketAction;

@Deprecated
public class GerritProbe extends Probe {
  private static final long serialVersionUID = 1L;

  private static final HTTPGetAction HTTP_GET_ACTION =
      new HTTPGetActionBuilder()
          .withPath("/config/server/healthcheck~status")
          .withPort(new IntOrString(GerritStatefulSet.HTTP_PORT))
          .build();

  @JsonIgnore private ExecAction exec;

  @JsonIgnore private GRPCAction grpc;

  @JsonIgnore private TCPSocketAction tcpSocket;

  @Override
  public void setExec(ExecAction exec) {
    super.setExec(null);
  }

  @Override
  public void setGrpc(GRPCAction grpc) {
    super.setGrpc(null);
  }

  @Override
  public void setHttpGet(HTTPGetAction httpGet) {
    super.setHttpGet(HTTP_GET_ACTION);
  }

  @Override
  public void setTcpSocket(TCPSocketAction tcpSocket) {
    super.setTcpSocket(null);
  }

  @Override
  public ExecAction getExec() {
    return null;
  }

  @Override
  public GRPCAction getGrpc() {
    return null;
  }

  @Override
  public HTTPGetAction getHttpGet() {
    return HTTP_GET_ACTION;
  }

  @Override
  public TCPSocketAction getTcpSocket() {
    return null;
  }
}
