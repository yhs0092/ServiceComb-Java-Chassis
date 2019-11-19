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

package org.apache.servicecomb.provider.pojo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.servicecomb.core.provider.producer.AbstractProducerProvider;
import org.apache.servicecomb.core.provider.producer.ProducerMeta;
import org.apache.servicecomb.foundation.common.utils.BeanUtils;
import org.apache.servicecomb.provider.pojo.instance.PojoInstanceFactory;
import org.apache.servicecomb.provider.pojo.instance.SpringInstanceFactory;
import org.apache.servicecomb.provider.pojo.schema.PojoProducerMeta;
import org.apache.servicecomb.provider.pojo.schema.PojoProducers;

public class PojoProducerProvider extends AbstractProducerProvider {
  private Map<String, InstanceFactory> instanceFactoryMgr = new HashMap<>();

  private void registerInstanceFactory(InstanceFactory instanceFactory) {
    instanceFactoryMgr.put(instanceFactory.getImplName(), instanceFactory);
  }

  public PojoProducerProvider() {
    registerInstanceFactory(new PojoInstanceFactory());
    registerInstanceFactory(new SpringInstanceFactory());
  }

  @Override
  public List<ProducerMeta> init() {
    // for some test cases, there is no spring context
    if (BeanUtils.getContext() == null) {
      return Collections.emptyList();
    }

    PojoProducers pojoProducers = BeanUtils.getContext().getBean(PojoProducers.class);
    for (ProducerMeta producerMeta : pojoProducers.getProducerMetas()) {
      PojoProducerMeta pojoProducerMeta = (PojoProducerMeta) producerMeta;
      initPojoProducerMeta(pojoProducerMeta);

//      try {
//        producerSchemaFactory.getOrCreateProducerSchema(
//            pojoProducerMeta.getSchemaId(),
//            pojoProducerMeta.getInstanceClass(),
//            pojoProducerMeta.getInstance());
//      } catch (Throwable e) {
//        throw new IllegalArgumentException(
//            "create producer schema failed, class=" + pojoProducerMeta.getInstanceClass().getName(), e);
//      }
    }

    return pojoProducers.getProducerMetas();
  }

  @Override
  public String getName() {
    return PojoConst.POJO;
  }

  private void initPojoProducerMeta(PojoProducerMeta pojoProducerMeta) {
    if (pojoProducerMeta.getInstance() != null) {
      return;
    }

    String[] nameAndValue = parseImplementation(pojoProducerMeta.getImplementation());

    InstanceFactory factory = instanceFactoryMgr.get(nameAndValue[0]);
    if (factory == null) {
      throw new IllegalStateException("failed to find instance factory, name=" + nameAndValue[0]);
    }

    Object instance = factory.create(nameAndValue[1]);
    pojoProducerMeta.setInstance(instance);
  }

  private String[] parseImplementation(String implementation) {
    String implName = PojoConst.POJO;
    String implValue = implementation;
    int idx = implementation.indexOf(':');
    if (idx != -1) {
      implName = implementation.substring(0, idx);
      implValue = implementation.substring(idx + 1);
    }

    return new String[] {implName, implValue};
  }
}
