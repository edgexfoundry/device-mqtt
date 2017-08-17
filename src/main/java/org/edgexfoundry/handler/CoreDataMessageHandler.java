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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.edgexfoundry.controller.DeviceClient;
import org.edgexfoundry.controller.EventClient;
import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.data.ProfileStore;
import org.edgexfoundry.domain.MQTTObject;
import org.edgexfoundry.domain.ResponseObject;
import org.edgexfoundry.domain.common.ValueDescriptor;
import org.edgexfoundry.domain.core.Event;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Component
public class CoreDataMessageHandler {

	
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(CoreDataMessageHandler.class);

	@Value("${service.connect.retries}")
	private int retries;
	@Value("${service.connect.wait}")
	private long delay;

	@Autowired
	private DeviceClient deviceClient;
	
	@Autowired
	private EventClient eventClient;
	
	@Autowired
	private DeviceStore devices;
	
	@Autowired
	private ProfileStore profiles;

	private Reading buildReading(String key, String value, ValueDescriptor descriptor) {
		Reading reading = new Reading();
		reading.setName(descriptor.getName());
		reading.setValue(value);
		return reading;
	}

	private Event buildEvent(String deviceName, List<Reading> readings) {
		Event event = new Event(deviceName);
		event.setReadings(readings);
		return event;
	}

	private boolean sendEvent(Event event, int attempt) {
		if (retries == 0 || attempt < retries) {
			if (event != null) {
				try {
					eventClient.add(event);
					return true;
				} catch (Exception e) { // something happened trying to send to
										// core data - likely that the service
										// is down.
					logger.debug("Problem sending event for " + event.getDevice()
							+ " to core data.  Retrying (attempt " + (attempt + 1) + ")...");
					try {
						Thread.sleep(delay);
					} catch (InterruptedException interrupt) {
						logger.debug("Event send delay interrupted");
						interrupt.printStackTrace();
					}
					return sendEvent(event, ++attempt);
				}
			}
		}
		return false;
	}

	private void updateLastConnected(String deviceName) {
		Device device = devices.getDevice(deviceName);
		if (device != null) {
			deviceClient.updateLastConnected(device.getId(), Calendar.getInstance().getTimeInMillis());
		} else {
			logger.debug("No device found for device name: " + deviceName + ". Could not update last connected time");
		}
	}

	public List<ResponseObject> sendCoreData(String deviceName, JsonObject jsonObject, Map<String, MQTTObject> objects) {
		try{
		
			if (objects != null) {
	
				List<Reading> readings = new ArrayList<>();
				List<ResponseObject> resps = new ArrayList<>();
				Set<Entry<String, JsonElement>> keys = jsonObject.entrySet();
				logger.debug("jsonObject: " + jsonObject);
				for (Entry<String, JsonElement> entry : keys) {
					if (jsonObject.has(entry.getKey())) {						
						ValueDescriptor descriptor = profiles.getValueDescriptors().stream().filter(d -> d.getName().equals(entry.getKey())).findAny().orElse(null);

						Reading reading = buildReading(entry.getKey(), entry.getValue().getAsString(),
								descriptor);
						readings.add(reading);
						
						ResponseObject resp = new ResponseObject(descriptor.getName(), entry.getValue().getAsString());						
						resps.add(resp);
					}
				}
				boolean success = sendEvent(buildEvent(deviceName, readings), 0);
				if (success) {
					updateLastConnected(deviceName);
					return resps;
				}
				else {
					if (devices.getDevice(deviceName).getOperatingState().equals(OperatingState.enabled))
						devices.setDeviceOpState(deviceName, OperatingState.disabled);
					logger.error("Could not send event to core data for " + deviceName + ".  Check core data service");
				}
			} else
				logger.debug("No profile object found for the device " + deviceName + ".  MQTT message ignored.");
		}catch(Exception e){
			logger.error("Cannot push the readings to Coredata " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
}
