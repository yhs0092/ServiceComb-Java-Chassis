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

package org.apache.servicecomb.serviceregistry;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.http.client.utils.URIBuilder;
import org.apache.servicecomb.config.ConfigUtil;
import org.apache.servicecomb.config.archaius.sources.MicroserviceConfigLoader;
import org.apache.servicecomb.foundation.common.concurrent.ConcurrentHashMapEx;
import org.apache.servicecomb.foundation.common.event.EventManager;
import org.apache.servicecomb.foundation.common.net.IpPort;
import org.apache.servicecomb.foundation.common.net.NetUtils;
import org.apache.servicecomb.serviceregistry.api.registry.Microservice;
import org.apache.servicecomb.serviceregistry.api.registry.MicroserviceInstance;
import org.apache.servicecomb.serviceregistry.api.response.FindInstancesResponse;
import org.apache.servicecomb.serviceregistry.cache.InstanceCacheManager;
import org.apache.servicecomb.serviceregistry.client.ServiceRegistryClient;
import org.apache.servicecomb.serviceregistry.client.http.Holder;
import org.apache.servicecomb.serviceregistry.client.http.MicroserviceInstances;
import org.apache.servicecomb.serviceregistry.config.ServiceRegistryConfig;
import org.apache.servicecomb.serviceregistry.definition.MicroserviceDefinition;
import org.apache.servicecomb.serviceregistry.registry.ServiceRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.netflix.config.DynamicPropertyFactory;

