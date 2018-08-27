package org.apache.servicecomb.loadbalance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Test;

import com.google.common.cache.LoadingCache;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.loadbalancer.WeightedResponseTimeRule;

import mockit.Deencapsulation;
import mockit.Mock;
import mockit.MockUp;

public class WeightedResponseTimeRuleBehaviourMultiIteration {

  public static final String TEST_SERVICE_NAME = "testServiceName";

  public static final String IP_PREFIX = "192.168.0.";

  public static final int PORT = 1000;

  public static final int REFRESH_LOOP_PERIOD = 10;

  /**
   * 调用方线程相对于服务方线程的倍数
   */
  public static final int CONSUMER_TO_PROVIDER_TIMES = 10;

  private HashMap<String, ServerStats> serverStatsMap = new HashMap<>();

  private ArrayList<Server> servers = new ArrayList<>();

  private WeightedResponseTimeRule weightedResponseTimeRule = new WeightedResponseTimeRule();

  public Map<String, MockProvider> prepareTest(List<Long> responseAvgMsDistribute) {
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

    Map<String, MockProvider> providerMap = new HashMap<>(responseAvgMsDistribute.size());
    for (int i = 0; i < responseAvgMsDistribute.size(); ++i) {
      Long responseDelay = responseAvgMsDistribute.get(i);

      Server server = new Server(IP_PREFIX + (i + 1), PORT);
      server.setAlive(true);
      servers.add(server);

      ServerStats serverStats = new ServerStats();
//       init avg response time
//      serverStats.noteResponseTime(responseDelay);
      serverStatsMap.put(server.getHost(), serverStats);

      providerMap.put(server.getHost(), new MockProvider(server.getHost(), responseDelay, serverStats));
    }

    // setLoadBalancer will trigger weight calculation
    weightedResponseTimeRule.setLoadBalancer(loadBalancer);

    return providerMap;
  }

  /**
   * two instances, delay is 1:2
   */
  @Test
  public void test() throws InterruptedException {
    Map<String, MockProvider> providerMap = prepareTest(Arrays.asList(2L, 4L));

    ArrayList<Callable<Void>> tasks = constructInvokeTasks(providerMap);

    startPeriodicPrint(providerMap);

    startInvokeTasks(tasks);
  }

  private void startPeriodicPrint(Map<String, MockProvider> providerMap) {
    List<MockProvider> sortedProviders = providerMap.values().stream()
        .sorted(Comparator.comparingLong(MockProvider::getInitDelay)).collect(Collectors.toList());

    ScheduledExecutorService scheduledPrinterTask = Executors.newScheduledThreadPool(1, r -> {
      Thread periodicPrinter = new Thread(r, "periodicPrinter");
      periodicPrinter.setDaemon(true);
      return periodicPrinter;
    });
    scheduledPrinterTask.scheduleAtFixedRate(() -> {
      System.out.println("=========================" + new Date());
      for (MockProvider provider : sortedProviders) {
        System.out.println(
            provider.host + ": delay=[" + provider.initDelay + "], invokes=[" + provider.avgDelay.getTotalDenominator()
                + "], avgDelay=[" + provider.avgDelay.getCurrentRatio() + "]");
      }
    }, 0, REFRESH_LOOP_PERIOD, TimeUnit.SECONDS);
  }

  private void startInvokeTasks(ArrayList<Callable<Void>> tasks)
      throws InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(tasks.size(), r -> {
      Thread thread = new Thread(r, "providerInvoker");
      thread.setDaemon(true);
      return thread;
    });
    executorService.invokeAll(tasks);
  }

  private ArrayList<Callable<Void>> constructInvokeTasks(Map<String, MockProvider> providerMap) {
    ArrayList<Callable<Void>> tasks = new ArrayList<>();
    for (int i = 0;
        i < providerMap.size() * MockProvider.MOCK_PROVIDER_THREAD_POOL_SIZE * CONSUMER_TO_PROVIDER_TIMES;
        ++i) {
      tasks.add(() -> {
        while (true) {
          Server chosenServer = weightedResponseTimeRule.choose(null);
          providerMap.get(chosenServer.getHost()).invoke();
        }
      });
    }
    return tasks;
  }

  public static class MockProvider {

    public static final int MOCK_PROVIDER_THREAD_POOL_SIZE = 4;

    private static ExecutorService executor = Executors
        .newFixedThreadPool(MOCK_PROVIDER_THREAD_POOL_SIZE, r -> new Thread(r, "mockProvider"));

    private final long initDelay;

    private ServerStats serverStats;

    private String host;

    private TimeWindowedRatioCounter avgDelay = new TimeWindowedRatioCounter(REFRESH_LOOP_PERIOD);

    public MockProvider(String host, long initDelay, ServerStats serverStats) {
      this.host = host;
      this.initDelay = initDelay;
      this.serverStats = serverStats;
    }

    public void invoke() {
      long start = System.currentTimeMillis();
      Future<?> future = executor.submit(() -> {
        try {
          Thread.sleep(initDelay);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });

      try {
        future.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      } finally {
        long timeCost = System.currentTimeMillis() - start;
        avgDelay.count(timeCost);
        // This method seems not thread safe, but I haven't find netflix use it in synchronized code block
        serverStats.noteResponseTime(timeCost);
      }
    }

    public long getInitDelay() {
      return initDelay;
    }
  }
}
