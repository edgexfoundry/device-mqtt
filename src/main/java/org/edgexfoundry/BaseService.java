/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: device-mqtt
 * @author: Jim White, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry;

import javax.annotation.PostConstruct;
import javax.ws.rs.NotFoundException;

import org.edgexfoundry.controller.AddressableClient;
import org.edgexfoundry.controller.DeviceServiceClient;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.AdminState;
import org.edgexfoundry.domain.meta.DeviceService;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.Async;

@ImportResource("spring-config.xml")
public class BaseService {

  private static final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(BaseService.class);

  // service name
  @Value("${service.name}")
  private String serviceName;

  // service Address Info
  @Value("${service.host}")
  private String host;

  @Value("${server.port}")
  private int port;

  // service labels
  @Value("${service.labels}")
  private String[] labels;

  // service callback root
  @Value("${service.callback}")
  private String callbackUrl;

  // TODO: This should become a service domain object , not a device service domain object
  private DeviceService service;

  // TODO: This should become a BaseServiceClient
  @Autowired
  private DeviceServiceClient deviceServiceClient;

  @Autowired
  private AddressableClient addressableClient;

  // service initialization
  @Value("${service.connect.retries}")
  private int initRetries;

  @Value("${service.connect.interval}")
  private long initInterval;

  // track initialization attempts
  private int initAttempts;

  // track initialization success
  private boolean initialized;

  // track registration success
  private boolean registered;

  public BaseService() {
    setInitAttempts(0);
    setInitialized(false);
  }

  public int getInitAttempts() {
    return initAttempts;
  }

  public void setInitAttempts(int initAttempts) {
    this.initAttempts = initAttempts;
  }

  public int getInitRetries() {
    return initRetries;
  }

  public void setInitRetries(int initRetries) {
    this.initRetries = initRetries;
  }

  public long getInitInterval() {
    return initInterval;
  }

  public void setInitInterval(long initInterval) {
    this.initInterval = initInterval;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public void setInitialized(boolean initialized) {
    this.initialized = initialized;
  }

  public boolean isRegistered() {
    return registered;
  }

  public void setRegistered(boolean registered) {
    logger.info("Service registered with id: " + service.getId());
    this.registered = registered;
  }

  // The base implementation always succeeds, derived classes customize
  public boolean initialize(String deviceServiceId) {
    return true;
  }

  @PostConstruct
  private void postConstructInitialize() {
    logger.debug("post construction initialization");
    attemptToInitialize();
  }

  @Async
  public void attemptToInitialize() {

    // count the attempt
    setInitAttempts(getInitAttempts() + 1);
    logger.debug("initialization attempt " + getInitAttempts());

    // first - get the service information or register service with metadata
    if (getService() != null) {
      // if we were able to get the service data we're registered
      setRegistered(true);
      // second - invoke any custom initialization method
      setInitialized(initialize(getServiceId()));
    }

    // if both are successful, then we're done
    if (isRegistered() && isInitialized()) {
      logger.info("initialization successful.");
    } else {
      // otherwise see if we need to keep going
      if ((getInitRetries() == 0) || (getInitAttempts() < getInitRetries())) {
        logger.debug("initialization unsuccessful. sleeping " + getInitInterval());
        try {
          Thread.sleep(getInitInterval());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        // start up the next thread
        attemptToInitialize();

      } else {
        // here, we've failed and run out of retries, so just be done.
        logger.info(
            "initialization unsuccessful after " + getInitAttempts() + " attempts.  Giving up.");
        // TODO: what do we do here? exit?
        Application.exit(-1);
      }
    }
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String[] getLabels() {
    return labels;
  }

  public void setLabels(String[] labels) {
    this.labels = labels;
  }

  public String getCallbackUrl() {
    return callbackUrl;
  }

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public DeviceService getService() {
    if (service == null) {
      try {
        service = deviceServiceClient.deviceServiceForName(serviceName);
        setService(service);
        logger.info("service " + serviceName + " has service id " + service.getId());
      } catch (NotFoundException n) {
        try {
          setService();
        } catch (NotFoundException e) {
          logger
              .info("failed to create service " + serviceName + " in metadata: " + e.getMessage());
          service = null;
        }
      } catch (Exception e) {
        logger.error(
            "unable to establish connection to metadata " + e.getCause() + " " + e.getMessage());
        service = null;
      }
    }
    return service;
  }

  private void setService() {
    logger.info("creating service " + serviceName + " in metadata");
    service = new DeviceService();

    // Check for an addressable
    Addressable addressable = null;
    try {
      addressable = addressableClient.addressableForName(serviceName);
    } catch (NotFoundException e) {
      // ignore this and create a new addressable
    }
    if (addressable == null) {
      addressable = new Addressable(serviceName, Protocol.HTTP, host, callbackUrl, port);
      addressable.setOrigin(System.currentTimeMillis());
      try {
        addressableClient.add(addressable);
      } catch (NotFoundException e) {
        logger.error("Could not add addressable to metadata: " + e.getMessage());
        service = null;
        return;
      }
    }

    // Setup the service
    service.setAddressable(addressable);
    service.setOrigin(System.currentTimeMillis());
    service.setAdminState(AdminState.UNLOCKED);
    service.setOperatingState(OperatingState.ENABLED);
    service.setLabels(labels);
    service.setName(serviceName);
    try {
      String id = deviceServiceClient.add(service);
      service.setId(id);
    } catch (NotFoundException e) {
      logger.error("Could not add device service to metadata: " + e.getMessage());
      service = null;
    }
  }

  public void setService(DeviceService srv) {
    service = srv;
    service.setAddressable(srv.getAddressable());
    service.setLabels(srv.getLabels());
    setHost(srv.getAddressable().getAddress());
    setPort(srv.getAddressable().getPort());
    setCallbackUrl(srv.getAddressable().getPath());
    setServiceName(srv.getName());
    setLabels(srv.getLabels());
  }

  public boolean isServiceLocked() {
    DeviceService srv = getService();
    if (srv == null) {
      return true;
    }
    return srv.getAdminState().equals(AdminState.LOCKED);
  }

  public String getServiceId() {
    return service.getId();
  }
}
