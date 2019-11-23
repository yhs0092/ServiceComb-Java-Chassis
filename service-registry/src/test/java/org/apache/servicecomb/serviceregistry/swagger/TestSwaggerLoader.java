/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicecomb.serviceregistry.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.Holder;

import org.apache.commons.io.IOUtils;
import org.apache.servicecomb.foundation.common.exceptions.ServiceCombException;
import org.apache.servicecomb.foundation.common.utils.JvmUtils;
import org.apache.servicecomb.serviceregistry.TestRegistryBase;
import org.apache.servicecomb.serviceregistry.api.registry.Microservice;
import org.apache.servicecomb.swagger.SwaggerUtils;
import org.apache.servicecomb.swagger.generator.SwaggerGenerator;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.swagger.models.Swagger;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;

public class TestSwaggerLoader extends TestRegistryBase {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void registerSwagger() {
    Swagger swagger = SwaggerGenerator.generate(Hello.class);
    serviceRegistry.getSwaggerLoader().registerSwagger("default:ms2", schemaId, swagger);

    Microservice microservice = appManager.getOrCreateMicroserviceVersions(appId, serviceName)
        .getVersions().values().iterator().next().getMicroservice();
    Assert.assertSame(swagger, serviceRegistry.getSwaggerLoader().loadSwagger(microservice, schemaId));
  }

  @Test
  public void loadFromRemote() {
    Swagger swagger = SwaggerGenerator.generate(Hello.class);
    new Expectations(serviceRegistry.getServiceRegistryClient()) {
      {
        serviceRegistry.getServiceRegistryClient().getAggregatedSchema(serviceId, schemaId);
        result = SwaggerUtils.swaggerToString(swagger);
      }
    };

    serviceRegistry.getSwaggerLoader().unregisterSwagger(appId, serviceName, schemaId);

    Microservice microservice = appManager.getOrCreateMicroserviceVersions(appId, serviceName)
        .getVersions().values().iterator().next().getMicroservice();
    Swagger loadedSwagger = serviceRegistry.getSwaggerLoader().loadSwagger(microservice, schemaId);
    Assert.assertNotSame(swagger, loadedSwagger);
    Assert.assertEquals(swagger, loadedSwagger);
  }

  @Test
  public void loadFromResource_sameApp_dirWithoutApp() throws IOException {
    Swagger swagger = SwaggerGenerator.generate(Hello.class);
    mockLocalResource(swagger, String.format("microservices/%s/%s.yaml", serviceName, schemaId));

    serviceRegistry.getSwaggerLoader().unregisterSwagger(appId, serviceName, schemaId);

    Microservice microservice = appManager.getOrCreateMicroserviceVersions(appId, serviceName)
        .getVersions().values().iterator().next().getMicroservice();
    Swagger loadedSwagger = serviceRegistry.getSwaggerLoader().loadSwagger(microservice, schemaId);
    Assert.assertNotSame(swagger, loadedSwagger);
    Assert.assertEquals(swagger, loadedSwagger);
  }

  @Test
  public void loadFromResource_sameApp_dirWithApp() throws IOException {
    Swagger swagger = SwaggerGenerator.generate(Hello.class);
    mockLocalResource(swagger, String.format("applications/%s/%s/%s.yaml", appId, serviceName, schemaId));

    serviceRegistry.getSwaggerLoader().unregisterSwagger(appId, serviceName, schemaId);

    Microservice microservice = appManager.getOrCreateMicroserviceVersions(appId, serviceName)
        .getVersions().values().iterator().next().getMicroservice();
    Swagger loadedSwagger = serviceRegistry.getSwaggerLoader().loadSwagger(microservice, schemaId);
    Assert.assertNotSame(swagger, loadedSwagger);
    Assert.assertEquals(swagger, loadedSwagger);
  }

