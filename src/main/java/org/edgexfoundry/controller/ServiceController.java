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

package org.edgexfoundry.controller;

import org.edgexfoundry.data.ObjectStore;
import org.edgexfoundry.handler.MqttHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/")
public class ServiceController {

  private static final EdgeXLogger logger =
      EdgeXLoggerFactory.getEdgeXLogger(ServiceController.class);

  @Autowired
  ObjectStore objects;

  @Autowired
  MqttHandler handler;

  @RequestMapping(path = "/debug/transformData/{transformData}", method = RequestMethod.GET)
  public @ResponseBody String setTransformData(@PathVariable Boolean transformData) {
    logger.info("Setting transform data to: " + transformData);
    objects.setTransformData(transformData);
    return "Set transform data to: " + transformData;
  }

  @RequestMapping(path = "/discovery", method = RequestMethod.POST)
  public @ResponseBody String doDiscovery() {
    logger.info("Running discovery request");
    handler.scan();
    return "Running discovery";
  }
}
