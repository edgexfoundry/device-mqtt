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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.Initializer;
import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.domain.MQTTDevice;
import org.edgexfoundry.exception.BadCommandRequestException;
import org.edgexfoundry.exception.DeviceLockedException;
import org.edgexfoundry.exception.ServiceLockedException;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommandHandler {
	
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(CommandHandler.class);
	
	@Autowired
	MQTTHandler MQTT;
	
	@Autowired
	DeviceStore devices;
	
	@Autowired
	Initializer init;

	public Map<String,String> getResponse(String deviceId, String cmd, String arguments) {
		if (init.isServiceLocked()) {
			logger.error("GET request cmd: " + cmd + " with device service locked on: " + deviceId);
			throw new ServiceLockedException();
		}
		if (devices.isDeviceLocked(deviceId)) {
			logger.error("GET request cmd: " + cmd + " with device locked on: " + deviceId);
			throw new DeviceLockedException(deviceId);
		}
		MQTTDevice device = devices.getMQTTDeviceById(deviceId);
		if (MQTT.commandExists(device, cmd)) {
			return MQTT.executeCommand(device, cmd, arguments);
		} else {
			logger.error("Command: " + cmd + " does not exist for device with id: " + deviceId);
			throw new BadCommandRequestException("Command: " + cmd + " does not exist for device with id: " + deviceId);
		}
	}

	public Map<String,String> getResponses(String cmd, String arguments) {
		Map<String,String> responses = new HashMap<String,String>();
		if (init.isServiceLocked()) {
			logger.error("GET request cmd: " + cmd + " with device service locked ");
			throw new ServiceLockedException();
		}
		for (String deviceId: devices.getDevices().entrySet().stream().map(d -> d.getValue().getId()).collect(Collectors.toList())) {
			if (devices.isDeviceLocked(deviceId)) {
				logger.error("GET request cmd: " + cmd + " with device locked on: " + deviceId);
				continue;
			}
			MQTTDevice device = devices.getMQTTDeviceById(deviceId);
			if (MQTT.commandExists(device, cmd))
				responses.putAll(MQTT.executeCommand(device, cmd, arguments));
		}
		return responses;
	}

}
