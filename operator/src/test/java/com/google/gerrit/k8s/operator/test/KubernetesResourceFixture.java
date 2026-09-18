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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.zjsonpatch.JsonPatch;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads YAML test fixtures with optional patch overlays.
 *
 * <p>Two patch formats are supported and detected automatically by the root node type:
 *
 * <ul>
 *   <li><b>JSON Merge Patch (RFC 7396)</b> — patch file root is an <em>object</em>. Fields in the
 *       patch overwrite the base; absent fields are left unchanged; a field set to {@code null} is
 *       removed. Object values are merged recursively. Array values are replaced wholesale.
 *   <li><b>JSON Patch (RFC 6902)</b> — patch file root is an <em>array</em> of operation objects
 *       ({@code op}, {@code path}, {@code value}). Supports surgical array mutations such as
 *       appending elements with {@code "-"} or targeting specific indices.
 * </ul>
 *
 * <p>Use Merge Patch ({@code .patch.yaml} with object root) for object-valued differences. Use JSON
 * Patch ({@code .patch.yaml} with array root) when the difference is inside an array (e.g. adding a
 * container port, env var, or volumeMount to a StatefulSet).
 *
 * <p>Example:
 *
 * <pre>{@code
 * // No patch — identical to ReconcilerUtils.loadYaml():
 * Gerrit g = TestYamlLoader.load(Gerrit.class, getClass(), "gerrit_base.yaml");
 *
 * // Merge Patch — object root, overwrites/merges fields:
 * Gerrit g = TestYamlLoader.load(Gerrit.class, getClass(),
 *     "gerrit_base.yaml", "gerrit_ha.patch.yaml");
 *
 * // JSON Patch — array root, surgical array operations:
 * StatefulSet sts = TestYamlLoader.load(StatefulSet.class, getClass(),
 *     "statefulset_base.yaml", "statefulset_ha.patch.yaml");
 *
 * // Multiple patches of either format, applied left to right:
 * StatefulSet sts = TestYamlLoader.load(StatefulSet.class, getClass(),
 *     "statefulset_base.yaml",
 *     "statefulset_ha.patch.yaml",
 *     "statefulset_es.patch.yaml");
 * }</pre>
 */
public class KubernetesResourceFixture<T extends KubernetesResource> {
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private final Class<T> type;
  private final Class<?> context;
  private final String baseFile;
  private final List<String> patchFiles;

  public KubernetesResourceFixture(Class<T> type, Class<?> context, String baseFile) {
    this(type, context, baseFile, List.of());
  }

  public KubernetesResourceFixture(
      Class<T> type, Class<?> context, String baseFile, List<String> patchFiles) {
    this.type = type;
    this.context = context;
    this.baseFile = baseFile;
    this.patchFiles = patchFiles;
  }

  /**
   * Load a YAML fixture, applying zero or more patch overlays in order.
   *
   * <p>Each patch file is applied as a JSON Merge Patch (RFC 7396) if its root node is an object,
   * or as a JSON Patch (RFC 6902) if its root node is an array.
   *
   * @param type the target class (e.g. {@code Gerrit.class})
   * @param context the test class used to resolve classpath-relative paths
   * @param baseFile path to the base YAML fixture (classpath-relative to {@code context})
   * @param patchFiles zero or more patch files to apply in order
   * @param <T> the target type
   * @return the patched object deserialised as {@code type}
   */
  public T load() {
    if (patchFiles.size() == 0) {
      return ReconcilerUtils.loadYaml(type, context, baseFile);
    }

    try {
      JsonNode merged = readYaml(context, baseFile);
      for (String patchFile : patchFiles) {
        JsonNode patch = readYaml(context, patchFile);
        merged = patch.isArray() ? JsonPatch.apply(patch, merged) : mergePatch(merged, patch);
      }
      return YAML_MAPPER.treeToValue(merged, type);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load YAML fixture '" + baseFile + "' with patches", e);
    }
  }

  private static JsonNode readYaml(Class<?> context, String resourcePath) throws IOException {
    InputStream stream = context.getResourceAsStream(resourcePath);
    if (stream == null) {
      throw new IllegalArgumentException(
          "Test resource not found: '" + resourcePath + "' relative to " + context.getName());
    }
    return YAML_MAPPER.readTree(stream);
  }

  /**
   * Apply a JSON Merge Patch (RFC 7396) to a target node. Both arguments are treated as immutable;
   * a new tree is returned.
   */
  static JsonNode mergePatch(JsonNode target, JsonNode patch) {
    if (!patch.isObject()) {
      // Non-object patch replaces the target wholesale (RFC 7396 §2).
      return patch.deepCopy();
    }

    ObjectNode result =
        target.isObject() ? (ObjectNode) target.deepCopy() : YAML_MAPPER.createObjectNode();

    for (Iterator<Map.Entry<String, JsonNode>> it = patch.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      String key = entry.getKey();
      JsonNode patchValue = entry.getValue();

      if (patchValue.isNull()) {
        // null in patch means "remove this key" (RFC 7396 §2).
        result.remove(key);
      } else if (patchValue.isObject() && result.has(key) && result.get(key).isObject()) {
        // Recurse into nested objects.
        result.set(key, mergePatch(result.get(key), patchValue));
      } else {
        result.set(key, patchValue.deepCopy());
      }
    }
    return result;
  }
}
