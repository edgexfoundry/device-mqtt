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
package org.edgexfoundry.mqtt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;

import org.edgexfoundry.controller.AddressableClient;
import org.edgexfoundry.controller.DeviceClient;
import org.edgexfoundry.controller.DeviceProfileClient;
import org.edgexfoundry.controller.DeviceServiceClient;
import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.data.ObjectStore;
import org.edgexfoundry.data.ProfileStore;
import org.edgexfoundry.domain.CmdMsg;
import org.edgexfoundry.domain.MQTTAttribute;
import org.edgexfoundry.domain.MQTTDevice;
import org.edgexfoundry.domain.MQTTObject;
import org.edgexfoundry.domain.ScanList;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.AdminState;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.DeviceProfile;
import org.edgexfoundry.domain.meta.DeviceService;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.BadCommandRequestException;
import org.edgexfoundry.exception.CommandSendException;
import org.edgexfoundry.handler.MQTTHandler;
import org.edgexfoundry.mqtt.messaging.CommandResponseMessageProcessor;
import org.edgexfoundry.mqtt.messaging.OutgoingSender;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class MQTTDriver {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(MQTTDriver.class);
	private final static String IDENTIFIER_KEY = "name";
	private final static String GET_OP = "get";
	private final static String SET_OP = "set";
	private final static String NO_DATA = "no data";

	@Autowired
	private ProfileStore profiles;

	@Autowired
	private DeviceStore devices;

	@Autowired
	private ObjectStore objectCache;

	@Autowired
	private MQTTHandler handler;

	@Autowired
	private DeviceServiceClient dsClient;

	@Autowired
	private DeviceProfileClient profClient;

	@Autowired
	private DeviceClient devClient;

	@Autowired
	private AddressableClient addrClient;

	@Value("${service.name}")
	private String deviceServiceName;
	
	@Value("${provision.mqtt.device}")
	private boolean provision;

	@Value("${device.profile.name}")
	private String deviceProfileName;

	@Value("${device.name}")
	private String deviceName;

	@Value("${device.description}")
	private String deviceDescription;

	@Value("${device.lablels}")
	private String[] deviceLabels;

	@Value("${device.addressablename}")
	private String mqttAddressableName;

	@Value("${request.broker.proto}")
	private String mqttRequestBrokerProtocolString;
	
	private Protocol mqttRequestBrokerProtocol;

	@Value("${request.broker}")
	private String mqttRequestBroker;

	@Value("${request.broker.port}")
	private int mqttRequestBrokerPort;

	@Value("${request.client.id}")
	private String mqttRequestClientId;

	@Value("${request.topic}")
	private String mqttRequestTopic;

	@Value("${request.user}")
	private String mqttRequestUser;

	@Value("${request.pass}")
	private String mqttRequestPassword;

	private JsonParser parser = new JsonParser();

	private Gson gson = new Gson();

	private Map<String, OutgoingSender> sendors = new ConcurrentHashMap<>();

	@Autowired
	private CommandResponseMessageProcessor responseProcessor;

	@PreDestroy
	private void sendorCleanUp() {
		for (String deviceName : sendors.keySet()) {
			sendors.get(deviceName).closeClient();
		}
	}

	public ScanList discover() {
		// no scanning at this time. Static incoming topic is watched and no
		// others at this time.
		ScanList scan = new ScanList();
		return scan;
	}

	public void process(ResourceOperation operation, MQTTDevice device, MQTTObject object, String value,
			String transactionId, String opId) {
		String result = "";
		result = processCommand(device.getName(), operation.getOperation(), device.getAddressable(),
				object.getAttributes(), value);
		if (result == null || NO_DATA.equals(result)) // either a set with no
														// return value or a
														// null
			// result for some reason
			handler.completeTransaction(transactionId, opId, null);
		else {
			objectCache.put(device, operation, result);
			handler.completeTransaction(transactionId, opId, objectCache.getResponses(device, operation));
		}
	}

	public void processJson(String json, String transactionId, String opId) {
		if (json != null && json.length() > 0) {
			logger.debug("MQTT data message rec'd:  " + json);
			JsonObject jsonObject = parser.parse(json).getAsJsonObject();
			String deviceName = getDeviceName(jsonObject);
			if (deviceName != null) {
				MQTTDevice d = (MQTTDevice) devices.getDevice(deviceName);
				processValues(d, jsonObject);
				handler.completeTransaction(transactionId, opId, objectCache.getResponses(d, null));
			} else
				logger.info("No device with matching name/alias managed by this service.  MQTT message ignored.");
		}
	}

	public String processCommand(String deviceName, String operation, Addressable addressable, MQTTAttribute attribute,
			String value) {
		final String uuid;
		if (GET_OP.equals(operation))
			uuid = sendCommand(deviceName, addressable, attribute, operation);
		else
			uuid = sendCommand(deviceName, addressable, attribute, operation, value);
		try {
			return receive(uuid, attribute);
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Problem in response handling:  " + e.getMessage());
			e.printStackTrace();
			throw new BadCommandRequestException("Problem handling response" + e.getMessage());
		}
	}

	public void initialize() {
		if (provision) {
			logger.debug("Provisioning MQTT device...");
			Device device = null;
			// locate existing device
			device = getExistingDevice();
			if (device != null) {
				logger.info("Located existing MQTT Device (" + device.getName() + ") - no need to create a new one.");
			} else {
				// existing device does not exist - try to provision a new one
				device = getNewDevice();
			}
			try {
				if (devices.add(device))
					logger.debug("Default MQTT device provisioned successfully");
			} catch (Exception e) {
				logger.error("Default MQTT device not provisioned successfully");
				logger.error("Problem in creating default MQTT device:  " + e.getMessage());
				logger.debug(e.getStackTrace().toString());
			}
		} else
			logger.debug("Automatic MQTT Device provisioning not enabled.  Devices must be added manually");
	}

	public void disconnectDevice(Addressable address) {
	}

	private String receive(String uuid, MQTTAttribute attribute) throws InterruptedException, ExecutionException {
		ResponseTask task = new ResponseTask(uuid, responseProcessor);
		ExecutorService service = Executors.newSingleThreadExecutor();
		Future<String> result = service.submit(task);
		String json = result.get();

		if (json != null && json.length() > 0) {
			logger.debug("Response data message rec'd:  " + json);
			JsonObject jsonObject = parser.parse(json).getAsJsonObject();
			JsonElement element = jsonObject.get(attribute.getName());
			if (element != null){
				logger.debug("response data for element found");
				return element.getAsString();
			}
			else {
				logger.debug("response data contain no element data");
				return null;
			}
		} else {
			throw new BadCommandRequestException(
					"Response with uuid:  " + uuid + " did not contain expected attribute: " + attribute.getName());
		}
	}

	private void processValues(MQTTDevice d, JsonObject json) {
		Map<String, Map<String, List<ResourceOperation>>> resources = profiles.getCommands().get(d.getName());
		json.entrySet().stream().parallel().forEach(entry -> {
			String dataKey = entry.getKey().toLowerCase();
			if (!dataKey.equals(IDENTIFIER_KEY)) {
				Map<String, List<ResourceOperation>> resource = resources.get(dataKey);
				if (resource == null)
					logger.info("Incoming MQTT message contained unknown and ignored attribute: " + dataKey + " for: "
							+ d.getName());
				else {
					List<ResourceOperation> ops = resource.get("get");
					ResourceOperation op = ops.get(0);
					objectCache.put(d, op, entry.getValue().getAsString());
				}
			}
		});
	}

	private Device getExistingDevice() {
		try {
			return devClient.deviceForName(deviceName);
		} catch (javax.ws.rs.NotFoundException notFound) {
			return null;
		}
	}

	private Device getNewDevice() {
		DeviceProfile profile = getDeviceProfile();
		if (profile != null) {
			DeviceService service = getDeviceService();
			if (service != null) {
				Addressable addressable = getAddressable();
				if (addressable != null) {
					return getDevice(profile, service, addressable);
				} else {
					logger.error("Cannont create default MQTT device without an addressable");
				}
			} else {
				logger.error("Cannot create default MQTT device without the device service");
			}
		} else
			logger.error("Cannot create default MQTT device without a profile");
		return null;
	}

	private Device getDevice(DeviceProfile profile, DeviceService service, Addressable addressable) {
		Device device = new Device();
		device.setAdminState(AdminState.unlocked);
		device.setOperatingState(OperatingState.enabled);
		device.setDescription(deviceDescription);
		device.setLabels(deviceLabels);
		device.setName(deviceName);
		device.setProfile(profile);
		device.setService(service);
		device.setAddressable(addressable);
		devClient.add(device);
		return device;
	}

	private Addressable getAddressable() {
		try {
			return addrClient.addressableForName(mqttAddressableName);
		} catch (javax.ws.rs.NotFoundException notFound) {
			mqttRequestBrokerProtocol = Protocol.valueOf(mqttRequestBrokerProtocolString);
			Addressable addressable = new Addressable(mqttAddressableName, mqttRequestBrokerProtocol,
					mqttRequestBroker, mqttRequestBrokerPort, mqttRequestClientId,
					mqttRequestUser, mqttRequestPassword, mqttRequestTopic);
			addrClient.add(addressable);
			return addressable;
		}
	}

	private DeviceService getDeviceService() {
		try {
			return dsClient.deviceServiceForName(deviceServiceName);
		} catch (javax.ws.rs.NotFoundException notFound) {
			return null;
		}
	}

	private DeviceProfile getDeviceProfile() {
		// get existing or create new MQTT device profile
		DeviceProfile profile = null;
		try {
			profile = profClient.deviceProfileForName(deviceProfileName);
		} catch (javax.ws.rs.NotFoundException notFound) {
			try {
				Yaml yaml = new Yaml();
				InputStream input;
				input = new FileInputStream(new File(deviceProfileName));
				DeviceProfile deviceProfile = yaml.loadAs(input, DeviceProfile.class);
				profClient.add(deviceProfile);
				return deviceProfile;
			} catch (FileNotFoundException e) {
				logger.error("Device profile YAML not found.");
			}
		}
		return profile;
	}

	private String getDeviceName(JsonObject jsonObject) {
		JsonElement element = jsonObject.get(IDENTIFIER_KEY);
		if (element != null)
			return element.getAsString();
		else
			return null;
	}

	private String sendCommand(String deviceName, Addressable addressable, MQTTAttribute attribute, String operation) {
		return sendCommand(deviceName, addressable, attribute, operation, null);
	}

	private String sendCommand(String deviceName, Addressable addressable, MQTTAttribute attribute, String operation,
			String parameter) {
		OutgoingSender sendor = getSendor(deviceName, addressable);
		CmdMsg msg;
		if (SET_OP.equals(operation))
			msg = new CmdMsg(attribute.getName(), operation, parameter);
		else
			msg = new CmdMsg(attribute.getName(), operation);
		if (sendor.sendMessage(gson.toJson(msg).getBytes())) {
			logger.info(operation + " request for " + attribute.getName() + " sent to: " + addressable.getName());
			logger.debug("Outgoing message:  " + gson.toJson(msg));
			return msg.getUuid();
		} else {
			String errorMsg = "Problem sending command for attribute (" + attribute.getName()
					+ ") message to addressable:  " + addressable.getName();
			logger.error(errorMsg);
			throw new CommandSendException(errorMsg);
		}
	}

	private synchronized OutgoingSender getSendor(String deviceName, Addressable addressable) {
		if (sendors.containsKey(deviceName))
			return sendors.get(deviceName);
		else {
			String broker =  addressable.getProtocol().toString().toLowerCase() + "://" + addressable.getAddress() + ":" + addressable.getPort();// broker
			String clientId = addressable.getPublisher();// client id
			String user = addressable.getUser(); // user
			String password = addressable.getPassword();// password
			String topic = addressable.getTopic();// topic
			OutgoingSender sendor = new OutgoingSender(broker, clientId, user, password, topic);
			sendors.put(deviceName, sendor);
			return sendor;
		}
	}
}
