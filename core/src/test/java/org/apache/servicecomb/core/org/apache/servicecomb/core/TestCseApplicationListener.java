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
package org.apache.servicecomb.core.org.apache.servicecomb.core;

import org.apache.servicecomb.core.CseApplicationListener;
import org.apache.servicecomb.core.SCBEngine;
import org.apache.servicecomb.core.SCBStatus;
import org.apache.servicecomb.core.bootstrap.SCBBootstrap;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.event.ContextClosedEvent;

import mockit.Mocked;

public class TestCseApplicationListener {
  @Test
  public void onApplicationEvent_close(@Mocked ContextClosedEvent contextClosedEvent) {
    SCBEngine scbEngine = new SCBBootstrap().useLocalRegistry().createSCBEngineForTest();
    scbEngine.setStatus(SCBStatus.UP);

    CseApplicationListener listener = new CseApplicationListener();
    listener.onApplicationEvent(contextClosedEvent);

    Assert.assertEquals(SCBStatus.DOWN, scbEngine.getStatus());

    scbEngine.destroy();
  }
}
