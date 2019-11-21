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

package org.apache.servicecomb.swagger.generator.core;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.servicecomb.foundation.test.scaffolding.config.ArchaiusUtils;
import org.apache.servicecomb.swagger.extend.parameter.HttpRequestParameter;
import org.apache.servicecomb.swagger.generator.pojo.PojoSwaggerGeneratorContext;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.Extension;
import io.swagger.annotations.ExtensionProperty;
import io.swagger.annotations.ResponseHeader;
import io.swagger.models.Response;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.QueryParameter;

public class TestOperationGenerator {
  @BeforeClass
  public static void setup() {
    ArchaiusUtils.resetConfig();
  }

  @AfterClass
  public static void teardown() {
    ArchaiusUtils.resetConfig();
  }

  @Test
  public void testPathPlaceHolder() {
    ArchaiusUtils.setProperty("var", "varValue");

    PojoSwaggerGeneratorContext context = new PojoSwaggerGeneratorContext();

    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(context, null);
    OperationGenerator operationGenerator = new OperationGenerator(swaggerGenerator, null);
    operationGenerator.setPath("/a/${var}/b");

    assertEquals("/a/varValue/b", operationGenerator.getPath());
  }

  @Test
  public void testConvertTags() throws NoSuchMethodException {
    Method function = TestClass.class.getMethod("function");
    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(new PojoSwaggerGeneratorContext(), TestClass.class);
    OperationGenerator operationGenerator = new OperationGenerator(swaggerGenerator, function);

    operationGenerator.generate();

    List<String> tagList = operationGenerator.getOperation().getTags();
    assertThat(tagList, contains("tag1", "tag2"));
  }

  @Test
  public void testConvertTagsOnMethodWithNoTag() throws NoSuchMethodException {
    Method function = TestClass.class.getMethod("functionWithNoTag");
    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(new PojoSwaggerGeneratorContext(), TestClass.class);
    OperationGenerator operationGenerator = new OperationGenerator(swaggerGenerator, function);
    swaggerGenerator.addDefaultTag("default0");
    swaggerGenerator.addDefaultTag("default1");

    operationGenerator.generate();

    List<String> tagList = operationGenerator.getOperation().getTags();
    assertThat(tagList, contains("default0", "default1"));
  }

  @Test
  public void testConvertTagsOnMethodWithNoAnnotation() throws NoSuchMethodException {
    Method function = TestClass.class.getMethod("functionWithNoAnnotation");
    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(new PojoSwaggerGeneratorContext(), TestClass.class);
    OperationGenerator operationGenerator = new OperationGenerator(swaggerGenerator, function);
    swaggerGenerator.addDefaultTag("default0");
    swaggerGenerator.addDefaultTag("default1");

    operationGenerator.generate();

    List<String> tagList = operationGenerator.getOperation().getTags();
    assertThat(tagList, contains("default0", "default1"));
  }

  @Test
  public void addProviderParameter() throws NoSuchMethodException {
    Method function = TestClass.class.getMethod("functionWithNoAnnotation");
    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(new PojoSwaggerGeneratorContext(), TestClass.class);
    OperationGenerator operationGenerator = new OperationGenerator(swaggerGenerator, function);

    Parameter parameter = new BodyParameter();
    parameter.setName("param0");
    operationGenerator.addProviderParameter(parameter);
    Assert.assertEquals(1, operationGenerator.getProviderParameters().size());
    Assert.assertSame(parameter, operationGenerator.getProviderParameters().get(0));

    parameter = new HttpRequestParameter();
    operationGenerator.addProviderParameter(parameter);
    Assert.assertSame(parameter, operationGenerator.getProviderParameters().get(1));

    parameter = new QueryParameter();
    parameter.setName("param1");
    operationGenerator.addProviderParameter(parameter);
    Assert.assertSame(parameter, operationGenerator.getProviderParameters().get(2));

    parameter = new QueryParameter();
    parameter.setName("param0");
    operationGenerator.addProviderParameter(parameter);
    Assert.assertEquals(3, operationGenerator.getProviderParameters().size());
    Assert.assertNotSame(parameter, operationGenerator.getProviderParameters().get(2));
  }

  @Test
  public void testApiOperationAndResponse() throws NoSuchMethodException {
    Method function = TestClass.class.getMethod("function");
    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(new PojoSwaggerGeneratorContext(), TestClass.class);
    OperationGenerator operationGenerator = new OperationGenerator(swaggerGenerator, function);
    operationGenerator.generate();
    Response response = operationGenerator.getOperation().getResponses().get("200");
    Assert.assertEquals("200 is ok............", response.getDescription());
    Assert.assertNotNull(response.getHeaders().get("x-user-domain"));
    Assert.assertNull(response.getHeaders().get("x-user-name"));
    Assert.assertNotNull(operationGenerator.getOperation().getVendorExtensions().get("x-class-name"));

    Method function1 = TestClass.class.getMethod("function1");
    SwaggerGenerator swaggerGenerator1 = new SwaggerGenerator(new PojoSwaggerGeneratorContext(), TestClass.class);
    OperationGenerator operationGenerator1 = new OperationGenerator(swaggerGenerator1, function1);
    operationGenerator1.generate();
    Response response1 = operationGenerator1.getOperation().getResponses().get("200");
    Assert.assertEquals("200 is ok............", response1.getDescription());
    Assert.assertNull(response1.getHeaders().get("x-user-domain"));
    Assert.assertNotNull(response1.getHeaders().get("x-user-name"));
    Assert.assertNotNull(operationGenerator1.getOperation().getVendorExtensions().get("x-class-name"));
  }

  private static class TestClass {

    @ApiResponse(code = 200, message = "200 is ok............", response = String.class,
        responseHeaders = @ResponseHeader(name = "x-user-domain", response = String.class))
    @ApiOperation(value = "value1", tags = {"tag1", "tag2"},
        responseHeaders = {@ResponseHeader(name = "x-user-name", response = String.class),
            @ResponseHeader(name = "x-user-id", response = String.class)},
        extensions = {
            @Extension(name = "x-class-name", properties = {@ExtensionProperty(value = "value", name = "key")})})
    public void function() {
    }

    @ApiOperation(value = "value1", tags = {"tag1", "tag2"},
        responseHeaders = {@ResponseHeader(name = "x-user-name", response = String.class),
            @ResponseHeader(name = "x-user-id", response = String.class)},
        extensions = {
            @Extension(name = "x-class-name", properties = {@ExtensionProperty(value = "value", name = "key")})})
    @ApiResponse(code = 200, message = "200 is ok............", response = String.class,
        responseHeaders = @ResponseHeader(name = "x-user-domain", response = String.class))
    public void function1() {
    }

    @ApiOperation(value = "value2")
    public void functionWithNoTag() {
    }

    @SuppressWarnings("unused")
    public void functionWithNoAnnotation() {
    }
  }
}