public final class RegistryUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegistryUtils.class);

  private static ServiceRegistry serviceRegistry;

  // value is ip or {interface name}
  public static final String PUBLISH_ADDRESS = "servicecomb.service.publishAddress";

  private static final String PUBLISH_PORT = "servicecomb.{transport_name}.publishPort";

  /**
   * This map holds all of the extra {@link ServiceRegistry} instances manually registered by users.
   * It's used in the situation that the microservice instance needs to be registered into multiple
   * service center clusters.
   * The key is the name of the ServiceRegistry instances.
   */
  private static final Map<String, ServiceRegistry> EXTRA_SERVICE_REGISTRIES = new ConcurrentHashMapEx<>(0);

  private RegistryUtils() {
  }

  public static void init() {
    MicroserviceConfigLoader loader = ConfigUtil.getMicroserviceConfigLoader();
    MicroserviceDefinition defaultMicroserviceDefinition = new MicroserviceDefinition(loader.getConfigModels());
    serviceRegistry =
        ServiceRegistryFactory
            .getOrCreate(EventManager.eventBus, ServiceRegistryConfig.INSTANCE, defaultMicroserviceDefinition);
    executeOnEachServiceRegistry(ServiceRegistry::init);
  }

  public static void run() {
    executeOnEachServiceRegistry(ServiceRegistry::run);
  }

  public static void destroy() {
    executeOnEachServiceRegistry(ServiceRegistry::destroy);
  }

  /**
   * @deprecated Replace by {@link #destroy()}
   */
  @Deprecated
  public static void destory() {
    destroy();
  }

  public static ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  public static void setServiceRegistry(ServiceRegistry serviceRegistry) {
    RegistryUtils.serviceRegistry = serviceRegistry;
  }

  public static ServiceRegistryClient getServiceRegistryClient() {
    return serviceRegistry.getServiceRegistryClient();
  }

  public static InstanceCacheManager getInstanceCacheManager() {
    return serviceRegistry.getInstanceCacheManager();
  }

  public static String getAppId() {
    return serviceRegistry.getMicroservice().getAppId();
  }

  public static Microservice getMicroservice() {
    return serviceRegistry.getMicroservice();
  }

  public static MicroserviceInstance getMicroserviceInstance() {
    return serviceRegistry.getMicroserviceInstance();
  }

  public static String getPublishAddress() {
    String publicAddressSetting =
        DynamicPropertyFactory.getInstance().getStringProperty(PUBLISH_ADDRESS, "").get();
    publicAddressSetting = publicAddressSetting.trim();
    if (publicAddressSetting.isEmpty()) {
      return NetUtils.getHostAddress();
    }

    // placeholder is network interface name
    if (publicAddressSetting.startsWith("{") && publicAddressSetting.endsWith("}")) {
      return NetUtils
          .ensureGetInterfaceAddress(publicAddressSetting.substring(1, publicAddressSetting.length() - 1))
          .getHostAddress();
    }

    return publicAddressSetting;
  }

  public static String getPublishHostName() {
    String publicAddressSetting =
        DynamicPropertyFactory.getInstance().getStringProperty(PUBLISH_ADDRESS, "").get();
    publicAddressSetting = publicAddressSetting.trim();
    if (publicAddressSetting.isEmpty()) {
      return NetUtils.getHostName();
    }

    if (publicAddressSetting.startsWith("{") && publicAddressSetting.endsWith("}")) {
      return NetUtils
          .ensureGetInterfaceAddress(publicAddressSetting.substring(1, publicAddressSetting.length() - 1))
          .getHostName();
    }

    return publicAddressSetting;
  }

  /**
   * In the case that listening address configured as 0.0.0.0, the publish address will be determined
   * by the query result for the net interfaces.
   *
   * @return the publish address, or {@code null} if the param {@code address} is null.
   */
  public static String getPublishAddress(String schema, String address) {
    if (address == null) {
      return address;
    }

    try {
      URI originalURI = new URI(schema + "://" + address);
      IpPort ipPort = NetUtils.parseIpPort(originalURI);
      if (ipPort == null) {
        LOGGER.warn("address {} not valid.", address);
        return null;
      }

      IpPort publishIpPort = genPublishIpPort(schema, ipPort);
      URIBuilder builder = new URIBuilder(originalURI);
      return builder.setHost(publishIpPort.getHostOrIp()).setPort(publishIpPort.getPort()).build().toString();
    } catch (URISyntaxException e) {
      LOGGER.warn("address {} not valid.", address);
      return null;
    }
  }

  private static IpPort genPublishIpPort(String schema, IpPort ipPort) {
    String publicAddressSetting = DynamicPropertyFactory.getInstance()
        .getStringProperty(PUBLISH_ADDRESS, "")
        .get();
    publicAddressSetting = publicAddressSetting.trim();

    if (publicAddressSetting.isEmpty()) {
      InetSocketAddress socketAddress = ipPort.getSocketAddress();
      if (socketAddress.getAddress().isAnyLocalAddress()) {
        String host = NetUtils.getHostAddress();
        LOGGER.warn("address {}, auto select a host address to publish {}:{}, maybe not the correct one",
            socketAddress,
            host,
            socketAddress.getPort());
        return new IpPort(host, ipPort.getPort());
      }

      return ipPort;
    }

    if (publicAddressSetting.startsWith("{") && publicAddressSetting.endsWith("}")) {
      publicAddressSetting = NetUtils
          .ensureGetInterfaceAddress(
              publicAddressSetting.substring(1, publicAddressSetting.length() - 1))
          .getHostAddress();
    }

    String publishPortKey = PUBLISH_PORT.replace("{transport_name}", schema);
    int publishPortSetting = DynamicPropertyFactory.getInstance()
        .getIntProperty(publishPortKey, 0)
        .get();
    int publishPort = publishPortSetting == 0 ? ipPort.getPort() : publishPortSetting;
    return new IpPort(publicAddressSetting, publishPort);
  }

  /**
   * Consider using {@link #findServiceInstances(String, String, String)} instead.
   */
  @Deprecated
  public static List<MicroserviceInstance> findServiceInstance(String appId, String serviceName,
      String versionRule) {
    List<MicroserviceInstance> result = new ArrayList<>();
    executeOnEachServiceRegistry(registry -> {
      List<MicroserviceInstance> instanceList = registry.findServiceInstance(appId, serviceName, versionRule);
      if (null != instanceList && !instanceList.isEmpty()) {
        result.addAll(instanceList);
      }
    });
    return result;
  }

  // update microservice instance properties
  public static boolean updateInstanceProperties(Map<String, String> instanceProperties) {
    Holder<Boolean> result = new Holder<>();
    result.setValue(Boolean.TRUE);
    executeOnEachServiceRegistry(registry -> {
      if (!registry.updateInstanceProperties(instanceProperties)) {
        result.setValue(Boolean.FALSE);
      }
    });
    return result.getValue();
  }

  public static Microservice getMicroservice(String microserviceId) {
    Holder<Microservice> result = new Holder<>();
    executeOnEachServiceRegistry(registry -> {
      if (null == result.getValue()) {
        result.setValue(serviceRegistry.getRemoteMicroservice(microserviceId));
      }
    });
    return result.getValue();
  }

  /**
   * Consider using {@link #findServiceInstances(String, String, String)} instead.
   */
  @Deprecated
  public static MicroserviceInstances findServiceInstances(String appId, String serviceName,
      String versionRule, String revision) {
    return serviceRegistry.findServiceInstances(appId, serviceName, versionRule, revision);
  }

  /**
   * Query the microservice instances specified by applicationId, microserviceName and versionRule.
   */
  public static MicroserviceInstances findServiceInstances(String appId, String serviceName, String versionRule) {
    final ArrayList<MicroserviceInstances> resultList = new ArrayList<>(1);
    executeOnEachServiceRegistry(registry -> {
      final MicroserviceInstances serviceInstances = registry.findServiceInstances(appId, serviceName, versionRule);
      if (null != serviceInstances) {
        resultList.add(serviceInstances);
      }
    });

    if (resultList.isEmpty()) {
      return null;
    }
    if (resultList.size() == 1) {
      // no need to merge
      return resultList.get(0);
    }
    // Multiple ServiceRegistry instances return valid responses. These responses should be merged.
    final MicroserviceInstances result = new MicroserviceInstances();
    result.setNeedRefresh(false);
    result.setMicroserviceNotExist(true);
    List<MicroserviceInstance> finalInstanceList = new ArrayList<>();
    resultList.forEach(microserviceInstances -> {
      result.setNeedRefresh(result.isNeedRefresh() || microserviceInstances.isNeedRefresh());
      result.setMicroserviceNotExist(result.isMicroserviceNotExist() && microserviceInstances.isMicroserviceNotExist());
      FindInstancesResponse instancesResponse = microserviceInstances.getInstancesResponse();
      if (null != instancesResponse
          && null != instancesResponse.getInstances()
          && !instancesResponse.getInstances().isEmpty()) {
        finalInstanceList.addAll(instancesResponse.getInstances());
      }
    });

    result.setInstancesResponse(new FindInstancesResponse());
    result.getInstancesResponse().setInstances(finalInstanceList);
    return result;
  }

  public static String calcSchemaSummary(String schemaContent) {
    return Hashing.sha256().newHasher().putString(schemaContent, Charsets.UTF_8).hash().toString();
  }

  /**
   * Add a {@link ServiceRegistry} instance into the {@link #EXTRA_SERVICE_REGISTRIES}.
   * These extra ServiceRegistry instances works almost the same as the default registry {@link #serviceRegistry}.
   * <p/>
   * <em>Notice that this method <b>ONLY</b> add the instance into the {@link #EXTRA_SERVICE_REGISTRIES}
   * without anymore initialization operation. The invoker should handle the initialization operations,
   * like {@link ServiceRegistry#init()} by himself.</em>
   *
   * @param serviceRegistry The added extra ServiceRegistry instance, which should be assigned a unique name.
   * @throws NullPointerException if the input {@code serviceRegistry} is null
   * @throws IllegalArgumentException if the name of the {@code serviceRegistry} is empty
   * or is duplicate with another extra ServiceRegistry instance.
   */
  public static void addExtraServiceRegistry(ServiceRegistry serviceRegistry) {
    synchronized (EXTRA_SERVICE_REGISTRIES) {
      Objects.requireNonNull(serviceRegistry);
      if (StringUtils.isEmpty(serviceRegistry.name())) {
        throw new IllegalArgumentException("The name of ServiceRegistry is empty");
      }
      if (EXTRA_SERVICE_REGISTRIES.containsKey(serviceRegistry.name())) {
        throw new IllegalArgumentException("Duplicated ServiceRegistry name: " + serviceRegistry.name());
      }
      EXTRA_SERVICE_REGISTRIES.put(serviceRegistry.name(), serviceRegistry);
    }
  }

  /**
   * Remove an extra ServiceRegistry instance specified by the {@code registryName}.
   * <p/>
   * <em>Notice that this method <b>ONLY</b> remove the instance from the {@link #EXTRA_SERVICE_REGISTRIES}
   * without anymore clean up operation. The invoker should handle the clean up operations,
   * like {@link ServiceRegistry#destroy()} by himself.</em>
   *
   * @param registryName The name of the ServiceRegistry instance to be removed
   * @return The removed ServiceRegistry instance, or null if there is no instance matched.
   * @throws IllegalArgumentException if the input {@code registryName} is empty
   */
  public static ServiceRegistry removeExtraServiceRegistry(String registryName) {
    synchronized (EXTRA_SERVICE_REGISTRIES) {
      if (StringUtils.isEmpty(registryName)) {
        throw new IllegalArgumentException("The registryName is empty");
      }
      return EXTRA_SERVICE_REGISTRIES.remove(registryName);
    }
  }

  /**
   * The {@code action} is applied to all of the ServiceRegistry instances
   * including the default one {@link #serviceRegistry} and all in the {@link #EXTRA_SERVICE_REGISTRIES}
   */
  private static void executeOnEachServiceRegistry(Consumer<ServiceRegistry> action) {
    if (null != serviceRegistry) {
      action.accept(serviceRegistry);
    }
    if (!EXTRA_SERVICE_REGISTRIES.isEmpty()) {
      EXTRA_SERVICE_REGISTRIES.values().forEach(action);
    }
  }
}
