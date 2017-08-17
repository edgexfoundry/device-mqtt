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
package org.edgexfoundry.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.domain.MQTTDevice;
import org.edgexfoundry.domain.MQTTObject;
import org.edgexfoundry.domain.ResponseObject;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.DeviceNotFoundException;
import org.edgexfoundry.handler.CoreDataMessageHandler;
import org.edgexfoundry.mqtt.ObjectTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;

@Component
public class ObjectStore {
	
	//private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ObjectStore.class);

	@Value("${data.transform:true}")
	private Boolean transformData;
	
	@Autowired
	private ProfileStore profiles;
	
	@Autowired
	private ObjectTransform transform;
	
	@Autowired
	private CoreDataMessageHandler processor;
	
	@Value("${data.cache.size:1}")
	private int CACHE_SIZE;
	
	private Map<String,Map<String,List<String>>> objectCache = new HashMap<>();
	
	private Map<String,Map<String,List<ResponseObject>>> responseCache = new HashMap<>();
	
	public Boolean getTransformData() {
		return transformData;
	}
	
	public void setTransformData(Boolean transform) {
		transformData = transform;
	}

	public void put(MQTTDevice device, ResourceOperation operation, String value) {
		if (value == null || value.equals("") || value.equals("{}"))
			return;
		
		List<MQTTObject> objectsList = createObjectsList(operation, device);
		
		String deviceId = device.getId();
		JsonObject jsonObject = new JsonObject();
		
		for (MQTTObject obj: objectsList) {
			String objectName = obj.getName();
			String result = transformResult(value, obj, device);
			jsonObject.addProperty(obj.getName(),result);
			
			synchronized(objectCache) {
				if (objectCache.get(deviceId) == null)
					objectCache.put(deviceId, new HashMap<String,List<String>>());
				if (objectCache.get(deviceId).get(objectName) == null)
					objectCache.get(deviceId).put(objectName, new ArrayList<String>());
				objectCache.get(deviceId).get(objectName).add(0, result);
				if (objectCache.get(deviceId).get(objectName).size() == CACHE_SIZE)
					objectCache.get(deviceId).get(objectName).remove(CACHE_SIZE-1);
			}
		}
		
		String operationId = objectsList.stream().map(o -> o.getName()).collect(Collectors.toList()).toString();
		
		List<ResponseObject> resps = processor.sendCoreData(device.getName(), jsonObject, profiles.getObjects().get(device.getName()));
		synchronized(responseCache) {
			if (responseCache.get(deviceId) == null)
				responseCache.put(deviceId, new HashMap<String,List<ResponseObject>>());
			responseCache.get(deviceId).put(operationId,resps);
		}
	}
	
	private List<MQTTObject> createObjectsList(ResourceOperation operation, Device device) {
		Map<String, MQTTObject> objects = profiles.getObjects().get(device.getName());
		List<MQTTObject> objectsList = new ArrayList<MQTTObject>();
		if (operation != null && objects != null) {
			MQTTObject object = objects.get(operation.getObject());
			object.setName(operation.getParameter());
			objectsList.add(object);
			
			if(operation.getSecondary() != null){
				for (String secondary: operation.getSecondary())
					objectsList.add(objects.get(secondary));
			}
		}
		
		return objectsList;
	}

	private String transformResult(String result, MQTTObject object, MQTTDevice device) {
		
		PropertyValue propValue = object.getProperties().getValue();
		
		String transformResult = transform.transform(propValue, result);
		
		// if there is an assertion set for the object on a get command, test it
		// if it fails the assertion, pass error to core services (disable device?)
		if (propValue.getAssertion() != null)
			if(!transformResult.equals(propValue.getAssertion().toString())) {
				device.setOperatingState(OperatingState.disabled);
				return "Assertion failed with value: " + transformResult;
			}
		return transformResult;
	}

	public String get(String deviceId, String object) {
		return get(deviceId, object, 1).get(0);
	}

	private List<String> get(String deviceId, String object, int i) {
		if (objectCache.get(deviceId) == null 
				|| objectCache.get(deviceId).get(object) == null 
				|| objectCache.get(deviceId).get(object).size() < i)
			return null;
		return objectCache.get(deviceId).get(object).subList(0, i);
	}

	public JsonObject get(MQTTDevice device, ResourceOperation operation) {
		JsonObject jsonObject = new JsonObject();
		List<MQTTObject> objectsList = createObjectsList(operation, device);
		for (MQTTObject obj: objectsList) {
			String objectName = obj.getName();
			jsonObject.addProperty(objectName, get(device.getId(),objectName));
		}
		return jsonObject;
	}
	
	public List<ResponseObject> getResponses(MQTTDevice device, ResourceOperation operation) {
		String deviceId = device.getId();
		List<MQTTObject> objectsList = createObjectsList(operation, device);
		if (objectsList == null)
			throw new DeviceNotFoundException("Device: " + deviceId + " failed to respond to command " + operation.getObject());
		String operationId = objectsList.stream().map(o -> o.getName()).collect(Collectors.toList()).toString();
		return responseCache.get(deviceId).get(operationId);
	}
	
}
