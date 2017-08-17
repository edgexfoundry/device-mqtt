/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @microservice:  device-mqtt
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
import org.edgexfoundry.domain.MQTTDevice;
import org.edgexfoundry.domain.MQTTObject;
import org.edgexfoundry.domain.ResponseObject;
import org.edgexfoundry.domain.ScanList;
import org.edgexfoundry.domain.Transaction;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.BadCommandRequestException;
import org.edgexfoundry.mqtt.DeviceDiscovery;
import org.edgexfoundry.mqtt.MQTTDriver;
import org.edgexfoundry.mqtt.ObjectTransform;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class MQTTHandler {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(MQTTHandler.class);

	@Autowired
	private MQTTDriver driver;

	@Autowired
	private DeviceDiscovery discover;

	@Autowired
	private ProfileStore profiles;

	@Autowired
	private ObjectTransform transform;

	@Autowired
	private ObjectStore objectCache;

	@Value("${mqtt.device.init:#{null}}")
	private String mqttInit;
	@Value("${mqtt.device.init.args:#{null}}")
	private String mqttInitArgs;

	@Value("${mqtt.device.remove:#{null}}")
	private String mqttRemove;
	@Value("${mqtt.device.remove.args:#{null}}")
	private String mqttRemoveArgs;

	public Map<String, Transaction> transactions = new HashMap<String, Transaction>();

	public void initialize() {
		if (driver != null)
			driver.initialize();
	}

	public void initializeDevice(Device device) {
		MQTTDevice MQTTDevice = new MQTTDevice(device);
		MQTTDevice.setFailures(0);
		if (mqttInit != null && commandExists(device, mqttInit))
			executeCommand(MQTTDevice, mqttInit, mqttInitArgs);
		logger.info("Initialized Device: " + device.getName());
	}

	public void disconnectDevice(Device device) {
		MQTTDevice MQTTDevice = new MQTTDevice(device);
		if (mqttRemove != null && commandExists(device, mqttRemove))
			executeCommand(MQTTDevice, mqttRemove, mqttRemoveArgs);
		driver.disconnectDevice(device.getAddressable());
		logger.info("Disconnected Device: " + device.getName());
	}

	public void scan() {
		ScanList availableList = null;
		availableList = driver.discover();
		discover.provision(availableList);
	}

	public boolean commandExists(Device device, String command) {
		Map<String, Map<String, List<ResourceOperation>>> cmdsForDevice = profiles.getCommands().get(device.getName());
		Map<String, List<ResourceOperation>> op = cmdsForDevice.get(command.toLowerCase());
		if (op == null)
			return false;
		return true;
	}

	public Map<String, String> executeCommand(MQTTDevice device, String cmd, String arguments) {
		// set immediate flag to false to read from object cache of last
		// readings
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

		List<ResponseObject> resp = transactions.get(transactionId).getResponses();
		transactions.remove(transactionId);
		Map<String, String> valueDescriptorMap = new HashMap<String, String>();
		for (ResponseObject obj : resp)
			valueDescriptorMap.put(obj.getName(), obj.getValue());
		return valueDescriptorMap;
	}

	private void executeOperations(MQTTDevice device, String commandName, String arguments, Boolean immediate,
			String transactionId) {
		String method;
		if (arguments == null) {
			method = "get";
		} else {
			method = "set";
		}

		List<ResponseObject> resp = new ArrayList<ResponseObject>();

		String deviceName = device.getName();
		String deviceId = device.getId();
		// get the objects for this device
		Map<String, MQTTObject> objects = profiles.getObjects().get(deviceName);
		// get this device's resources map
		Map<String, Map<String, List<ResourceOperation>>> resources = profiles.getCommands().get(deviceName);

		if (resources == null) {
			logger.error("Command requested for unknown device " + deviceName);
			String opId = transactions.get(transactionId).newOpId();
			completeTransaction(transactionId, opId, resp);
			throw new BadCommandRequestException("Command requested for unknown device " + deviceName);
		}

		// get the get and set resources for this device's object
		Map<String, List<ResourceOperation>> resource = resources.get(commandName.toLowerCase());

		if (resource == null || resource.get(method) == null) {
			logger.error("Resource " + commandName + " not found");
			String opId = transactions.get(transactionId).newOpId();
			completeTransaction(transactionId, opId, resp);
			throw new BadCommandRequestException("Resource " + commandName + " not found");
		}

		// get the operations for this device's object operation method
		List<ResourceOperation> operations = resource.get(method);

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
			MQTTObject object = objects.get(objectName);
			if (object == null) {
				logger.error("Object " + objectName + " not found");
				String opId = transactions.get(transactionId).newOpId();
				completeTransaction(transactionId, opId, resp);
				throw new BadCommandRequestException("Object " + objectName + " not found");
			}

			if (!operation.getProperty().equals("value"))
				throw new BadCommandRequestException("Only property of value is implemented for this service!");

			String val = null;

			if (method.equals("set"))
				val = parseArguments(arguments, operation, device, object, objects);

			// command operation for client processing
			if (immediate || method.equals("set") || objectCache.get(deviceId, objectName) == null
					|| objectCache.get(deviceId, objectName).equals("{}")) {
				String opId = transactions.get(transactionId).newOpId();
				final String parameter = val;
				new Thread(() -> driver.process(operation, device, object, parameter, transactionId, opId)).start();
			}
		}
	}

	// TODO - at some point we need to add transformation info into profile so
	// we don't have to do things like size set to 0 for strings
	private String parseArguments(String arguments, ResourceOperation operation, Device device, MQTTObject object,
			Map<String, MQTTObject> objects) {
		PropertyValue value = object.getProperties().getValue();
		String val = parseArg(arguments, operation, value, operation.getParameter());

		// if the written value is on a multiplexed handle, read the current
		// value and apply the mask first
		if (!value.mask().equals(BigInteger.ZERO)) {
			String result = driver.processCommand(device.getName(), "get", device.getAddressable(), object.getAttributes(), val);
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
		while (val.length() < value.size())
			val = "0" + val;
		return val;
	}

	private String parseArg(String arguments, ResourceOperation operation, PropertyValue value, String object) {
		// parse the argument string and get the "value" parameter
		JsonObject args;
		String val = null;
		JsonElement jElem = null;
		Boolean passed = true;

		// check for parameters from the command
		if (arguments != null) {
			args = new JsonParser().parse(arguments).getAsJsonObject();
			jElem = args.get(object);
		}

		// if the parameter is passed from the command, use it, otherwise treat
		// parameter as the default
		if (jElem == null || jElem.toString().equals("null")) {
			val = operation.getParameter();
			passed = false;
		} else {
			val = jElem.toString().replace("\"", "");
		}

		// if no value is specified by argument or parameter, take the object
		// default from the profile
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

	public void completeTransaction(String transactionId, String opId, List<ResponseObject> resp) {
		synchronized (transactions) {
			transactions.get(transactionId).finishOp(opId, resp);
			transactions.notifyAll();
		}
	}

}
