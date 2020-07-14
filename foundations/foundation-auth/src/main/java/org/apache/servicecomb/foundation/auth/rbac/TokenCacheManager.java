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

package org.apache.servicecomb.foundation.auth.rbac;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.apache.servicecomb.foundation.common.concurrency.SuppressedRunnableWrapper;
import org.apache.servicecomb.foundation.common.concurrent.ConcurrentHashMapEx;
import org.apache.servicecomb.foundation.common.utils.TimeUtils;
import org.apache.servicecomb.serviceregistry.RegistryUtils;
import org.apache.servicecomb.serviceregistry.ServiceRegistry;
import org.apache.servicecomb.serviceregistry.api.request.RbacTokenRequest;
import org.apache.servicecomb.serviceregistry.api.response.RbacTokenResponse;
import org.apache.servicecomb.serviceregistry.client.ServiceRegistryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TokenCacheManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(TokenCacheManager.class);

  private static final TokenCacheManager INSTANCE = new TokenCacheManager();

  private Clock clock = TimeUtils.getSystemDefaultZoneClock();

  private ScheduledExecutorService tokenCacheWorker;

  private Map<String, TokenCache> tokenCacheMap;

  public static TokenCacheManager getInstance() {
    return INSTANCE;
  }

  private TokenCacheManager() {
    tokenCacheWorker = Executors.newScheduledThreadPool(2, new ThreadFactory() {
      private final AtomicInteger threadIndexer = new AtomicInteger();

      @Override
      public Thread newThread(@Nonnull Runnable r) {
        Thread thread = new Thread(r, "auth-token-cache-" + threadIndexer.getAndIncrement());
        thread.setDaemon(true);
        return thread;
      }
    });
    tokenCacheMap = new ConcurrentHashMapEx<>();
  }

  public void addTokenCache(String registryName, String accountName, String password) {
    Objects.requireNonNull(registryName, "registryName should not be null!");
    if (tokenCacheMap.containsKey(registryName)) {
      throw new IllegalArgumentException(
          "duplicate token cache registration for serviceRegistry[" + registryName + "]");
    }

    TokenCache tokenCache = new TokenCache(registryName, accountName, password, this.clock);
    tokenCache.setTokenCacheWorker(this.tokenCacheWorker);
    tokenCacheMap.put(registryName, tokenCache);
  }

  public String getToken(String registryName) {
    return Optional.ofNullable(tokenCacheMap.get(registryName))
        .map(TokenCache::getToken)
        .orElse("");
  }

  public static class TokenCache {
    private final String registryName;

    private final String accountName;

    private final String password;

    private final Clock clock;

    private String token;

    private long nextRefreshTime;

    private boolean wrongPassword;

    /**
     * The life cycle period of a token, in millisecond.
     * After the {@code tokenLife} time since the token created, it should be refreshed.
     * <p>
     * Default life time in sc is 30min, give 2min buffer
     * </p>
     */
    private long tokenLife = TimeUnit.MINUTES.toMillis(30 - 2);

    private ScheduledExecutorService tokenCacheWorker;

    public TokenCache(String registryName, String accountName, String password, Clock clock) {
      this.registryName = registryName;
      this.accountName = accountName;
      this.password = password;
      this.clock = clock;
    }

    public String getToken() {
      return token == null ? "" : token;
    }

    public void setTokenCacheWorker(ScheduledExecutorService tokenCacheWorker) {
      Objects.requireNonNull(tokenCacheWorker, "input tokenCacheWorker is null");
      if (this.tokenCacheWorker != null) {
        throw new IllegalStateException("tokenCacheWorker already set!");
      }

      this.tokenCacheWorker = tokenCacheWorker;
      startTokenRefreshTask();
    }

    private void startTokenRefreshTask() {
      this.tokenCacheWorker.scheduleAtFixedRate(
          new SuppressedRunnableWrapper(() -> {
            if (isTokenOutdated()) {
              refreshToken();
            }
          }),
          1,
          5,
          TimeUnit.SECONDS);
    }

    private boolean isTokenOutdated() {
      return clock.millis() > nextRefreshTime;
    }

    private void refreshToken() {
      if (wrongPassword) {
        return;
      }
      ServiceRegistry serviceRegistry = RegistryUtils.getServiceRegistry(registryName);
      ServiceRegistryClient serviceRegistryClient =
          serviceRegistry == null ? null : serviceRegistry.getServiceRegistryClient();
      if ((serviceRegistry == null || serviceRegistryClient == null)
          && ServiceRegistry.DEFAULT_REGISTRY_NAME.equals(registryName)) {
        LOGGER.error("failed to get default serviceRegistry");
        tokenCacheWorker.schedule( // retry after 1 second
            this::refreshToken, 1, TimeUnit.SECONDS);
        return;
      }
      RbacTokenRequest request = new RbacTokenRequest();
      request.setAccountName(accountName);
      request.setPassword(password);
      RbacTokenResponse rbacTokenResponse = serviceRegistryClient.getRbacToken(request);
      LOGGER.info("refresh token successfully {}", rbacTokenResponse.getStatusCode());
      if (StringUtils.isEmpty(this.token) && Status.UNAUTHORIZED.getStatusCode() == rbacTokenResponse.getStatusCode()) {
        // password wrong, do not try anymore
        wrongPassword = true;
      }
      this.token = rbacTokenResponse.getToken();
      this.nextRefreshTime = clock.millis() + tokenLife;
    }
  }
}
