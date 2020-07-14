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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.servicecomb.foundation.auth.AuthHeaderProvider;
import org.apache.servicecomb.serviceregistry.ServiceRegistry;

import com.netflix.config.DynamicPropertyFactory;

public class TokenAuthHeaderProvider implements AuthHeaderProvider {
  public static final String ACCOUNT_NAME_KEY = "servicecomb.credentials.accountName";

  public static final String PASSWORD_KEY = "servicecomb.credentials.password";

  public static final String CIPHER_KEY = "servicecomb.credentials.cipher";

  private String registryName;

  private String accountName;

  private String password;

  public TokenAuthHeaderProvider() {
    this.registryName = ServiceRegistry.DEFAULT_REGISTRY_NAME;
    this.accountName = DynamicPropertyFactory.getInstance().getStringProperty(ACCOUNT_NAME_KEY, null).get();
    this.password = DynamicPropertyFactory.getInstance().getStringProperty(PASSWORD_KEY, null).get();
    if (StringUtils.isNotEmpty(this.accountName)) {
      TokenCacheManager.getInstance().addTokenCache(registryName, accountName, password);
    }
  }

  public TokenAuthHeaderProvider(String registryName, String accountName, String password) {
    this.registryName = registryName;
    this.accountName = accountName;
    this.password = password;
    TokenCacheManager.getInstance().addTokenCache(this.registryName, this.accountName, this.password);
  }

  @Override
  public Map<String, String> authHeaders() {
    String token = TokenCacheManager.getInstance().getToken(registryName);
    if (StringUtils.isEmpty(token)) {
      return new HashMap<>();
    }

    HashMap<String, String> header = new HashMap<>();
    header.put("Authorization", "Bearer " + token);
    return Collections.unmodifiableMap(header);
  }
}
