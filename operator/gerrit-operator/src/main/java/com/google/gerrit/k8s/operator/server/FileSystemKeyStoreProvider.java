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

import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class FileSystemKeyStoreProvider extends AbstractKeyStoreProvider {
  static final String KEYSTORE_PATH = "/operator/keystore.jks";
  static final String KEYSTORE_PWD_FILE = "/operator/keystore.password";

  @Override
  public Path getKeyStorePath() {
    return Path.of(KEYSTORE_PATH);
  }

  @Override
  public String getKeyStorePassword() throws IOException {
    return Files.readString(Path.of(KEYSTORE_PWD_FILE));
  }
}
