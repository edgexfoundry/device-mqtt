/*******************************************************************************
 * Copyright 2017 Dell Inc.
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

package org.edgexfoundry;

import org.edgexfoundry.mqtt.messaging.OutgoingSender;

/*
 * Use this class to generate random numbers and send them into the device service as if a sensor
 * was sending the data. Requires the Device Service along with Mongo, Core Data, and Metadata to be
 * running
 *
 */
public class TestMqttDeviceSendor {

  private static String MQTT_BROKER = "m11.cloudmqtt.com";
  private static String MQTT_PROTOCOL = "tcp";
  private static int MQTT_BROKER_PORT = 12439;
  private static String MQTT_CLIENT_ID = "IncomingDataPublisher";
  private static String MQTT_TOPIC = "DataTopic";
  private static String MQTT_USER = "tobeprovided";
  private static String MQTT_PASS = "tobeprovided";
  private static String MSG = "{\"name\":\"TestMQTTDevice\",\"randnum\":\"";
  private static int MSG_LAG = 15; // time in seconds
  private static double MAX = 10000;

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Test MQTT Device Starting");
    String address = MQTT_PROTOCOL + "://" + MQTT_BROKER + ":" + MQTT_BROKER_PORT;
    double random;
    OutgoingSender sender =
        new OutgoingSender(address, MQTT_CLIENT_ID, MQTT_USER, MQTT_PASS, MQTT_TOPIC);
    while (true) {
      random = Math.random() * MAX;
      String msg = MSG + random + "\"}";
      sender.sendMessage(msg.getBytes());
      System.out.println("sent:  " + random);
      Thread.sleep(1000 * MSG_LAG);
    }
  }

}
