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
import static com.google.common.truth.Truth.assertWithMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.zjsonpatch.JsonDiff;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public final class KubernetesResourceAssert {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private KubernetesResourceAssert() {}

  public static void assertUnordered(Object actual, Object expected) {
    JsonNode canonicalActual = canonicalize(MAPPER.valueToTree(actual));
    JsonNode canonicalExpected = canonicalize(MAPPER.valueToTree(expected));
    assertThat(prettyDiff(canonicalActual, canonicalExpected)).isEqualTo("[]");
  }

  public static void assertEqualWithDiff(Object actual, Object expected) {
    JsonNode actualNode = MAPPER.valueToTree(actual);
    JsonNode expectedNode = MAPPER.valueToTree(expected);
    assertThat(prettyDiff(actualNode, expectedNode)).isEqualTo("[]");
  }

  /**
   * Asserts that two {@link ConfigMap} objects are equal, printing a structured diff on failure.
   *
   * <p>Metadata is compared with plain {@code equals}. Each data entry is compared by filename:
   *
   * <ul>
   *   <li>{@code .config} files are parsed as JGit {@link Config} and diffed entry-by-entry,
   *       reporting changed, added, and removed keys per section.
   *   <li>{@code .yaml} files are parsed with Jackson and diffed as JSON Patch operations.
   * </ul>
   */
  public static void assertConfigMapEqual(ConfigMap actual, ConfigMap expected) {
    assertThat(actual.getMetadata()).isEqualTo(expected.getMetadata());
    assertThat(actual.getData().keySet()).isEqualTo(expected.getData().keySet());
    for (Map.Entry<String, String> entry : actual.getData().entrySet()) {
      String filename = entry.getKey();
      String actualValue = entry.getValue();
      String expectedValue = expected.getData().get(filename);
      if (filename.endsWith(".config")) {
        assertWithMessage(filename)
            .that(configDiff(parseConfig(actualValue), parseConfig(expectedValue)))
            .isEqualTo("");
      } else {
        assertWithMessage(filename).that(yamlDiff(actualValue, expectedValue)).isEqualTo("[]");
      }
    }
  }

  /**
   * Returns a human-readable diff of two parsed {@link Config} objects, or an empty string when
   * they are equal.
   *
   * <p>Each section (and subsection) is walked. For every key the value lists of actual and
   * expected are compared; differences are reported as:
   *
   * <ul>
   *   <li>{@code REPLACE [section] key}: actual and expected values differ
   *   <li>{@code ADD [section] key}: key present in expected but not in actual
   *   <li>{@code REMOVE [section] key}: key present in actual but not in expected
   * </ul>
   */
  static String configDiff(Config actual, Config expected) {
    StringBuilder sb = new StringBuilder();

    Set<String> allSections = new LinkedHashSet<>();
    allSections.addAll(actual.getSections());
    allSections.addAll(expected.getSections());

    for (String section : allSections) {
      Set<String> allSubsections = new LinkedHashSet<>();
      allSubsections.add(null); // null = no subsection
      allSubsections.addAll(actual.getSubsections(section));
      allSubsections.addAll(expected.getSubsections(section));

      for (String sub : allSubsections) {
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(actual.getNames(section, sub));
        allKeys.addAll(expected.getNames(section, sub));

        String header = sub == null ? "[" + section + "]" : "[" + section + " \"" + sub + "\"]";

        for (String key : allKeys) {
          String[] act = actual.getStringList(section, sub, key);
          String[] exp = expected.getStringList(section, sub, key);
          if (Arrays.equals(act, exp)) continue;

          if (act.length == 0) {
            sb.append(String.format("  ADD    %s %s = %s%n", header, key, Arrays.toString(exp)));
          } else if (exp.length == 0) {
            sb.append(
                String.format("  REMOVE %s %s (was: %s)%n", header, key, Arrays.toString(act)));
          } else {
            sb.append(
                String.format(
                    "  REPLACE %s %s%n    was: %s%n    now: %s%n",
                    header, key, Arrays.toString(act), Arrays.toString(exp)));
          }
        }
      }
    }

    return sb.toString();
  }

  /** Returns a human-readable JSON Patch diff of two YAML strings, or {@code "[]"} when equal. */
  static String yamlDiff(String actual, String expected) {
    try {
      ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
      JsonNode actualNode = yamlMapper.readTree(actual);
      JsonNode expectedNode = yamlMapper.readTree(expected);
      return prettyDiff(actualNode, expectedNode);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid YAML in diff input", e);
    }
  }

  private static Config parseConfig(String text) {
    Config cfg = new Config();
    try {
      cfg.fromText(text);
    } catch (ConfigInvalidException e) {
      throw new IllegalArgumentException("Invalid git config: " + text, e);
    }
    return cfg;
  }

  /**
   * Computes a human-readable JSON Patch diff between two already-serialized JsonNodes. Returns
   * "[]" when the nodes are equal.
   */
  public static String prettyDiff(JsonNode actual, JsonNode expected) {
    JsonNode patch = JsonDiff.asJson(actual, expected);
    if (!patch.isArray() || patch.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("diff (JSON Patch operations to turn actual into expected):\n");
    for (JsonNode op : patch) {
      String operation = op.path("op").asText();
      String path = op.path("path").asText();
      switch (operation) {
        case "replace":
          sb.append(String.format("  REPLACE %s%n with %s%n", path, op.path("value")));
          break;
        case "add":
          sb.append(String.format("  ADD    %s = %s%n", path, op.path("value")));
          break;
        case "remove":
          sb.append(String.format("  REMOVE %s (was: %s)%n", path, op.path("value")));
          break;
        case "move":
          sb.append(String.format("  MOVE   %s -> %s%n", op.path("from").asText(), path));
          break;
        default:
          sb.append(String.format("  %s %s%n", operation.toUpperCase(), op));
          break;
      }
    }
    return sb.toString();
  }

  private static JsonNode canonicalize(JsonNode node) {
    if (node == null || node.isNull() || node.isValueNode()) {
      return node;
    }

    if (node.isObject()) {
      return canonicalizeObject((ObjectNode) node);
    }

    if (node.isArray()) {
      return canonicalizeArray((ArrayNode) node);
    }

    return node;
  }

  private static ArrayNode canonicalizeArray(ArrayNode node) {
    List<JsonNode> elements = new ArrayList<>();
    node.forEach(element -> elements.add(canonicalize(element)));
    elements.sort(Comparator.comparing(JsonNode::toString));
    ArrayNode sortedArray = MAPPER.createArrayNode();
    sortedArray.addAll(elements);
    return sortedArray;
  }

  private static ObjectNode canonicalizeObject(ObjectNode node) {
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
}
