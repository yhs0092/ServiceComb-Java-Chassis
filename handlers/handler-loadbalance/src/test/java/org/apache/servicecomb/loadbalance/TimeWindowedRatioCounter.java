package org.apache.servicecomb.loadbalance;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.netflix.spectator.impl.AtomicDouble;

public class TimeWindowedRatioCounter {

  private final Object SLIP_LOCK = new Object();

  /**
   * 分子
   */
  double[] numeratorWindows;

  /**
   * 分母
   */
  int[] denominatorWindows;

  AtomicDouble numeratorCounter;

  AtomicInteger denominatorCounter;

  int currentWindowIndex;

  double totalNumerator;

  int totalDenominator;

  double currentRatio;

  /**
   * Debug purpose
   */
  Consumer<TimeWindowedRatioCounter> slipHook;

  public TimeWindowedRatioCounter(int windowsSize) {
    numeratorWindows = new double[windowsSize];
    denominatorWindows = new int[windowsSize];
    numeratorCounter = new AtomicDouble(0);
    denominatorCounter = new AtomicInteger(0);
    counters.add(this);
  }

  public void unregisterScheduleRefresher() {
    counters.remove(this);
  }

  public void count(double value) {
    numeratorCounter.addAndGet(value);
    denominatorCounter.incrementAndGet();
  }

  public void slipWindow() {
    synchronized (SLIP_LOCK) {
      double currentNumeratorValue = numeratorCounter.getAndSet(0);
      int currentDenominatorValue = denominatorCounter.getAndSet(0);

      totalNumerator -= numeratorWindows[currentWindowIndex];
      totalDenominator -= denominatorWindows[currentWindowIndex];

      numeratorWindows[currentWindowIndex] = currentNumeratorValue;
      denominatorWindows[currentWindowIndex] = currentDenominatorValue;

      totalNumerator += currentNumeratorValue;
      totalDenominator += currentDenominatorValue;

      currentWindowIndex = ++currentWindowIndex % denominatorWindows.length;

      if (0 != totalDenominator) {
        currentRatio = totalNumerator / totalDenominator;
      } else {
        currentRatio = Double.MAX_VALUE;
      }

      callBackHook();
    }
  }

  public double getCurrentRatio() {
    return currentRatio;
  }

  public int getTotalDenominator() {
    return totalDenominator;
  }

  private void callBackHook() {
    if (null != slipHook) {
      slipHook.accept(this);
    }
  }

  private static Set<TimeWindowedRatioCounter> counters = new HashSet<>();

  private static final ScheduledExecutorService scheduledRefresher = Executors
      .newScheduledThreadPool(1, r -> new Thread(r, "counterSlipper"));

  public static final int TIME_WINDOW_SLIP_PERIOD = 1000;

  static {
    scheduledRefresher.scheduleAtFixedRate(() -> {
      for (TimeWindowedRatioCounter counter : counters) {
        counter.slipWindow();
      }
    }, TIME_WINDOW_SLIP_PERIOD, TIME_WINDOW_SLIP_PERIOD, TimeUnit.MILLISECONDS);
  }
}
