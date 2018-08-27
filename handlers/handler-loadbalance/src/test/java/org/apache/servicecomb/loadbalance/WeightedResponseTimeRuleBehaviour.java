package org.apache.servicecomb.loadbalance;

import java.util.ArrayList;

import org.junit.Test;

import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.WeightedResponseTimeRule;

import mockit.Deencapsulation;

public class WeightedResponseTimeRuleBehaviour {

  public static final String TEST_SERVICE_NAME = "testServiceName";

  @Test
  public void test() {
    WeightedResponseTimeRule weightedResponseTimeRule = new WeightedResponseTimeRule();
    LoadBalancerStats loadBalancerStats = new LoadBalancerStats(TEST_SERVICE_NAME);
    Deencapsulation.setField(loadBalancerStats,"serverStatsCache",);
    LoadBalancer loadBalancer = new LoadBalancer(null, TEST_SERVICE_NAME, loadBalancerStats);
    weightedResponseTimeRule.setLoadBalancer(loadBalancer);

    ArrayList<Server> servers = new ArrayList<>();
  }
}
