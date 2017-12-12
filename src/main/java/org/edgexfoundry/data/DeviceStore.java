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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.controller.AddressableClient;
import org.edgexfoundry.controller.DeviceClient;
import org.edgexfoundry.controller.DeviceProfileClient;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.AdminState;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.DeviceProfile;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.handler.MqttHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceStore {
  private static final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(DeviceStore.class);

  @Autowired
  private DeviceClient deviceClient;

  @Autowired
  private AddressableClient addressableClient;

  @Autowired
  private DeviceProfileClient profileClient;

  @Autowired
  private MqttHandler mqtt;

  @Autowired
  private WatcherStore watchers;

  @Autowired
  private ProfileStore profiles;

  @Value("${service.name}")
  private String serviceName;

  // cache for devices
  private Map<String, Device> devices = new HashMap<>();

  public boolean remove(Device device) {
    logger.debug("Removing managed device:  " + device.getName());
    if (devices.containsKey(device.getName())) {
      devices.remove(device.getName());
      mqtt.disconnectDevice(device);
      deviceClient.updateOpState(device.getId(), OperatingState.DISABLED.name());
      profiles.removeDevice(device);
    }
    return true;
  }

  public boolean remove(String deviceId) {
    Device d = devices.values().stream().filter(device -> device.getId().equals(deviceId)).findAny()
        .orElse(null);

    if (d != null) {
      remove(d);
    }

    return true;
  }

  public boolean add(String deviceId) {
    Device device = deviceClient.device(deviceId);
    return add(device);
  }

  public boolean add(Device device) {
    if (devices.containsKey(device.getName())) {
      devices.remove(device.getName());
      profiles.removeDevice(device);
    }

    logger.info("Adding managed device:  " + device.getName());
    Device metaDevice = addDeviceToMetaData(device);

    if (metaDevice == null) {
      remove(device);
      return false;
    }

    if (metaDevice.getOperatingState().equals(OperatingState.ENABLED)) {
      mqtt.initializeDevice(metaDevice);
    }

    return true;
  }

  private Device addDeviceToMetaData(Device device) {
    // Create a new addressable Object with the devicename + last 6 digits of MAC address.
    // Assume this to be unique

    Addressable addressable = null;
    try {
      addressableClient.addressableForName(device.getAddressable().getName());
    } catch (javax.ws.rs.NotFoundException e) {
      addressable = device.getAddressable();
      addressable.setOrigin(System.currentTimeMillis());
      logger.info("Creating new Addressable Object with name: " + addressable.getName()
          + ", Address:" + addressable);
      String addressableId = addressableClient.add(addressable);
      addressable.setId(addressableId);
      device.setAddressable(addressable);
    }

    Device d = null;
    try {
      d = deviceClient.deviceForName(device.getName());
      device.setId(d.getId());
      if (!device.getOperatingState().equals(d.getOperatingState())) {
        deviceClient.updateOpState(device.getId(), device.getOperatingState().name());
      }
    } catch (javax.ws.rs.NotFoundException e) {
      logger.info("Adding Device to Metadata:" + device.getName());
      try {
        device.setId(deviceClient.add(device));
      } catch (Exception f) {
        logger.error("Could not add new device " + device.getName() + " to metadata with error "
            + e.getMessage());
        return null;
      }
    }

    profiles.addDevice(device);
    devices.put(device.getName(), device);
    return device;
  }

  public boolean update(String deviceId) {
    Device device = deviceClient.device(deviceId);
    Device localDevice = getDeviceById(deviceId);
    if (device != null && localDevice != null && compare(device, localDevice)) {
      return true;
    }

    return add(device);
  }

  private boolean compare(Device a, Device b) {
    if (a.getAddressable().equals(b.getAddressable())
        && a.getAdminState().equals(a.getAdminState())
        && a.getDescription().equals(b.getDescription()) && a.getId().equals(b.getId())
        && a.getLabels().equals(b.getLabels()) && a.getLocation().equals(b.getLocation())
        && a.getName().equals(b.getName()) && a.getOperatingState().equals(b.getOperatingState())
        && a.getProfile().equals(b.getProfile()) && a.getService().equals(b.getService())) {
      return true;
    }

    return false;
  }

  public Map<String, Device> getDevices() {
    return devices;
  }

  public Map<String, Device> initialize(String id) {
    devices = new HashMap<>();
    watchers.initialize(id);
    mqtt.initialize();
    List<Device> metaDevices = deviceClient.devicesForService(id);
    for (Device device : metaDevices) {
      deviceClient.updateOpState(device.getId(), OperatingState.DISABLED.name());
      add(device);
    }

    logger.info("Device service has " + devices.size() + " devices.");
    return getDevices();
  }

  public List<Device> getMetaDevices() {
    List<Device> metaDevices;
    metaDevices = deviceClient.devicesForServiceByName(serviceName);
    for (Device metaDevice : metaDevices) {
      Device device = devices.get(metaDevice.getName());

      if (device != null) {
        device.setOperatingState(metaDevice.getOperatingState());
      }
    }
    return metaDevices;
  }

  public Device getMetaDevice(String deviceName) {
    List<Device> metaDevices = getMetaDevices();
    Device result = metaDevices.stream().filter(device -> deviceName.equals(device.getName()))
        .findAny().orElse(null);
    return result;
  }

  public Device getMetaDeviceById(String deviceId) {
    List<Device> metaDevices = getMetaDevices();
    Device result = metaDevices.stream().filter(device -> deviceId.equals(device.getId())).findAny()
        .orElse(null);
    return result;
  }

  public Device getDevice(String deviceName) {
    if (devices != null) {
      return devices.get(deviceName);
    } else {
      return null;
    }
  }

  public Device getDeviceById(String deviceId) {
    if (devices != null) {
      return devices.values().stream().filter(device -> device.getId().equals(deviceId)).findAny()
          .orElse(null);
    }

    return null;
  }

  public boolean isDeviceLocked(String deviceId) {
    Device device = getDeviceById(deviceId);
    if (device == null) {
      device = getMetaDeviceById(deviceId);
      if (device == null) {
        logger.error("Device not present with id " + deviceId);
        throw new NotFoundException("device", deviceId);
      }
    }

    return device.getAdminState().equals(AdminState.LOCKED)
        || device.getOperatingState().equals(OperatingState.DISABLED);
  }

  public void setDeviceOpState(String deviceName, OperatingState state) {
    deviceClient.updateOpStateByName(deviceName, state.name());
  }

  public void setDeviceByIdOpState(String deviceId, OperatingState state) {
    deviceClient.updateOpState(deviceId, state.name());
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
    for (Device device : devices.entrySet().stream().map(d -> d.getValue())
        .filter(d -> profile.getName().equals(d.getProfile().getName()))
        .collect(Collectors.toList())) {
      // update all devices that use the profile
      device.setProfile(profile);
      success &= update(device.getId());
    }
    return success;
  }
}