  @Test
  public void loadFromResource_diffApp_dirWithoutApp() throws IOException {
    Swagger swagger = SwaggerGenerator.generate(Hello.class);
    mockLocalResource(swagger, String.format("microservices/%s/%s.yaml", "ms3", schemaId));

    Microservice microservice = appManager.getOrCreateMicroserviceVersions("other", "ms3")
        .getVersions().values().iterator().next().getMicroservice();

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage(
        "no schema in local, and can not get schema from service center, appId=other, microserviceName=ms3, version=1.0, serviceId=003, schemaId=hello.");
    serviceRegistry.getSwaggerLoader().loadSwagger(microservice, schemaId);
  }

  @Test
  public void loadFromResource_diffApp_dirWithApp() throws IOException {
    Swagger swagger = SwaggerGenerator.generate(Hello.class);
    mockLocalResource(swagger, String.format("applications/%s/%s/%s.yaml", "other", "ms3", schemaId));

    Microservice microservice = appManager.getOrCreateMicroserviceVersions("other", "ms3")
        .getVersions().values().iterator().next().getMicroservice();
    Swagger loadedSwagger = serviceRegistry.getSwaggerLoader().loadSwagger(microservice, schemaId);
    Assert.assertNotSame(swagger, loadedSwagger);
    Assert.assertEquals(swagger, loadedSwagger);
  }

  private void mockLocalResource(Swagger swagger, String path) {
    mockLocalResource(SwaggerUtils.swaggerToString(swagger), path);
  }

  private void mockLocalResource(String content, String path) {
    Map<String, String> resourceMap = new HashMap<>();
    resourceMap.put(path, content);

    mockLocalResource(resourceMap);
  }

  private void mockLocalResource(Map<String, String> resourceMap) {
    Holder<String> pathHolder = new Holder<>();
    URL url = new MockUp<URL>() {
      @Mock
      String getPath() {
        return pathHolder.value;
      }

      @Mock
      String toExternalForm() {
        return pathHolder.value;
      }
    }.getMockInstance();
    ClassLoader classLoader = new MockUp<ClassLoader>() {
      @Mock
      URL getResource(String name) {
        pathHolder.value = name;
        return resourceMap.containsKey(name) ? url : null;
      }

      @Mock
      InputStream getResourceAsStream(String name) {
        String content = resourceMap.get(name);
        return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      }
    }.getMockInstance();
    new Expectations(JvmUtils.class) {
      {
        JvmUtils.findClassLoader();
        result = classLoader;
      }
    };
    new MockUp<IOUtils>() {
      @Mock
      String toString(URL url, Charset encoding) {
        return resourceMap.get(url.getPath());
      }
    };
  }

  @Test
  public void should_ignore_not_exist_location_when_register_swagger_in_location() {
    Map<String, Object> apps = Deencapsulation.getField(serviceRegistry.getSwaggerLoader(), "apps");
    apps.clear();
    serviceRegistry.getSwaggerLoader().registerSwaggersInLocation("notExistPath");
    assertThat(apps).isEmpty();
  }

  @Test
  public void should_ignore_non_yaml_file_when_register_swagger_in_location() {
    serviceRegistry.getSwaggerLoader().registerSwaggersInLocation("swagger-del");
    assertThat(serviceRegistry.getSwaggerLoader().loadFromMemory(appId, serviceName, "other")).isNull();
  }

  @Test
  public void should_throw_exception_when_register_invalid_swagger_in_location() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("failed to register swaggers, microserviceName=default, location=location.");
    expectedException.expectCause(instanceOf(ServiceCombException.class));
    expectedException.expectCause(allOf(instanceOf(ServiceCombException.class),
        hasProperty("message", is("Parse swagger from url failed, url=location/invalid.yaml"))));

    Map<String, String> resourceMap = new HashMap<>();
    resourceMap.put("location", "invalid.yaml");
    resourceMap.put("location/invalid.yaml", "invalid yaml content");
    mockLocalResource(resourceMap);

    serviceRegistry.getSwaggerLoader().registerSwaggersInLocation("location");
  }

  @Test
  public void should_correct_register_swagger_in_location() {
    serviceRegistry.getSwaggerLoader().registerSwaggersInLocation("swagger-del");
    assertThat(serviceRegistry.getSwaggerLoader().loadFromMemory(appId, serviceName, "hello")).isNotNull();
  }
}
