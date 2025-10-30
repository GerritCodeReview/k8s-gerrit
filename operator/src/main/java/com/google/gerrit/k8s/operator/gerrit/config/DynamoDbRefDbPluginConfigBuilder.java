// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.gerrit.config;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import java.util.ArrayList;
import java.util.List;

public class DynamoDbRefDbPluginConfigBuilder extends ConfigBuilder {
  public DynamoDbRefDbPluginConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("aws-dynamodb-refdb.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    String endpoint =
            gerrit.getSpec().getRefdb().getDynamoDb().getEndpoint();

    if (endpoint == null || endpoint.isEmpty()) {
      endpoint =
              "https://dynamodb."
                      + gerrit.getSpec().getRefdb().getDynamoDb().getRegion()
                      + ".amazonaws.com";
    }
    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "aws-dynamodb-refdb",
            "region",
            gerrit.getSpec().getRefdb().getDynamoDb().getRegion()));

    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "aws-dynamodb-refdb",
            "endpoint",
            endpoint));

    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "aws-dynamodb-refdb",
            "locksTableName",
            gerrit.getSpec().getRefdb().getDynamoDb().getLocksTableName()));

    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "aws-dynamodb-refdb",
            "refsDbTableName",
            gerrit.getSpec().getRefdb().getDynamoDb().getRefsDbTableName()));

    return requiredOptions;
  }
}
