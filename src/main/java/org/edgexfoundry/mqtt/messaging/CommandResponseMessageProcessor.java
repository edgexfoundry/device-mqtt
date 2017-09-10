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

package org.edgexfoundry.mqtt.messaging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class CommandResponseMessageProcessor {

  private static final String UUID_KEY = "uuid";
  private static final Logger logger = Logger.getLogger(CommandResponseMessageProcessor.class);
  private static final String NO_RESP = "none";
  private static final long SLEEP_TIME = 1000;

  @Value("${service.connect.retries}")
  private int retries;
  @Value("${service.connect.wait}")
  private long delay;

  private Map<String, String> responses = new ConcurrentHashMap<>();

  private JsonParser parser = new JsonParser();

  public void process(byte[] messagePayload) {
    String json = new String(messagePayload);
    if (json != null && json.length() > 0) {
      JsonObject jsonObject = parser.parse(json).getAsJsonObject();
      String uuid = extractCommandData(jsonObject, UUID_KEY);
      if (uuid != null) {
        responses.put(uuid, json);
        logger.debug("Response message for uuid: " + uuid + " stored for processing: " + json);
      } else {
        logger.error("No UUID found in the message.  Response message ignored.");
      }
    }
  }

  public String getResponse(String uuid) {
    addResponse(uuid);
    logger.debug("Response registered for uuid: " + uuid);
    try {
      while (NO_RESP.equals(responses.get(uuid))) {
        Thread.sleep(SLEEP_TIME);
      }
      logger.info("Matching response received for uuid:  " + uuid);
      String response = responses.get(uuid);
      responses.remove(uuid);
      return response;
    } catch (InterruptedException e) {
      throw new RuntimeException("Response loop interrupted.");
    }
  }

  private void addResponse(String uuid) {
    responses.put(uuid, NO_RESP);
  }

  // get the data out of the MQTT JSON message.
  private String extractCommandData(JsonObject jsonObject, String dataPart) {
    JsonElement element = jsonObject.get(dataPart);
    if (element != null) {
      return element.getAsString();
    } else {
      return null;
    }
  }
}
