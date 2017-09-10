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

import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

import com.google.gson.Gson;

public class MqttAttribute {

  private static final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(MqttAttribute.class);

  // Replace these attributes with the MQTT
  // specific metadata needed by the MQTT Driver

  private String name;

  public MqttAttribute(Object attributes) {
    try {
      Gson gson = new Gson();
      String jsonString = gson.toJson(attributes);
      MqttAttribute thisObject = gson.fromJson(jsonString, this.getClass());

      this.setName(thisObject.getName());

    } catch (Exception e) {
      logger.error("Cannot Construct MqttAttribute: " + e.getMessage());
    }
  }


  public String getName() {
    return name;
  }


  public void setName(String name) {
    this.name = name;
  }

}

