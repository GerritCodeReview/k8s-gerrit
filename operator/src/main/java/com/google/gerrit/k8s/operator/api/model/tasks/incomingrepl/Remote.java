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

package com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl;

import java.util.List;
import java.util.Objects;

public class Remote {
  private String name;
  private String url;
  private String time;
  private List<Fetch> fetch;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getTime() {
    return time;
  }

  public void setTime(String time) {
    this.time = time;
  }

  public List<Fetch> getFetch() {
    return fetch;
  }

  public void setFetch(List<Fetch> fetch) {
    this.fetch = fetch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fetch, name, time, url);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Remote other = (Remote) obj;
    return Objects.equals(fetch, other.fetch)
        && Objects.equals(name, other.name)
        && Objects.equals(time, other.time)
        && Objects.equals(url, other.url);
  }

  @Override
  public String toString() {
    return "Remote [name=" + name + ", url=" + url + ", time=" + time + ", fetch=" + fetch + "]";
  }
}
