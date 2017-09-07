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

package org.edgexfoundry.scheduling;

import javax.ws.rs.ClientErrorException;

import org.edgexfoundry.controller.AddressableClient;
import org.edgexfoundry.controller.DeviceServiceClient;
import org.edgexfoundry.controller.ScheduleClient;
import org.edgexfoundry.controller.ScheduleEventClient;
import org.edgexfoundry.domain.SimpleSchedule;
import org.edgexfoundry.domain.SimpleScheduleEvent;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.Schedule;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// TODO: Refactor - Change this to use Quartz or better scheduling mechanism
// TODO: handle clock update

@Component
public class Scheduler {
  private static final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(Scheduler.class);

  // Client to fetch schedule events
  @Autowired
  private ScheduleEventClient scheduleEventClient;

  // Client to fetch schedules
  @Autowired
  private ScheduleClient scheduleClient;


  @Autowired
  private DeviceServiceClient serviceClient;

  @Autowired
  private AddressableClient addressableClient;

  @Autowired
  private SimpleSchedule defaultSchedules;

  @Autowired
  private SimpleScheduleEvent defaultScheduleEvents;


  // Scheduler implementation of initialize
  public boolean initialize(String serviceName) {
    boolean loaded = true;

    addDefaultSchedules();
    addDefaultScheduleEvents();
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
      String path = defaultScheduleEvents.getPath()[i];
      String service = defaultScheduleEvents.getService()[i];

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

      String parameters = defaultScheduleEvents.getParameters()[i];
      String schedule = defaultScheduleEvents.getSchedule()[i];
      String scheduler = defaultScheduleEvents.getScheduler()[i];

      ScheduleEvent scheduleEvent =
          new ScheduleEvent(name, addressable, parameters, schedule, scheduler);

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
