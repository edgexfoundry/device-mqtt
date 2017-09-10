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

package org.edgexfoundry.data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.edgexfoundry.controller.DeviceProfileClient;
import org.edgexfoundry.controller.DeviceServiceClient;
import org.edgexfoundry.controller.ProvisionWatcherClient;
import org.edgexfoundry.domain.SimpleWatcher;
import org.edgexfoundry.domain.meta.DeviceProfile;
import org.edgexfoundry.domain.meta.DeviceService;
import org.edgexfoundry.domain.meta.ProvisionWatcher;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class WatcherStore {

  private static final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(WatcherStore.class);

  @Autowired
  private ProvisionWatcherClient provisionClient;

  @Autowired
  private SimpleWatcher defaultWatchers;

  @Autowired
  private DeviceProfileClient profileClient;

  @Autowired
  private DeviceServiceClient serviceClient;

  private List<ProvisionWatcher> watchers = new ArrayList<ProvisionWatcher>();

  public List<ProvisionWatcher> getWatchers() {
    return watchers;
  }

  public void initialize(String deviceServiceId) {
    List<ProvisionWatcher> metaWatchers =
        provisionClient.provisionWatcherForService(deviceServiceId);

    for (ProvisionWatcher watcher : metaWatchers) {
      add(watcher);
    }

    addDefaultWatchers(deviceServiceId);
  }

  public boolean add(String provisionWatcher) {
    ProvisionWatcher watcher = provisionClient.provisionWatcher(provisionWatcher);
    return add(watcher);
  }

  public boolean add(ProvisionWatcher watcher) {
    if (watchers.stream().noneMatch(w -> w.getName().equals(watcher.getName()))) {
      if (watcher.getProfile().getId() == null) {
        logger.info("Profile " + watcher.getProfile().getName()
            + " has not been added to metadata, watcher will not be added");
        return false;
      }

      if (watcher.getId() == null) {
        try {
          watcher.setId(provisionClient.add(watcher));
        } catch (Exception e) {
          logger.error("Error adding new provision watcher " + watcher.getName() + " error is: "
              + e.getMessage());
        }
      }

      watchers.add(watcher);
    }

    return true;
  }

  public boolean remove(String provisionWatcher) {
    ProvisionWatcher watcher;

    try {
      watcher = provisionClient.provisionWatcherForName(provisionWatcher);
    } catch (Exception e) {
      watcher = new ProvisionWatcher();
      watcher.setId(provisionWatcher);
    }

    return remove(watcher);
  }

  public boolean remove(ProvisionWatcher provisionWatcher) {
    ProvisionWatcher watcher = watchers.stream()
        .filter(w -> w.getId().equals(provisionWatcher.getId())).findAny().orElse(null);

    if (watcher != null) {
      watchers.remove(watcher);
    }

    return true;
  }

  public boolean update(String provisionWatcher) {
    ProvisionWatcher watcher = provisionClient.provisionWatcherForName(provisionWatcher);
    return update(watcher);
  }

  public boolean update(ProvisionWatcher provisionWatcher) {
    ProvisionWatcher watcher = watchers.stream()
        .filter(w -> w.getId().equals(provisionWatcher.getId())).findAny().orElse(null);

    if (watcher != null) {
      watchers.remove(watcher);
      return add(provisionWatcher);
    }

    return false;
  }

  public DeviceProfile getWatcherProfile(ProvisionWatcher watcher) {
    DeviceProfile profile = profileClient.deviceProfileForName(watcher.getProfile().getName());
    return profile;
  }

  public Integer addDefaultWatchers(String deviceServiceId) {
    for (int i = 0; i < defaultWatchers.getSize(); i++) {
      ProvisionWatcher watcher = new ProvisionWatcher();
      watcher.setName(defaultWatchers.getName()[i]);
      try {
        ProvisionWatcher existing = provisionClient.provisionWatcherForName(watcher.getName());
        add(existing);
      } catch (Exception notfound) {
        watcher.setIdentifiers(defaultWatchers.getIdentifiers().get(i));
        DeviceService service = serviceClient.deviceService(deviceServiceId);

        if (service.getAddressable().getName().equals(defaultWatchers.getService()[i])) {
          watcher.setService(service);
        }

        DeviceProfile profile;
        try {
          profile = profileClient.deviceProfileForName(defaultWatchers.getProfile()[i]);
        } catch (Exception e) {
          profile = new DeviceProfile();
          profile.setName(defaultWatchers.getProfile()[i]);
        }

        watcher.setProfile(profile);
        add(watcher);
      }
    }

    return watchers.size();
  }

  public boolean updateProfile(String profileId) {
    DeviceProfile profile;
    try {
      profile = profileClient.deviceProfile(profileId);
    } catch (Exception e) {
      // No such profile exists to update
      return true;
    }

    boolean success = true;
    for (ProvisionWatcher watcher : watchers.stream()
        .filter(w -> profile.getName().equals(w.getProfile().getName()))
        .collect(Collectors.toList())) {

      // update all devices that use the profile
      watcher.setProfile(profile);
      success &= update(watcher);
    }
    return success;
  }
}
