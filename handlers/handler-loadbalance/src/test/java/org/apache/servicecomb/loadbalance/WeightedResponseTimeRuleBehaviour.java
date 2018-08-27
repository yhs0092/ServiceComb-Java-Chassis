package org.apache.servicecomb.loadbalance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import com.google.common.cache.LoadingCache;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.loadbalancer.WeightedResponseTimeRule;

import mockit.Deencapsulation;
import mockit.Mock;
import mockit.MockUp;

public class WeightedResponseTimeRuleBehaviour {

  public static final String TEST_SERVICE_NAME = "testServiceName";

  public static final String IP_PREFIX = "192.168.0.";

  public static final int PORT = 1000;

  private HashMap<String, ServerStats> serverStatsMap = new HashMap<>();

  private ArrayList<Server> servers = new ArrayList<>();

  private WeightedResponseTimeRule weightedResponseTimeRule = new WeightedResponseTimeRule();

  public Map<String, Counter> prepareTest(List<Double> responseAvgMsDistribute) {
    LoadBalancerStats loadBalancerStats = new LoadBalancerStats(TEST_SERVICE_NAME);
    LoadingCache<Server, ServerStats> serverStatsCache = new MockUp<LoadingCache<Server, ServerStats>>() {
      @Mock
      ServerStats get(Server server) {
        return serverStatsMap.get(server.getHost());
      }
    }.getMockInstance();
    Deencapsulation.setField(loadBalancerStats, "serverStatsCache", serverStatsCache);
    LoadBalancer loadBalancer = new LoadBalancer(null, TEST_SERVICE_NAME, loadBalancerStats);
    Deencapsulation.setField(loadBalancer, "serverList", servers);

    Map<String, Counter> counterMap = new HashMap<>(responseAvgMsDistribute.size());
    for (int i = 0; i < responseAvgMsDistribute.size(); ++i) {
      Double responseDelay = responseAvgMsDistribute.get(i);

      Server server = new Server(IP_PREFIX + (i + 1), PORT);
      server.setAlive(true);
      servers.add(server);

      ServerStats serverStats = new ServerStats();
      // set avg response time
      serverStats.noteResponseTime(responseDelay);
      serverStatsMap.put(server.getHost(), serverStats);

      counterMap.put(server.getHost(), new Counter(server.getHost(), responseDelay));
    }

    // setLoadBalancer will trigger weight calculation
    weightedResponseTimeRule.setLoadBalancer(loadBalancer);

    return counterMap;
  }

  /**
   * two instances, delay is 1:2
   */
  @Test
  public void test() {
    Map<String, Counter> counterMap = prepareTest(Arrays.asList(10D, 20D));

    for (int i = 0; i < 300_0000; ++i) {
      Server chosenServer = weightedResponseTimeRule.choose(null);
      counterMap.get(chosenServer.getHost()).count();
    }

    printResult(counterMap);
  }

  /**
   * two instances, delay is 3:7
   */
  @Test
  public void test2() {
    Map<String, Counter> counterMap = prepareTest(Arrays.asList(30D, 70D));

    for (int i = 0; i < 1000_0000; ++i) {
      Server chosenServer = weightedResponseTimeRule.choose(null);
      counterMap.get(chosenServer.getHost()).count();
    }

    printResult(counterMap);
  }

  /**
   * twenty instances, delays is 1:1:1.....:2:2:2....
   */
  @Test
  public void testMoreInstances() {
    ArrayList<Double> responseAvgMsDistribute = new ArrayList<>();
    for (int i = 0; i < 10; ++i) {
      responseAvgMsDistribute.add(10D);
    }
    for (int i = 0; i < 10; ++i) {
      responseAvgMsDistribute.add(20D);
    }
    Map<String, Counter> counterMap = prepareTest(responseAvgMsDistribute);

    for (int i = 0; i < 2_0000_0000; ++i) {
      Server chosenServer = weightedResponseTimeRule.choose(null);
      counterMap.get(chosenServer.getHost()).count();
    }

    printResult(counterMap);
  }

  private void printResult(Map<String, Counter> counterMap) {
    counterMap.entrySet().stream().map(Entry::getValue)
        .sorted(Comparator.comparingDouble(Counter::getResponseDelay))
        .forEachOrdered(System.out::println);
  }

  public static class Counter {
    private double responseDelay;

    private String name;

    private long count;

    public Counter(String name, double responseDelay) {
      this.name = name;
      this.responseDelay = responseDelay;
    }

    public void count() {
      ++count;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public long getCount() {
      return count;
    }

    public void setCount(long count) {
      this.count = count;
    }

    public double getResponseDelay() {
      return responseDelay;
    }

    public void setResponseDelay(double responseDelay) {
      this.responseDelay = responseDelay;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Counter{");
      sb.append("responseDelay=").append(responseDelay);
      sb.append(", name='").append(name).append('\'');
      sb.append(", count=").append(count);
      sb.append('}');
      return sb.toString();
    }
  }
}
