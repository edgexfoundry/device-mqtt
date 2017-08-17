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

import org.edgexfoundry.controller.ScheduleClient;
import org.edgexfoundry.controller.ScheduleEventClient;
import org.edgexfoundry.domain.meta.ActionType;
import org.edgexfoundry.domain.meta.CallbackAlert;
import org.edgexfoundry.domain.meta.Schedule;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.edgexfoundry.scheduling.Scheduler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//Callbacks receive a JSON blob of the format '{"ActionType":"<action>", "id" : "<id>"} 
//e.g. '{"ActionType":"SCHEDULEEVENT","id":"583c70737cf6786707dd5663"}

@Component
public class SchedulerCallbackHandler {
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(SchedulerCallbackHandler.class);

	@Autowired
	private Scheduler scheduler;
	
	@Autowired	
	private ScheduleClient scheduleClient;

	@Autowired	
	private ScheduleEventClient scheduleEventClient;

	public SchedulerCallbackHandler() {
	}
	
	public boolean handlePUT(CallbackAlert data) {
		ActionType action = data.getType();
		switch(action) {
			case SCHEDULE:
				try {
					Schedule schedule = scheduleClient.schedule(data.getId());
					if(schedule != null) scheduler.updateScheduleContext(schedule);
				} catch (Exception e) {
					logger.error("failed to put schedule " + data.getId() + " " + e);
					return false;
				}
				break;
			case SCHEDULEEVENT:
				try {
					ScheduleEvent scheduleEvent = scheduleEventClient.scheduleEvent(data.getId());
					if(scheduleEvent != null) scheduler.updateScheduleEventInScheduleContext(scheduleEvent);
				} catch (Exception e) {
					logger.error("failed to put schedule event " + data.getId() + " " + e);
					return false;
				}
				break;
			default:
				break;
		}
		return true;
	}

	public boolean handlePOST(CallbackAlert data) {
		ActionType action = data.getType();
		switch(action) {
			case SCHEDULE:
				try {
					Schedule schedule = scheduleClient.schedule(data.getId());
					if(schedule != null) scheduler.createScheduleContext(schedule);
				} catch (Exception e) {
					logger.error("failed to post schedule " + data.getId() + " " + e);
					return false;
				}
				break;
			case SCHEDULEEVENT:
				try {
					ScheduleEvent scheduleEvent = scheduleEventClient.scheduleEvent(data.getId());
					if(scheduleEvent != null) scheduler.addScheduleEventToScheduleContext(scheduleEvent);
				} catch (Exception e) {
					logger.error("failed to post schedule event " + data.getId() + " " + e);
					return false;
				}
				break;
			default:
				break;
		}
		return true;
	}

	public boolean handleDELETE(CallbackAlert data) {
		ActionType action = data.getType();
		switch(action) {
			case SCHEDULE:
				try {
					scheduler.removeScheduleById(data.getId());
				} catch (Exception e) {
					logger.error("failed to delete schedule " + data.getId() + " " + e);
					return false;
				}
				break;
			case SCHEDULEEVENT:
				try {
					scheduler.removeScheduleEventById(data.getId());
				} catch (Exception e) {
					logger.error("failed to delete schedule " + data.getId() + " " + e);
					return false;
				}
				break;
			default:
				break;
		}
		return true;
	}
	
}
