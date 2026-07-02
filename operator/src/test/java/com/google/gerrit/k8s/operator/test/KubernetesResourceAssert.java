// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.test;

import static com.google.common.truth.Truth.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class KubernetesResourceAssert {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private KubernetesResourceAssert() {}

  public static void assertUnordered(Object actual, Object expected) {
    JsonNode canonicalActual = canonicalize(MAPPER.valueToTree(actual));
    JsonNode canonicalExpected = canonicalize(MAPPER.valueToTree(expected));
    assertThat(canonicalActual).isEqualTo(canonicalExpected);
  }

  private static JsonNode canonicalize(JsonNode node) {
    if (node == null || node.isNull() || node.isValueNode()) {
      return node;
    }

    if (node.isObject()) {
      ObjectNode sortedObject = MAPPER.createObjectNode();
      Iterator<String> fieldNames = node.fieldNames();
      List<String> keys = new ArrayList<>();
      while (fieldNames.hasNext()) {
        keys.add(fieldNames.next());
      }
      keys.sort(String::compareTo);
      for (String key : keys) {
        sortedObject.set(key, canonicalize(node.get(key)));
      }
      return sortedObject;
    }

    if (node.isArray()) {
      List<JsonNode> elements = new ArrayList<>();
      for (JsonNode child : node) {
        elements.add(canonicalize(child));
      }
      elements.sort(Comparator.comparing(JsonNode::toString));
      ArrayNode sortedArray = MAPPER.createArrayNode();
      sortedArray.addAll(elements);
      return sortedArray;
    }

    return node;
  }
}
