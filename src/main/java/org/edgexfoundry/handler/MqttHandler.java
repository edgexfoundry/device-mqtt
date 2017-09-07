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

package org.edgexfoundry.handler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.edgexfoundry.data.ObjectStore;
import org.edgexfoundry.data.ProfileStore;
import org.edgexfoundry.domain.MqttObject;
import org.edgexfoundry.domain.ResponseObject;
import org.edgexfoundry.domain.ScanList;
import org.edgexfoundry.domain.Transaction;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.exception.controller.ServiceException;
import org.edgexfoundry.mqtt.DeviceDiscovery;
import org.edgexfoundry.mqtt.MqttDriver;
import org.edgexfoundry.mqtt.ObjectTransform;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class MqttHandler {

  private static final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(MqttHandler.class);

  @Autowired
  private MqttDriver driver;

  @Autowired
  private DeviceDiscovery discover;

  @Autowired
  private ProfileStore profiles;

  @Autowired
  private ObjectTransform transform;

  @Autowired
  private ObjectStore objectCache;

  @Autowired
  private CoreDataMessageHandler processor;

  @Value("${mqtt.device.init:#{null}}")
  private String mqttInit;
  @Value("${mqtt.device.init.args:#{null}}")
  private String mqttInitArgs;
  @Value("${mqtt.device.remove:#{null}}")
  private String mqttRemove;
  @Value("${mqtt.device.remove.args:#{null}}")
  private String mqttRemoveArgs;

  public Map<String, Transaction> transactions = new HashMap<>();

  public void initialize() {
    if (driver != null) {
      driver.initialize();
    }
  }

  public void initializeDevice(Device device) {
    if (mqttInit != null && commandExists(device, mqttInit)) {
      executeCommand(device, mqttInit, mqttInitArgs);
    }

    logger.info("Initialized Device: " + device.getName());
  }

  public void disconnectDevice(Device device) {
    if (mqttRemove != null && commandExists(device, mqttRemove)) {
      executeCommand(device, mqttRemove, mqttRemoveArgs);
    }

    driver.disconnectDevice(device.getAddressable());
    logger.info("Disconnected Device: " + device.getName());
  }

  public void scan() {
    ScanList availableList = null;
    availableList = driver.discover();
    discover.provision(availableList);
  }

  public boolean commandExists(Device device, String command) {
    Map<String, Map<String, List<ResourceOperation>>> cmdsForDevice =
        profiles.getCommands().get(device.getName());
    Map<String, List<ResourceOperation>> op = cmdsForDevice.get(command.toLowerCase());
    if (op == null) {
      return false;
    }

    return true;
  }

  public Map<String, String> executeCommand(Device device, String cmd, String arguments) {
    // set immediate flag to false to read from object cache of last readings
    Boolean immediate = true;
    Transaction transaction = new Transaction();
    String transactionId = transaction.getTransactionId();
    transactions.put(transactionId, transaction);
    executeOperations(device, cmd, arguments, immediate, transactionId);

    synchronized (transactions) {
      while (!transactions.get(transactionId).isFinished()) {
        try {
          transactions.wait();
        } catch (InterruptedException e) {
          // Exit quietly on break
          return null;
        }
      }
    }

    List<Reading> readings = transactions.get(transactionId).getReadings();
    transactions.remove(transactionId);

    return sendTransaction(device.getName(), readings);
  }

  public Map<String, String> executeCommandGet(String transactionId, String deviceName) {
    synchronized (transactions) {
      while (!transactions.get(transactionId).isFinished()) {
        try {
          transactions.wait();
        } catch (InterruptedException e) {
          // Exit quietly on break
          return null;
        }
      }
    }

    List<Reading> readings = transactions.get(transactionId).getReadings();
    transactions.remove(transactionId);

    return sendTransaction(deviceName, readings);
  }

  public Map<String, String> sendTransaction(String deviceName, List<Reading> readings) {
    Map<String, String> valueDescriptorMap = new HashMap<>();
    List<ResponseObject> resps =
        processor.sendCoreData(deviceName, readings, profiles.getObjects().get(deviceName));

    for (ResponseObject obj : resps) {
      valueDescriptorMap.put(obj.getName(), obj.getValue());
    }

    return valueDescriptorMap;
  }

  private void executeOperations(Device device, String commandName, String arguments,
      Boolean immediate, String transactionId) {
    String method = (arguments == null) ? "get" : "set";

    String deviceName = device.getName();
    String deviceId = device.getId();
    // get the objects for this device
    Map<String, MqttObject> objects = profiles.getObjects().get(deviceName);

    // get the operations for this device's object operation method
    List<ResourceOperation> operations =
        getResourceOperations(deviceName, deviceId, transactionId, commandName, method);

    for (ResourceOperation operation : operations) {
      String opResource = operation.getResource();
      if (opResource != null) {
        if (operation.getOperation().equals("get")) {
          executeOperations(device, opResource, null, immediate, transactionId);
        } else {
          executeOperations(device, opResource, arguments, immediate, transactionId);
        }
        continue;
      }

      String objectName = operation.getObject();
      MqttObject object = getMqttObject(objects, objectName, transactionId);

      // TODO Add property flexibility
      if (!operation.getProperty().equals("value")) {
        throw new ServiceException(new UnsupportedOperationException(
            "Only property of value is implemented for this service!"));
      }

      String val = null;

      if (method.equals("set")) {
        val = parseArguments(arguments, operation, device, object, objects);
      }

      // command operation for client processing
      if (requiresQuery(immediate, method, device, operation)) {
        String opId = transactions.get(transactionId).newOpId();
        final String parameter = val;
        new Thread(() -> driver.process(operation, device, object, parameter, transactionId, opId))
            .start();
      }
    }
  }

  private Boolean requiresQuery(boolean immediate, String method, Device device,
      ResourceOperation operation) {
    // if the immediate flag is set
    if (immediate) {
      return true;
    }
    // if the resource operation method is a set
    if (method.equals("set")) {
      return true;
    }
    // if the objectCache has no values
    if (objectCache.get(device, operation) == null) {
      return true;
    }

    return false;
  }

  private MqttObject getMqttObject(Map<String, MqttObject> objects, String objectName,
      String transactionId) {
    MqttObject object = objects.get(objectName);

    if (object == null) {
      logger.error("Object " + objectName + " not found");
      String opId = transactions.get(transactionId).newOpId();
      completeTransaction(transactionId, opId, new ArrayList<Reading>());
      throw new NotFoundException("DeviceObject", objectName);
    }

    return object;
  }

  private List<ResourceOperation> getResourceOperations(String deviceName, String deviceId,
      String transactionId, String commandName, String method) {
    // get this device's resources map
    Map<String, Map<String, List<ResourceOperation>>> resources =
        profiles.getCommands().get(deviceName);

    if (resources == null) {
      logger.error("Command requested for unknown device " + deviceName);
      String opId = transactions.get(transactionId).newOpId();
      completeTransaction(transactionId, opId, new ArrayList<Reading>());
      throw new NotFoundException("Device", deviceId);
    }

    // get the get and set resources for this device's object
    Map<String, List<ResourceOperation>> resource = resources.get(commandName.toLowerCase());

    if (resource == null || resource.get(method) == null) {
      logger.error("Resource " + commandName + " not found");
      String opId = transactions.get(transactionId).newOpId();
      completeTransaction(transactionId, opId, new ArrayList<Reading>());
      throw new NotFoundException("Command", commandName);
    }

    // get the operations for this device's object operation method
    return resource.get(method);
  }

  private String parseArguments(String arguments, ResourceOperation operation, Device device,
      MqttObject object, Map<String, MqttObject> objects) {

    PropertyValue value = object.getProperties().getValue();
    String val = parseArg(arguments, operation, value, operation.getParameter());

    // if the written value is on a multiplexed handle, read the current value and apply
    // the mask first
    if (!value.mask().equals(BigInteger.ZERO)) {
      String result = driver.processCommand(device.getName(), "get", device.getAddressable(),
          object.getAttributes(), val);
      val = transform.maskedValue(value, val, result);
      if (operation.getSecondary() != null) {
        for (String secondary : operation.getSecondary()) {
          if (objects.get(secondary) != null) {
            PropertyValue secondaryValue = objects.get(secondary).getProperties().getValue();
            String secondVal = parseArg(arguments, operation, secondaryValue, secondary);
            val = transform.maskedValue(secondaryValue, secondVal, "0x" + val);
          }
        }
      }
    }

    while (val.length() < value.size()) {
      val = "0" + val;
    }

    return val;
  }

  private String parseArg(String arguments, ResourceOperation operation, PropertyValue value,
      String object) {
    // parse the argument string and get the "value" parameter
    JsonObject args;
    String val = null;
    JsonElement jsonElem = null;
    Boolean passed = true;

    // check for parameters from the command
    if (arguments != null) {
      args = new JsonParser().parse(arguments).getAsJsonObject();
      jsonElem = args.get(object);
    }

    // if the parameter is passed from the command, use it, otherwise treat parameter
    // as the default
    if (jsonElem == null || jsonElem.toString().equals("null")) {
      val = operation.getParameter();
      passed = false;
    } else {
      val = jsonElem.toString().replace("\"", "");
    }

    // if no value is specified by argument or parameter, take the object default from the profile
    if (val == null) {
      val = value.getDefaultValue();
      passed = false;
    }

    // if a mapping translation has been specified in the profile, use it
    Map<String, String> mappings = operation.getMappings();
    if (mappings != null && mappings.containsKey(val)) {
      val = mappings.get(val);
      passed = false;
    }

    if (!value.mask().equals(BigInteger.ZERO) && passed) {
      val = transform.format(value, val);
    }

    return val;
  }

  public void processJson(String json) {
    Transaction transaction = new Transaction();
    String transactionId = transaction.getTransactionId();
    transactions.put(transactionId, transaction);
    String opId = transactions.get(transactionId).newOpId();
    driver.processJson(json, transactionId, opId);
  }

  public void completeTransaction(String transactionId, String opId, List<Reading> readings) {
    synchronized (transactions) {
      transactions.get(transactionId).finishOp(opId, readings);
      transactions.notifyAll();
    }
  }
}
