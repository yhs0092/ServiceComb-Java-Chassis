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
package org.apache.servicecomb.it.testcase;

import java.util.concurrent.ExecutionException;

import org.apache.servicecomb.it.Consumers;
import org.apache.servicecomb.it.schema.ReactiveHelloIntf;
import org.junit.Assert;
import org.junit.Test;

public class TestReactive {
  static Consumers<ReactiveHelloIntf> consumers = new Consumers<>("reactiveWithIntf", ReactiveHelloIntf.class);

  @Test
  public void reactiveWithIntf() throws ExecutionException, InterruptedException {
    Assert.assertEquals("hello name", consumers.getIntf().hello("name").get());
  }

  @Test
  public void reactiveWithIntf_rt() {
    Assert.assertEquals("hello name",
        consumers.getSCBRestTemplate().postForObject("/hello", "name", String.class));
  }
}
