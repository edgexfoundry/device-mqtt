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

package org.edgexfoundry.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(ignoreUnknownFields = true, prefix = "default.scheduleEvent")
public class SimpleScheduleEvent {

  private String name;
  private String schedule;
  private String parameters;
  private String service;
  private String path;
  private String scheduler;

  public Integer getSize() {
    if (name == null) {
      return 0;
    }

    if ((name.split(",").length == 1) && name.equals("null")) {
      return 0;
    }

    return name.split(",").length;
  }

  public String[] getName() {
    return name.split(",");
  }

  public void setName(String name) {
    this.name = name;
  }

  public String[] getSchedule() {
    if (schedule == null) {
      return new String[getSize()];
    }

    return schedule.split(",");
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public String[] getParameters() {
    if (parameters == null) {
      return new String[getSize()];
    }

    return parameters.split(",");
  }

  public void setParameters(String parameters) {
    this.parameters = parameters;
  }

  public String[] getService() {
    if (service == null) {
      return new String[getSize()];
    }

    return service.split(",");
  }

  public void setService(String service) {
    this.service = service;
  }

  public String[] getPath() {
    if (path == null) {
      return new String[getSize()];
    }

    return path.split(",");
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String[] getScheduler() {
    if (scheduler == null) {
      return getService();
    }

    return scheduler.split(",");
  }

  public void setScheduler(String scheduler) {
    this.scheduler = scheduler;
  }
}
