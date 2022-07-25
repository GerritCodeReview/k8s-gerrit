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

package com.google.gerrit.k8s.operator.site;

public class GerritSiteSpec {

  private StorageClassConfig storageClasses;
  private GitRepositoryStorage gitRepositoryStorage;

  public StorageClassConfig getStorageClasses() {
    return storageClasses;
  }

  public GitRepositoryStorage getGitRepositoryStorage() {
    return gitRepositoryStorage;
  }

  public void setStorageClasses(StorageClassConfig storageClasses) {
    this.storageClasses = storageClasses;
  }

  public void setGitRepositoryStorage(GitRepositoryStorage gitRepositoryStorage) {
    this.gitRepositoryStorage = gitRepositoryStorage;
  }
}
