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
package org.edgexfoundry.controller;

import javax.servlet.http.HttpServletRequest;

import org.edgexfoundry.domain.meta.ActionType;
import org.edgexfoundry.domain.meta.CallbackAlert;
import org.edgexfoundry.exception.DeviceNotFoundException;
import org.edgexfoundry.handler.SchedulerCallbackHandler;
import org.edgexfoundry.handler.UpdateHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UpdateController {
	
	private static final String WATCHERPATH = "/watcher";
	private static final String DEVICEPATH = "/device";
	private static final String CALLBACKPATH = "/callback";

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(UpdateController.class);
	
	@Autowired
	UpdateHandler update;
	
	@Autowired
	SchedulerCallbackHandler scheduler;
	
	@RequestMapping(value = CALLBACKPATH)
	public void getCallback(HttpServletRequest request, @RequestBody(required=false) CallbackAlert data) {
		
		ActionType actionType = data.getType();
		String id = data.getId();
		String method = request.getMethod();
		
		if(ActionType.DEVICE.equals(actionType) && method.equals("POST"))
			addDevice(id);
		if(ActionType.DEVICE.equals(actionType) && method.equals("PUT"))
			updateDevice(id);
		if(ActionType.DEVICE.equals(actionType) && method.equals("DELETE"))
			deleteDevice(id);
		
		if(ActionType.PROFILE.equals(actionType) && method.equals("PUT"))
			updateProfile(id);

		if(ActionType.PROVISIONWATCHER.equals(actionType) && method.equals("POST"))
			addWatcher(id);
		if(ActionType.PROVISIONWATCHER.equals(actionType) && method.equals("PUT"))
			updateWatcher(id);
		if(ActionType.PROVISIONWATCHER.equals(actionType) && method.equals("DELETE"))
			deleteWatcher(id);
		
		if(ActionType.SCHEDULE.equals(actionType) && method.equals("POST"))
			scheduler.handlePOST(data);
		if(ActionType.SCHEDULE.equals(actionType) && method.equals("PUT"))
			scheduler.handlePUT(data);
		if(ActionType.SCHEDULE.equals(actionType) && method.equals("DELETE"))
			scheduler.handleDELETE(data);
		
		if(ActionType.SCHEDULEEVENT.equals(actionType) && method.equals("POST"))
			scheduler.handlePOST(data);
		if(ActionType.SCHEDULEEVENT.equals(actionType) && method.equals("PUT"))
			scheduler.handlePUT(data);
		if(ActionType.SCHEDULEEVENT.equals(actionType) && method.equals("DELETE"))
			scheduler.handleDELETE(data);

	}


	@RequestMapping(value = WATCHERPATH, method = RequestMethod.POST)
	public void addWatcher(@RequestBody String provisionWatcher) {

		logger.debug("Watcher Input:" + provisionWatcher);
		if (provisionWatcher != null) {
			if (update.addWatcher(provisionWatcher)) {
				logger.debug("New device watcher received to add devices with provision watcher id:" + provisionWatcher);
			} else {
				logger.error("Received add device provision watcher request without an id attached.");
				throw new DeviceNotFoundException("no device watcher id provided in request.");
			}
		}
	}
	
	
	@RequestMapping(value = WATCHERPATH, method = RequestMethod.PUT)
	public void updateWatcher(@RequestBody String provisionWatcher) {

		logger.debug("Watcher Input:" + provisionWatcher);
		if (provisionWatcher != null) {
			if (update.updateWatcher(provisionWatcher)) {
				logger.debug("Update device provision watcher with id:" + provisionWatcher);
			} else {
				logger.error("Received update device provision watcher request without an id attached.");
				throw new DeviceNotFoundException("no device watcher id provided in request.");
			}
		}
	}
	
	@RequestMapping(value = WATCHERPATH, method = RequestMethod.DELETE)
	public void deleteWatcher(@RequestBody String provisionWatcher) {

		logger.debug("Watcher Input:" + provisionWatcher);
		if (provisionWatcher != null) {
			if (update.removeWatcher(provisionWatcher)) {
				logger.debug("Remove device provision watcher with id:" + provisionWatcher);
			} else {
				logger.error("Received remove device provision watcher request without an id attached.");
				throw new DeviceNotFoundException("no device watcher id provided in request.");
			}
		}
	}
	
	@RequestMapping(value = DEVICEPATH, method = RequestMethod.POST)
	public void addDevice(@RequestBody String deviceId) {
		if (deviceId != null) {
			if (update.addDevice(deviceId)) {
				logger.debug("Added device.  Received add device request with id:" + deviceId);
			} else {
				logger.error("Received add device request without a device id attached.");
				throw new DeviceNotFoundException("no device id provided in request.");
			}
		}
	}
	
	@RequestMapping(value = DEVICEPATH, method = RequestMethod.PUT)
	public void updateDevice(@RequestBody String deviceId) {
		if (deviceId != null) {
			if (update.updateDevice(deviceId)) {
				logger.debug("Updated device. Received update device request with id:" + deviceId);
			} else {
				logger.error("Received update device request without a device id attached.");
				throw new DeviceNotFoundException("no device id provided in request.");
			}
		}
	}

	@RequestMapping(value = DEVICEPATH, method = RequestMethod.DELETE)
	public void deleteDevice(@RequestBody String deviceId) {

		if (deviceId != null) {
			if (update.deleteDevice(deviceId)) {
				logger.debug("Removing device. Received delete device request with id:" + deviceId);
			} else {
				logger.error("Received delete device request without a device id attached.");
				throw new DeviceNotFoundException("no device id provided in request.");
			}
		}
	}
	
	public void updateProfile(@RequestBody String profileId) {
		if (profileId != null) {
			if (update.updateProfile(profileId)) {
				logger.debug("Updated profile. Received update profile request with id:" + profileId);
			} else {
				logger.error("Received update profile request without a profile id attached.");
				throw new DeviceNotFoundException("no profile id provided in request.");
			}
		}
	}
}
