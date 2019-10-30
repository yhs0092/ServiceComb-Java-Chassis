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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.servicecomb.foundation.auth.AuthHeaderProvider;
import org.apache.servicecomb.foundation.auth.SignRequest;
import org.apache.servicecomb.foundation.common.net.IpPort;
import org.apache.servicecomb.foundation.common.utils.SPIServiceUtils;
import org.apache.servicecomb.foundation.vertx.client.http.HttpClientWithContext;
import org.apache.servicecomb.serviceregistry.config.ServiceRegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;

/**
 * The registry data access class.
 * <p/>
 * TODO: should be refactored.
 * This is not a good design. When we use this class, our actual intention is to get access to the registry data.
 * Therefore, this class is stateful, and it's not a typical "util".
 * This class should be refactored later and used as a registry data related context class.
 */
public final class RestUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(RestUtils.class);

  private static final String HEADER_CONTENT_TYPE = "Content-Type";

  private static final String HEADER_USER_AGENT = "User-Agent";

  private static final String HEADER_TENANT_NAME = "x-domain-name";

  private static final List<AuthHeaderProvider> AUTH_HEADER_PROVIDERS =
      SPIServiceUtils.getAllService(AuthHeaderProvider.class);

  private static final RestUtils INSTANCE = new RestUtils();

  private HttpClientPool httpClientPool;

  private List<AuthHeaderProvider> authHeaderProviders;

  private RestUtils() {
    this(HttpClientPool.INSTANCE, AUTH_HEADER_PROVIDERS);
  }

  public RestUtils(HttpClientPool httpClientPool) {
    this(httpClientPool, AUTH_HEADER_PROVIDERS);
  }

  public RestUtils(HttpClientPool httpClientPool, List<AuthHeaderProvider> authHeaderProviders) {
    this.httpClientPool = httpClientPool;
    this.authHeaderProviders = authHeaderProviders;
  }

  public static RestUtils getInstance() {
    return INSTANCE;
  }

  public void httpDo(RequestContext requestContext, Handler<RestResponse> responseHandler) {
    if (requestContext.getParams().getTimeout() != 0) {
      httpDo(requestContext.getParams().getTimeout(), requestContext, responseHandler);
      return;
    }
    httpDo(ServiceRegistryConfig.INSTANCE.getRequestTimeout(), requestContext, responseHandler);
  }

  public void httpDo(long timeout, RequestContext requestContext, Handler<RestResponse> responseHandler) {
    HttpClientWithContext vertxHttpClient = httpClientPool.getClient();
    vertxHttpClient.runOnContext(httpClient -> {
      IpPort ipPort = requestContext.getIpPort();
      HttpMethod httpMethod = requestContext.getMethod();
      RequestParam requestParam = requestContext.getParams();

      if (ipPort == null) {
        LOGGER.error("request address is null");
        responseHandler.handle(new RestResponse(requestContext, null));
        return;
      }

      // query params
      StringBuilder url = new StringBuilder(requestContext.getUri());
      String queryParams = requestParam.getQueryParams();
      if (!queryParams.isEmpty()) {
        url.append(url.lastIndexOf("?") > 0 ? "&" : "?")
            .append(queryParams);
      }

      @SuppressWarnings("deprecation")
      HttpClientRequest httpClientRequest = httpClient
          .request(httpMethod, ipPort.getPort(), ipPort.getHostOrIp(), url.toString(), response -> {
            responseHandler.handle(new RestResponse(requestContext, response));
          });

      httpClientRequest.setTimeout(timeout)
          .exceptionHandler(e -> {
            LOGGER.error("{} {} fail, endpoint is {}:{}, message: {}",
                httpMethod,
                url.toString(),
                ipPort.getHostOrIp(),
                ipPort.getPort(),
                e.getMessage());
            if (e instanceof UnknownHostException) {
              // help analyses DNS problem
              LOGGER.error("DNS resolve failed!", e);
            }
            responseHandler.handle(new RestResponse(requestContext, null));
          });

      //headers
      Map<String, String> headers = defaultHeaders();
      httpClientRequest.headers().addAll(headers);

      if (requestParam.getHeaders() != null && requestParam.getHeaders().size() > 0) {
        headers.putAll(requestParam.getHeaders());
        for (Map.Entry<String, String> header : requestParam.getHeaders().entrySet()) {
          httpClientRequest.putHeader(header.getKey(), header.getValue());
        }
      }

      // cookies header
      if (requestParam.getCookies() != null && requestParam.getCookies().size() > 0) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> cookie : requestParam.getCookies().entrySet()) {
          stringBuilder.append(cookie.getKey())
              .append("=")
              .append(cookie.getValue())
              .append("; ");
        }
        httpClientRequest.putHeader("Cookie", stringBuilder.toString());
        headers.put("Cookie", stringBuilder.toString());
      }

      //SignAuth
      SignRequest signReq = createSignRequest(requestContext.getMethod().toString(),
          requestContext.getIpPort(),
          requestContext.getParams(),
          url.toString(),
          headers);
      httpClientRequest.headers().addAll(getSignAuthHeaders(signReq));

      // body
      if (httpMethod != HttpMethod.GET && requestParam.getBody() != null && requestParam.getBody().length > 0) {
        httpClientRequest.end(Buffer.buffer(requestParam.getBody()));
      } else {
        httpClientRequest.end();
      }
    });
  }

  public RequestContext createRequestContext(HttpMethod method, IpPort ipPort, String uri,
      RequestParam requestParam) {
    RequestContext requestContext = new RequestContext();
    requestContext.setMethod(method);
    requestContext.setIpPort(ipPort);
    requestContext.setUri(uri);
    requestContext.setParams(requestParam);
    return requestContext;
  }

  public SignRequest createSignRequest(String method, IpPort ipPort, RequestParam requestParam, String url,
      Map<String, String> headers) {
    SignRequest signReq = new SignRequest();
    StringBuilder endpoint = new StringBuilder("https://" + ipPort.getHostOrIp());
    endpoint.append(":" + ipPort.getPort());
    endpoint.append(url);
    try {
      signReq.setEndpoint(new URI(endpoint.toString()));
    } catch (URISyntaxException e) {
      LOGGER.error("set uri failed, uri is {}, message: {}", endpoint.toString(), e.getMessage());
    }
    signReq.setContent((requestParam.getBody() != null && requestParam.getBody().length > 0)
        ? new ByteArrayInputStream(requestParam.getBody())
        : null);
    signReq.setHeaders(headers);
    signReq.setHttpMethod(method);
    signReq.setQueryParams(requestParam.getQueryParamsMap());
    return signReq;
  }

  public void addDefaultHeaders(HttpClientRequest request) {
    request.headers().addAll(getDefaultHeaders());
  }

  private Map<String, String> defaultHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_CONTENT_TYPE, "application/json");
    headers.put(HEADER_USER_AGENT, "cse-serviceregistry-client/1.0.0");
    headers.put(HEADER_TENANT_NAME, ServiceRegistryConfig.INSTANCE.getTenantName());

    return headers;
  }

  public MultiMap getDefaultHeaders() {
    return new CaseInsensitiveHeaders().addAll(defaultHeaders());
  }

  public void get(IpPort ipPort, String uri, RequestParam requestParam,
      Handler<RestResponse> responseHandler) {
    httpDo(createRequestContext(HttpMethod.GET, ipPort, uri, requestParam), responseHandler);
  }

  public void post(IpPort ipPort, String uri, RequestParam requestParam,
      Handler<RestResponse> responseHandler) {
    httpDo(createRequestContext(HttpMethod.POST, ipPort, uri, requestParam), responseHandler);
  }

  public void put(IpPort ipPort, String uri, RequestParam requestParam,
      Handler<RestResponse> responseHandler) {
    httpDo(createRequestContext(HttpMethod.PUT, ipPort, uri, requestParam), responseHandler);
  }

  public void delete(IpPort ipPort, String uri, RequestParam requestParam,
      Handler<RestResponse> responseHandler) {
    httpDo(createRequestContext(HttpMethod.DELETE, ipPort, uri, requestParam), responseHandler);
  }

  public Map<String, String> getSignAuthHeaders(SignRequest signReq) {
    Map<String, String> headers = new HashMap<>();
    authHeaderProviders.forEach(provider -> headers.putAll(provider.getSignAuthHeaders(signReq)));
    return headers;
  }
}
