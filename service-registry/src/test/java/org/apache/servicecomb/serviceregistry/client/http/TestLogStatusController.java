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

package org.apache.servicecomb.serviceregistry.client.http;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.servicecomb.serviceregistry.client.http.ServiceRegistryClientImpl.ClientConnectionStatus;
import org.apache.servicecomb.serviceregistry.client.http.ServiceRegistryClientImpl.LogStatusController;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestLogStatusController {

  private LogStatusController logStatusController;

  private MockedClock mockedClock;

  final long hour1 = 60 * 60 * 1000;

  final long minute15 = 15 * 60 * 1000;

  final long minute2 = 2 * 60 * 1000;

  @Before
  public void before() {
    logStatusController = new LogStatusController();
    mockedClock = new MockedClock();
    logStatusController.clock = mockedClock;
  }

  @Test
  public void test() {
    checkStatus(0, logStatusController, ClientConnectionStatus.NORMAL, 0, 0, 0);

    // the first 9 times of consecutive failures don't cause controller status shift
    // only the LogStatusController.consecutiveFailure is accumulated
    test_consecutive_failure_9_times(logStatusController, 0);

    // If the failure continues, the status is shifted between BAN_ERROR_LOG and ALLOW_ERROR_LOG,
    // and the period of BAN_ERROR_LOG is 15 min, while the period of ALLOW_ERROR_LOG is 2 min,
    // until the LogStatusController.errorStatusShiftCount reaches 5.
    long mockNowMillis = test_consecutive_failure_during_the_first_5_status_shift();

    // After the first 5 times error status shifting, the period of BAN_ERROR_LOG is extended to 1 hour,
    // and the period of ALLOW_ERROR_LOG is still 2 min.
    test_consecutive_failure_over_5_times_of_status_shift(mockNowMillis);
  }

  private void test_consecutive_failure_9_times(LogStatusController logStatusController, int round) {
    for (int i = 1; i < 10; i++) {
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.NORMAL, i, 0, 0);
    }
    checkStatus(round, logStatusController, ClientConnectionStatus.NORMAL, 9, 0, 0);
  }

  private long test_consecutive_failure_during_the_first_5_status_shift() {
    int round = 0;
    long mockNowMillis = 0;
    int errorStatusShiftCount = 1;
    while (round < 6) {
      ++round;
      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.BAN_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis + minute15);

      mockNowMillis += minute15;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.BAN_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis);

      ++mockNowMillis;
      errorStatusShiftCount++;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.ALLOW_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis + minute2);

      mockNowMillis += minute2;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.ALLOW_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis);

      ++mockNowMillis;
      errorStatusShiftCount++;

      if (errorStatusShiftCount >= 5) {
        break;
      }

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.BAN_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis + minute15);
    }
    return mockNowMillis;
  }

  private void test_consecutive_failure_over_5_times_of_status_shift(long mockNowMillis) {
    int errorStatusShiftCount = 5;
    int round = 1;
    while (mockNowMillis < 1_0000_0000_0000L) {
      ++round;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.BAN_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis + hour1);

      mockNowMillis += hour1;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.BAN_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis);

      mockNowMillis++;
      errorStatusShiftCount++;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.ALLOW_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis + minute2);

      mockNowMillis += minute2;

      mockedClock.millisMock(mockNowMillis);
      logStatusController.markFailure();
      checkStatus(round, logStatusController, ClientConnectionStatus.ALLOW_ERROR_LOG, 10, errorStatusShiftCount,
          mockNowMillis);

      ++mockNowMillis;
      ++errorStatusShiftCount;
    }
  }

  private void checkStatus(int round,
      LogStatusController logStatusController,
      ClientConnectionStatus status,
      int consecutiveFailure,
      int errorStatusShiftCount,
      long nextShiftTime) {
    Assert.assertEquals("round" + round, status, logStatusController.getStatus());
    Assert.assertEquals("round" + round, consecutiveFailure, logStatusController.getConsecutiveFailure());
    Assert.assertEquals("round" + round, errorStatusShiftCount, logStatusController.getErrorStatusShiftCount());
    Assert.assertEquals("round" + round, nextShiftTime, logStatusController.getNextShiftTime());
  }

  static class MockedClock extends Clock {
    private long millis = 0L;

    @Override
    public ZoneId getZone() {
      return null;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return null;
    }

    @Override
    public Instant instant() {
      return null;
    }

    @Override
    public long millis() {
      return millis;
    }

    public MockedClock millisMock(long millis) {
      this.millis = millis;
      return this;
    }
  }
}
