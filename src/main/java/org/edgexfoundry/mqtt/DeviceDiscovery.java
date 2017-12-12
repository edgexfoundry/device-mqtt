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

package org.edgexfoundry.mqtt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.data.WatcherStore;
import org.edgexfoundry.domain.ScanList;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.AdminState;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.domain.meta.ProvisionWatcher;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeviceDiscovery {
  private static final EdgeXLogger logger =
      EdgeXLoggerFactory.getEdgeXLogger(DeviceDiscovery.class);

  @Autowired
  private WatcherStore watchers;

  @Autowired
  private DeviceStore devices;

  // TODO Generate protocol dynamically
  private Protocol protocol = Protocol.TCP;

  private ProvisionWatcher deviceMatches(Map<String, String> device) {
    for (ProvisionWatcher watcher : watchers.getWatchers()) {
      Map<String, String> identifiers = watcher.getIdentifiers();
      boolean found = true;

      for (Map.Entry<String, String> entry : identifiers.entrySet()) {
        Pattern p = Pattern.compile(entry.getValue());
        Matcher m = null;
        String fieldValue = "";
        fieldValue = device.get(entry.getKey());
        m = p.matcher(fieldValue);

        if (m == null) {
          logger.error("Identifier field " + entry.getKey() + " was not found.");
          break;
        }

        if (m.matches()) {
          found = found && true;
        } else {
          found = false;
          break;
        }
      }

      if (found) {
        logger.debug("Matching Device " + device + " found.");
        return watcher;
      }
    }

    return null;
  }

  private Device deviceExists(Map<String, String> device) {
    return devices.getMetaDevices().stream()
        .filter(d -> device.get("address").equals(d.getAddressable().getPath())).findFirst()
        .orElse(null);
  }

  private Device createDevice(Map<String, String> device, ProvisionWatcher watcher) {
    Device newDevice = new Device();
    newDevice.setProfile(watcher.getProfile());
    newDevice.setService(watcher.getService());
    String name = device.get("name") + " " + device.get("address");
    newDevice.setName(name);
    Addressable addressable =
        createAddressable(device, name, watcher.getService().getAddressable());
    newDevice.setAddressable(addressable);
    newDevice.setLabels(watcher.getService().getLabels());
    newDevice.setAdminState(AdminState.UNLOCKED);
    newDevice.setOperatingState(OperatingState.ENABLED);
    return newDevice;
  }

  private Addressable createAddressable(Map<String, String> device, String name,
      Addressable service) {
    Addressable addressable = new Addressable(name, protocol, device.get("interface"),
        device.get("address"), service.getPort());

    return addressable;
  }

  public void provision(ScanList availableList) {
    if (availableList != null && availableList.getScan().size() > 0) {
      for (Map<String, String> device : availableList.getScan()) {
        Device matchingDevice = deviceExists(device);

        if (matchingDevice != null) {
          if (matchingDevice.getOperatingState().equals(OperatingState.DISABLED)
              || devices.getDevice(matchingDevice.getName()) == null) {
            matchingDevice.setOperatingState(OperatingState.ENABLED);
            devices.add(matchingDevice);
          }

          continue;
        }

        ProvisionWatcher watcher = deviceMatches(device);
        if (watcher != null) {
          // Provision the device
          Device newDevice = createDevice(device, watcher);
          devices.add(newDevice);
        }
      }
    }
  }
}
