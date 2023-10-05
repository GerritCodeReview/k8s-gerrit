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

package com.google.gerrit.k8s.operator.v1alpha.api.model.receiver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverDeployment;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.GRPCAction;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.TCPSocketAction;
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder;

@Deprecated
public class ReceiverProbe extends Probe {
  private static final long serialVersionUID = 1L;

  private static final TCPSocketAction TCP_SOCKET_ACTION =
      new TCPSocketActionBuilder().withPort(new IntOrString(ReceiverDeployment.HTTP_PORT)).build();

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
    super.setHttpGet(null);
  }

  @Override
  public void setTcpSocket(TCPSocketAction tcpSocket) {
    super.setTcpSocket(TCP_SOCKET_ACTION);
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
    return null;
  }

  @Override
  public TCPSocketAction getTcpSocket() {
    return TCP_SOCKET_ACTION;
  }
}
