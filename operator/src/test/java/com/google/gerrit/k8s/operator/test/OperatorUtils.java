package com.google.gerrit.k8s.operator.test;

import io.fabric8.kubernetes.api.builder.Builder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.BuilderUtils;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.io.IOException;
import java.io.InputStream;

/**
 * Workaround the problem that {@link ReconcilerUtils} doesn't provide a method to load Yaml file
 * using {@link ClassLoader#getResourceAsStream(String)}.
 */
public class OperatorUtils {
  public static <T> T loadYaml(Class<T> clazz, Class<?> loader, String yaml) {
    try (InputStream is = getResourceAsStream(loader, yaml)) {
      if (is == null) {
        throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
      }
      if (Builder.class.isAssignableFrom(clazz)) {
        return BuilderUtils.newBuilder(
            clazz, Serialization.unmarshal(is, BuilderUtils.builderTargetType(clazz)));
      }
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }

  private static InputStream getResourceAsStream(Class<?> relativeToClass, String resourceName) {
    ClassLoader loader = relativeToClass.getClassLoader();
    String resource = normalizeResourcePath(relativeToClass, resourceName);
    return loader.getResourceAsStream(resource);
  }

  private static String normalizeResourcePath(Class<?> relativeToClass, String resourceName) {
    String packageName = relativeToClass.getPackage().getName();
    while (resourceName.startsWith("../")) {
      resourceName = resourceName.substring(3, resourceName.length());
      packageName = packageName.substring(0, packageName.lastIndexOf("."));
    }
    String path = packageName.replace('.', '/');
    return path + '/' + resourceName;
  }
}
