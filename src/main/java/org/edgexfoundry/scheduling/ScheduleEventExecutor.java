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
package org.edgexfoundry.scheduling;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.springframework.beans.factory.annotation.Autowired;

public class ScheduleEventExecutor {

	private static final Logger logger = Logger.getLogger(ScheduleEventExecutor.class);

	@Autowired
	ScheduleEventHTTPExecutor httpExecutor;
	
	ScheduleEventExecutor() {
		httpExecutor = new ScheduleEventHTTPExecutor();
	}

	public void execute(LinkedHashMap<String, ScheduleEvent> events) {
		if(events == null) {
			logger.error("schedule event list is null");
		} else {
			logger.debug("schedule event list contains " + events.size() + " events");
			for(Map.Entry<String, ScheduleEvent> entry : events.entrySet()) {
				execute(entry.getValue());
			}
		}
	}

	public void execute(ScheduleEvent event) {
		// TODO: Refactor - when Addressable is refactored, create a factory 
		// to handle the different execution types, MQTT, HTTP, TCP, etc.
		logger.info("executing event " + event.getId() + " '" + event.getName() + "'");
		httpExecutor.execute(event);
	}
}
