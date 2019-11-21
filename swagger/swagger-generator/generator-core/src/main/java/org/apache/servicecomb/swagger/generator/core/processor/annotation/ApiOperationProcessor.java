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

package org.apache.servicecomb.swagger.generator.core.processor.annotation;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.servicecomb.swagger.generator.MethodAnnotationProcessor;
import org.apache.servicecomb.swagger.generator.OperationGenerator;
import org.apache.servicecomb.swagger.generator.SwaggerGenerator;
import org.springframework.util.StringUtils;

import io.swagger.annotations.ApiOperation;
import io.swagger.models.Operation;
import io.swagger.models.Scheme;
import io.swagger.util.BaseReaderUtils;

public class ApiOperationProcessor implements MethodAnnotationProcessor<ApiOperation> {
  private static final String SEPARATOR = ",";

  public Type getProcessType() {
    return ApiOperation.class;
  }

  @Override
  public void process(SwaggerGenerator swaggerGenerator,
      OperationGenerator operationGenerator, ApiOperation apiOperationAnnotation) {
    Operation operation = operationGenerator.getOperation();

    operationGenerator.setHttpMethod(apiOperationAnnotation.httpMethod());

    if (!StringUtils.isEmpty(apiOperationAnnotation.value())) {
      operation.setSummary(apiOperationAnnotation.value());
    }

    if (!StringUtils.isEmpty(apiOperationAnnotation.notes())) {
      operation.setDescription(apiOperationAnnotation.notes());
    }

    operation.setOperationId(apiOperationAnnotation.nickname());
    operation.getVendorExtensions().putAll(BaseReaderUtils.parseExtensions(apiOperationAnnotation.extensions()));

    convertTags(apiOperationAnnotation.tags(), operation);
    convertProduces(apiOperationAnnotation.produces(), operation);
    convertConsumes(apiOperationAnnotation.consumes(), operation);
    convertProtocols(apiOperationAnnotation.protocols(), operation);
    AnnotationUtils.addResponse(swaggerGenerator.getSwagger(),
        operation,
        apiOperationAnnotation);

    // responseReference未解析
    // hidden未解析
    // authorizations未解析
  }

  // protocols以分号为创建，比如：http, https, ws, wss
  private void convertProtocols(String protocols, Operation operation) {
    if (protocols == null) {
      return;
    }

    for (String protocol : protocols.split(SEPARATOR)) {
      if (StringUtils.isEmpty(protocol)) {
        continue;
      }

      operation.addScheme(Scheme.forValue(protocol));
    }
  }

  // consumes以分号为创建，比如：application/json, application/xml
  private void convertConsumes(String consumes, Operation operation) {
    if (StringUtils.isEmpty(consumes)) {
      return;
    }

    List<String> consumeList = Arrays.stream(consumes.split(SEPARATOR)).filter(s -> !StringUtils.isEmpty(s))
        .collect(Collectors.toList());
    if (!consumeList.isEmpty()) {
      operation.setConsumes(consumeList);
    }
  }

  // produces以分号为创建，比如：application/json, application/xml
  private void convertProduces(String produces, Operation operation) {
    if (StringUtils.isEmpty(produces)) {
      return;
    }

    List<String> produceList = Arrays.stream(produces.split(SEPARATOR)).filter(s -> !StringUtils.isEmpty(s))
        .collect(Collectors.toList());
    if (!produceList.isEmpty()) {
      operation.setProduces(produceList);
    }
  }

  private void convertTags(String[] tags, Operation operation) {
    if (tags == null || tags.length == 0) {
      return;
    }

    for (String tag : tags) {
      if (StringUtils.isEmpty(tag)) {
        continue;
      }

      operation.addTag(tag);
    }
  }
}
