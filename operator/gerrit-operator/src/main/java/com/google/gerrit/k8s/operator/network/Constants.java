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
// limitations under the License.package com.google.gerrit.k8s.operator.network;

package com.google.gerrit.k8s.operator.network;

public class Constants {
  public static final String UPLOAD_PACK_URL_PATTERN = "/.*/git-upload-pack";
  public static final String INFO_REFS_PATTERN = "/.*/info/refs";
  public static final String RECEIVE_PACK_URL_PATTERN = "/.*/git-receive-pack";
  public static final String PROJECTS_URL_PATTERN = "/a/projects/.*";
  public static final String GERRIT_FORBIDDEN_URL_PATTERN = "^(/a)?/plugins/high-availability/.*$";
}
