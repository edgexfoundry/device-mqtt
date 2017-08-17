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

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import javax.ws.rs.ClientErrorException;

import org.edgexfoundry.controller.AddressableClient;
import org.edgexfoundry.controller.DeviceServiceClient;
import org.edgexfoundry.controller.ScheduleClient;
import org.edgexfoundry.controller.ScheduleClientImpl;
import org.edgexfoundry.controller.ScheduleEventClient;
import org.edgexfoundry.controller.ScheduleEventClientImpl;
import org.edgexfoundry.domain.SimpleSchedule;
import org.edgexfoundry.domain.SimpleScheduleEvent;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.Schedule;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class Scheduler
{
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(Scheduler.class);
	
	// Client to fetch schedule events
	@Autowired
	private ScheduleEventClient scheduleEventClient;

	// Client to fetch schedules
	@Autowired
	private ScheduleClient scheduleClient;

	// Schedule event executor to execute schedule events
	private ScheduleEventExecutor scheduleEventExecutor;
	
	@Autowired
	private DeviceServiceClient serviceClient;
	
	@Autowired
	private AddressableClient addressableClient;
	
	@Autowired
	private SimpleSchedule defaultSchedules;
	
	@Autowired
	private SimpleScheduleEvent defaultScheduleEvents;

	// Schedule id -> Schedule Context Mapping
	// used to find the schedule context given a schedule, e.g. update/delete this schedule
	private HashMap<String, ScheduleContext> scheduleIdToScheduleContextMap;

	// Schedule Event id -> Schedule Id
	// used to find the schedule context (via schedule id) given a schedule event, e.g. update/delete this schedule event
	private HashMap<String, String> scheduleEventIdToScheduleIdMap;

	// the scheduleContextQueue is prioritized based upon the next execution time of each schedule
	private PriorityQueue<ScheduleContext> scheduleContextQueue = new PriorityQueue<ScheduleContext>(
			new Comparator<ScheduleContext>()
			{
				@Override
				public int compare(ScheduleContext unit1, ScheduleContext unit2)
				{
					int result = Long.compare(unit1.getNextTime().toEpochSecond(), unit2.getNextTime().toEpochSecond());
					// if the ticks are equivalent just dequeue the lhs
					result = result != 0 ? result : -1;
					return result;
				}
			});
	
	public Scheduler() {
		scheduleEventExecutor = new ScheduleEventExecutor();
		scheduleEventClient = new ScheduleEventClientImpl();
		scheduleClient = new ScheduleClientImpl();
		scheduleIdToScheduleContextMap = new HashMap<String, ScheduleContext>();
		scheduleEventIdToScheduleIdMap = new HashMap<String, String>();
	}

	@Scheduled(fixedRateString = "${schedule.interval}")
	public void schedule()
	{
		synchronized (scheduleContextQueue)
		{
			// Instant is in epoch time
			Instant nowInstant = Instant.now();
			long nowEpoch = nowInstant.getEpochSecond();

			// logger.debug("tick " + nowInstant.toString());

			while (scheduleContextQueue.peek() != null && scheduleContextQueue.peek().getNextTime().toEpochSecond() <= nowEpoch)
			{
				try {
					// pop the schedule context off the queue
					ScheduleContext scheduleContext = scheduleContextQueue.remove();

					logger.info("executing schedule " + scheduleContext.getInfo() + " at " + scheduleContext.getNextTime());
					
					// run the events for the schedule
					scheduleEventExecutor.execute(scheduleContext.getScheduleEvents());

					// update the context 
					scheduleContext.updateNextTime();
					scheduleContext.updateIterations();

					// if the schedule is not complete, enqueue it.
					if(scheduleContext.isComplete()) {
						logger.info("schedule " + scheduleContext.getInfo() + " is complete." + scheduleContext.toString());
					} else {
						logger.debug("queueing schedule " + scheduleContext.getInfo());
						scheduleContextQueue.add(scheduleContext);
					}
				} catch (Exception e) {
					logger.error("exception while scheduling schedule contects" + e);
				}
			}
		}
	}

	public void createScheduleContext(Schedule schedule)
	{
		synchronized (scheduleContextQueue)
		{
			if(scheduleIdToScheduleContextMap.containsKey(schedule.getId())) {
				// not intended to be an error
				logger.info("schedule context " + schedule.getId() + " '" + schedule.getName() + "' already exists.");
			} else {
				// build a new schedule context
				ScheduleContext scheduleContext = new ScheduleContext(schedule);

				// store a mapping of schedule id to schedule context
				scheduleIdToScheduleContextMap.put(schedule.getId(), scheduleContext);

				// enqueue the context
				scheduleContextQueue.add(scheduleContext);
				logger.info("created schedule context " + scheduleContext.getInfo()
						+ " initial start time " + scheduleContext.getNextTime().toString());
			}
		}
	}

	public void updateScheduleContext(Schedule schedule)
	{
		synchronized (scheduleContextQueue)
		{
			if(!scheduleIdToScheduleContextMap.containsKey(schedule.getId())) {
				// not intended to be an error
				logger.error("failed to find schedule for " + schedule.getId() + " " + schedule.getName());
			} else {
				// remove the schedule context from the queue
				scheduleContextQueue.remove(scheduleIdToScheduleContextMap.get(schedule.getId()));

				// update the schedule
				scheduleIdToScheduleContextMap.get(schedule.getId()).reset(schedule);
				
				// enqueue the context
				ScheduleContext scheduleContext = scheduleIdToScheduleContextMap.get(schedule.getId());
				scheduleContextQueue.add(scheduleContext);
				logger.info("updated schedule " + scheduleContext.getInfo()
						+ " initial start time " + scheduleContext.getNextTime().toString());
			}
		}
	}

	public void removeScheduleById(String id)
	{
		synchronized (scheduleContextQueue)
		{
			if(!scheduleIdToScheduleContextMap.containsKey(id)) {
				logger.error("schedule " + id + " not found.");
			} else {
				// look up the schedule context
				ScheduleContext sc = scheduleIdToScheduleContextMap.get(id);
				
				// remove all event id to schedule id mappings
				for(Map.Entry<String, ScheduleEvent> entry : sc.getScheduleEvents().entrySet()) {
					scheduleEventIdToScheduleIdMap.remove(entry.getValue().getId());
				}
				
				// remove the schedule context from the queue
				scheduleContextQueue.remove(sc);

				// remove the schedule context from the map (which contains schedule events)
				scheduleIdToScheduleContextMap.remove(id);
				
				logger.info("removed schedule " + id );
			}
		}
	}

	public void addScheduleEventToScheduleContext(ScheduleEvent scheduleEvent)
	{
		synchronized (scheduleContextQueue)
		{
			// get the schedule for the event
			Schedule schedule = scheduleClient.scheduleForName(scheduleEvent.getSchedule());
			if(schedule == null) {
				logger.error("failed to add schedule event " + scheduleEvent.getId() + " '" + scheduleEvent.getName() + "' " + 
						"schedule '" + scheduleEvent.getSchedule() + "' not found");
			} else {
				// ensure a schedule context exists
				createScheduleContext(schedule);
				
				// add the schedule event to the context
				scheduleIdToScheduleContextMap.get(schedule.getId()).addScheduleEvent(scheduleEvent);

				// add to the schedule event id to schedule id map
				scheduleEventIdToScheduleIdMap.put(scheduleEvent.getId(), schedule.getId());
			}
		}
	}

	public void updateScheduleEventInScheduleContext(ScheduleEvent scheduleEvent)
	{
		synchronized (scheduleContextQueue)
		{
			// get the schedule for the event
			String scheduleId = scheduleEventIdToScheduleIdMap.get(scheduleEvent.getId());
			if(scheduleId == null) {
				logger.error("failed to update schedule event " + scheduleEvent.getName() + 
						" current schedule " + scheduleEvent.getId() + " not found");
			} else {
				Schedule schedule = scheduleClient.scheduleForName(scheduleEvent.getSchedule());
				if(schedule == null) {
					logger.error("failed to update schedule event " + scheduleEvent.getName() + 
							" schedule " + scheduleEvent.getSchedule() + " not found");
				} else {
					// see if the event switched schedules
					if(scheduleId != schedule.getId()) {
						if(!scheduleIdToScheduleContextMap.containsKey(scheduleId)) {
							logger.error("failed to switch schedule event " + scheduleEvent.getId() + ", schedule " + scheduleId + " not found");
						} else {
							// remove the schedule event from the old schedule
							removeScheduleEventById(scheduleEvent.getId());
							// add the schedule event to the new schedule
							addScheduleEventToScheduleContext(scheduleEvent);
						}
					} else {
						// update the schedule event in place
						if(!scheduleIdToScheduleContextMap.containsKey(schedule.getId())) {
							logger.error("failed to update schedule event " + scheduleEvent.getId() + ", schedule " + schedule.getId() + " not found");
						} else {
							// update the schedule event in the context
							scheduleIdToScheduleContextMap.get(schedule.getId()).updateScheduleEvent(scheduleEvent);
						}
					}
				}
			}
		}
	}

	public void removeScheduleEventById(String id)
	{
		synchronized (scheduleContextQueue)
		{
			String scheduleId;
			scheduleId = scheduleEventIdToScheduleIdMap.get(id);
			if(scheduleId == null) {
				// check for schedule event id to schedule id mapping
				logger.error("failed to remove schedule event, schedule event " + id + " not found");
			} else {
				// check for schedule event id to schedule context mapping
				if(!scheduleIdToScheduleContextMap.containsKey(scheduleId)) {
					logger.error("failed to remove schedule event, schedule " + scheduleId + " not found");
				} else {
					// remove the schedule event from the schedule context
					scheduleIdToScheduleContextMap.get(scheduleId).removeScheduleEventById(id);
					
					// if there are no more events for the schedule remove the schedule context
					if(scheduleIdToScheduleContextMap.get(scheduleId).getScheduleEvents().isEmpty()) {
						logger.info("schedule " + scheduleId + " event list is empty, removing.");
						removeScheduleById(scheduleId);
					}
				}
			}
		}
	}
	
	public void reset()
	{
		synchronized (scheduleContextQueue)
		{
			// TODO: implement reset of all schedules due to events such as clock change
			logger.info("resetting all schedules");
		}
	}

	// Scheduler implementation of initialize
	public boolean initialize(String serviceName) {
		boolean loaded = true;

		addDefaultSchedules();
		addDefaultScheduleEvents();
		logger.info("loading schedules");
		synchronized (scheduleContextQueue)
		{
			// get all the schedule events for this service
			List<ScheduleEvent> scheduleEventList = null;
			try {
				scheduleEventList = scheduleEventClient.scheduleEventsForServiceByName(serviceName);
				for(ScheduleEvent se : scheduleEventList) addScheduleEventToScheduleContext(se);
			} catch (Exception e) {
				logger.error("failed to load schedule events for service " + serviceName + " " + e);
				e.printStackTrace();
				loaded = false;
			}

		}
		logger.info("loaded schedules");
		return loaded;
	}
	
	public void addDefaultSchedules() {		
		
		for (int i = 0; i < defaultSchedules.getSize(); i++) {
			String name = defaultSchedules.getName()[i];
			String start = defaultSchedules.getStart()[i];
			String end = defaultSchedules.getEnd()[i];
			String frequency = defaultSchedules.getFrequency()[i];
			String cron = defaultSchedules.getCron()[i];
			Boolean runOnce = Boolean.valueOf(defaultSchedules.getRunOnce()[i]);
			
			Schedule schedule = new Schedule(name, start, end, frequency, cron, runOnce);
				
			try {
				scheduleClient.add(schedule);
			} catch (ClientErrorException e) {
				// Ignore if the schedule is already present
				// schedule.setId(scheduleClient.scheduleForName(name).getId());
				// scheduleClient.update(schedule);
			}
		}
		
	}
		
	public void addDefaultScheduleEvents() {
		for (int i = 0; i < defaultScheduleEvents.getSize(); i++) {	
			String name = defaultScheduleEvents.getName()[i];
			String schedule = defaultScheduleEvents.getSchedule()[i];
			String parameters = defaultScheduleEvents.getParameters()[i];
			String service = defaultScheduleEvents.getService()[i];
			String path = defaultScheduleEvents.getPath()[i];
			String scheduler = defaultScheduleEvents.getScheduler()[i];
			
			Addressable serviceAddressable = serviceClient.deviceServiceForName(service).getAddressable();
			
			Addressable addressable = serviceAddressable;
			addressable.setName("Schedule-" + name);
			addressable.setPath(path);
			
			addressable.setId(null);
			
			try {
				addressable.setId(addressableClient.add(addressable));
			} catch (ClientErrorException e) {
				// Ignore if the addressable is already present
				// addressable.setId(addressableClient.addressableForName(addressable.getName()).getId());
				// addressableClient.update(addressable);
			}
			
			ScheduleEvent scheduleEvent = new ScheduleEvent(name, addressable, parameters, schedule, scheduler);
			
			try {
				scheduleEventClient.add(scheduleEvent);
			} catch (ClientErrorException e) {
				// Ignore if the event is already present
				// scheduleEvent.setId(scheduleEventClient.scheduleEventForName(name).getId());
				// scheduleEventClient.update(scheduleEvent);
			}
		}
		
	}

}
