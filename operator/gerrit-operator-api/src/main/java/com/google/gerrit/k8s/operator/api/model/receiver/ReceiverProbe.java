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

package com.google.gerrit.k8s.operator.api.model.receiver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.GRPCAction;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.TCPSocketAction;
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder;
import java.util.Objects;

public class ReceiverProbe extends Probe {
  private static final long serialVersionUID = 1L;

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
    return;
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

  @JsonIgnore
  public Probe withPort(int port) {
    super.setTcpSocket(new TCPSocketActionBuilder().withPort(new IntOrString(port)).build());
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(exec, grpc, tcpSocket);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    ReceiverProbe other = (ReceiverProbe) obj;
    return Objects.equals(exec, other.exec)
        && Objects.equals(grpc, other.grpc)
        && Objects.equals(tcpSocket, other.tcpSocket);
  }

  @Override
  public String toString() {
    return "ReceiverProbe [exec=" + exec + ", grpc=" + grpc + ", tcpSocket=" + tcpSocket + "]";
  }
}
