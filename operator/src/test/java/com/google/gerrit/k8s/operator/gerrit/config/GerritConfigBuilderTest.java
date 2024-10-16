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

package com.google.gerrit.k8s.operator.gerrit.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.api.model.shared.ElasticSearchConfig;
import com.google.gerrit.k8s.operator.api.model.shared.IndexConfig;
import com.google.gerrit.k8s.operator.api.model.shared.IndexType;
import com.google.gerrit.k8s.operator.api.model.shared.IngressConfig;
import java.util.Map;
import java.util.Set;
import org.assertj.core.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;

public class GerritConfigBuilderTest {

  @Test
  public void emptyGerritConfigContainsAllPresetConfiguration() {
    Gerrit gerrit = createGerrit("");
    ConfigBuilder cfgBuilder = new GerritConfigBuilder(gerrit);
    Config cfg = cfgBuilder.build();
    for (RequiredOption<?> opt : cfgBuilder.getRequiredOptions()) {
      if (opt.getExpected() instanceof String || opt.getExpected() instanceof Boolean) {
        assertTrue(
            cfg.getString(opt.getSection(), opt.getSubSection(), opt.getKey())
                .equals(opt.getExpected().toString()));
      } else if (opt.getExpected() instanceof Set) {
        assertTrue(
            Arrays.asList(cfg.getStringList(opt.getSection(), opt.getSubSection(), opt.getKey()))
                .containsAll((Set<?>) opt.getExpected()));
      }
    }
  }

  @Test
  public void invalidConfigValueIsRejected() {
    Gerrit gerrit = createGerrit("[gerrit]\n  basePath = invalid");
    assertThrows(IllegalStateException.class, () -> new GerritConfigBuilder(gerrit).build());
  }

  @Test
  public void validConfigValueIsAccepted() {
    Gerrit gerrit = createGerrit("[gerrit]\n  basePath = git");
    assertDoesNotThrow(() -> new GerritConfigBuilder(gerrit).build());
  }

  @Test
  public void canonicalWebUrlIsConfigured() {
    IngressConfig ingressConfig = new IngressConfig();
    ingressConfig.setEnabled(true);
    ingressConfig.setHost("gerrit.example.com");

    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setIngress(ingressConfig);
    Gerrit gerrit = new Gerrit();
    gerrit.setSpec(gerritSpec);
    Config cfg = new GerritConfigBuilder(gerrit).build();
    assertTrue(
        cfg.getString("gerrit", null, "canonicalWebUrl").equals("http://gerrit.example.com/"));
  }

  @Test
  public void validElasticsearchConfigIsParsed() {
    assertEsConfig("[elasticsearch]\n  codec = default");
    assertEsConfig("[elasticsearch]\n # test comment\n codec = default");
    assertEsConfig("[elasticsearch]\n codec = default # test comment");
    assertEsConfig("[elasticsearch]\n\n codec = default");

    Gerrit gerrit = createGerritWithElasticSearch("[elasticsearch]\n username = \"hash#tag\"");
    Config cfg = new GerritConfigBuilder(gerrit).build();
    assertTrue(cfg.getString("elasticsearch", null, "username").equals("hash#tag"));

    gerrit = createGerritWithElasticSearch(null);
    cfg = new GerritConfigBuilder(gerrit).build();
    assertTrue(cfg.getString("elasticsearch", null, "server").equals("http://es.example.com"));
  }

  @Test
  public void invalidElasticsearchConfigCausesError() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new GerritConfigBuilder(createGerritWithElasticSearch("[invalid]\n  codec = default"))
                .build());
    assertThrows(
        IllegalStateException.class,
        () ->
            new GerritConfigBuilder(createGerritWithElasticSearch("[invalid]\n  invalidText"))
                .build());
  }

  private void assertEsConfig(String esConfig) {
    Gerrit gerrit = createGerritWithElasticSearch(esConfig);
    Config cfg = new GerritConfigBuilder(gerrit).build();
    assertTrue(cfg.getString("elasticsearch", null, "server").equals("http://es.example.com"));
    assertTrue(cfg.getString("elasticsearch", null, "codec").equals("default"));
  }

  private Gerrit createGerrit(String configText) {
    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setConfigFiles(Map.of("gerrit.config", configText));
    Gerrit gerrit = new Gerrit();
    gerrit.setSpec(gerritSpec);
    return gerrit;
  }

  private Gerrit createGerritWithElasticSearch(String esConfigText) {
    IndexConfig indexConfig = new IndexConfig();
    indexConfig.setType(IndexType.ELASTICSEARCH);
    ElasticSearchConfig esConfig = new ElasticSearchConfig();
    esConfig.setServer("http://es.example.com");
    esConfig.setConfig(esConfigText);
    indexConfig.setElasticsearch(esConfig);

    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setIndex(indexConfig);
    Gerrit gerrit = new Gerrit();
    gerrit.setSpec(gerritSpec);
    return gerrit;
  }
}
