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
@ConfigurationProperties(ignoreUnknownFields = true, prefix = "default.schedule")
public class SimpleSchedule {

  private String name;
  private String start;
  private String end;
  private String frequency;
  private String cron;
  private String runOnce;

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

  public String[] getStart() {
    if (start == null) {
      return new String[getSize()];
    }

    return start.split(",");
  }

  public void setStart(String start) {
    this.start = start;
  }

  public String[] getEnd() {
    if (end == null) {
      return new String[getSize()];
    }

    return end.split(",");
  }

  public void setEnd(String end) {
    this.end = end;
  }

  public String[] getFrequency() {
    if (frequency == null) {
      return new String[getSize()];
    }

    return frequency.split(",");
  }

  public void setFrequency(String frequency) {
    this.frequency = frequency;
  }

  public String[] getCron() {
    if (cron == null) {
      return new String[getSize()];
    }

    return cron.split(",");
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String[] getRunOnce() {
    if (runOnce == null) {
      return new String[getSize()];
    }

    return runOnce.split(",");
  }

  public void setRunOnce(String runOnce) {
    this.runOnce = runOnce;
  }
}
