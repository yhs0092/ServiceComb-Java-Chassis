package org.apache.servicecomb.serviceregistry.registry.cache;

import java.util.List;

import org.apache.servicecomb.serviceregistry.api.registry.MicroserviceInstance;

public interface MicroserviceCache {
  MicroserviceCacheKey getKey();

  List<MicroserviceInstance> getInstances();

  String getRevisionId();

  MicroserviceCacheStatus getStatus();

  enum MicroserviceCacheStatus {
    /**
     * init status, not pull instances from sc yet
     */
    INIT,
    /**
     * unknown error
     */
    UNKNOWN_ERROR,
    /**
     * error occurs while getting access to service center
     */
    CLIENT_ERROR,
    /**
     * success to query the service center, but no target microservice found
     */
    SERVICE_NOT_FOUND,
    /**
     * success to query the service center, but the target microservice instance list is not changed
     */
    NO_CHANGE,
    /**
     * success to query the service center, and the target microservice instance list is changed.
     * the cached instance list gets refreshed successfully.
     */
    REFRESHED,
    /**
     * unknown error occurs while setting the pulled instances into this cache
     */
    SETTING_CACHE_ERROR
  }
}
