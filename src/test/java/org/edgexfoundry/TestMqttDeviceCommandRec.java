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

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.edgexfoundry.mqtt.messaging.OutgoingSender;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 * Use this class to test receiving commands from the device service and responded back for get
 * commands. Use a REST client to send a command to the service like:
 * http://localhost:49982/api/v1/devices/{device id}>/message - use POST on this one with
 * {"message":"some text"} in body http://localhost:49982/api/v1/devices/<device id>/ping - use GET
 * http://localhost:49982/api/v1/devices/<device id>/randnum - use GET
 * 
 * If command micro service is running, the same can be performed through command to device service
 * like this http://localhost:48082/api/v1/device/<device id>/command/<command id>
 * 
 * Requires the Device Service, Command, Core Data, Metadata and Mongo to all be running
 *
 */
public class TestMqttDeviceCommandRec implements MqttCallback {

  private static String REQ_MQTT_BROKER = "m11.cloudmqtt.com";
  private static String REQ_MQTT_PROTOCOL = "tcp";
  private static int REQ_MQTT_BROKER_PORT = 15757;
  private static String REQ_MQTT_CLIENT_ID = "OutgoingCommandSubscriber";
  private static String REQ_MQTT_TOPIC = "CommandTopic";
  private static String REQ_MQTT_USER = "tobeprovided";
  private static String REQ_MQTT_PASS = "tobeprovided";

  private static String RESP_MQTT_BROKER = "m11.cloudmqtt.com";
  private static String RESP_MQTT_PROTOCOL = "tcp";
  private static int RESP_MQTT_BROKER_PORT = 15757;
  private static String RESP_MQTT_CLIENT_ID = "CommandResponsePublisher";
  private static String RESP_MQTT_TOPIC = "ResponseTopic";
  private static String RESP_MQTT_USER = "tobeprovided";
  private static String RESP_MQTT_PASS = "tobeprovided";

  private static final String CMD_KEY = "cmd";

  boolean msgRecd = false;

  MqttClient sampleClient;
  JsonParser parser = new JsonParser();

  Gson gson = new Gson();

  public static void main(String[] args) {
    new TestMqttDeviceCommandRec().doDemo();
  }

  public void doDemo() {
    try {
      String address = REQ_MQTT_PROTOCOL + "://" + REQ_MQTT_BROKER + ":" + REQ_MQTT_BROKER_PORT;
      sampleClient = new MqttClient(address, REQ_MQTT_CLIENT_ID);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setUserName(REQ_MQTT_USER);
      connOpts.setPassword(REQ_MQTT_PASS.toCharArray());
      connOpts.setCleanSession(true);
      connOpts.setKeepAliveInterval(30);
      System.out.println("Connecting to broker");
      sampleClient.connect(connOpts);
      System.out.println("Connected");
      sampleClient.setCallback(this);
      sampleClient.subscribe(REQ_MQTT_TOPIC, 0);
      while (true) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void connectionLost(Throwable cause) {
    System.out.println("oops connection lost + " + cause.getMessage());
  }

  @Override
  public void messageArrived(String topic, MqttMessage message) throws Exception {
    String payload = new String(message.getPayload());
    System.out.println("Red'c command:  " + payload);
    JsonObject jsonObject = parser.parse(payload).getAsJsonObject();
    String cmd = extractCommandData(jsonObject, CMD_KEY);
    switch (cmd) {
      case "ping":
        sendResponse(pingResponse(jsonObject));
        break;
      case "randnum":
        sendResponse(randResponse(jsonObject));
        break;
      default:
        sendResponse(payload);
    }
    msgRecd = true;
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    System.out.println("delivery complete");
  }

  private String pingResponse(JsonObject j) {
    j.addProperty("ping", "pong");
    return j.toString();
  }

  private String randResponse(JsonObject j) {
    j.addProperty("randnum", "42.0");
    return j.toString();
  }

  private void sendResponse(String returnPayload) {
    byte[] msg = returnPayload.getBytes();
    System.out.println("Sending response message");
    String address = RESP_MQTT_PROTOCOL + "://" + RESP_MQTT_BROKER + ":" + RESP_MQTT_BROKER_PORT;
    OutgoingSender sender = new OutgoingSender(address, RESP_MQTT_CLIENT_ID, RESP_MQTT_USER,
        RESP_MQTT_PASS, RESP_MQTT_TOPIC);
    sender.sendMessage(msg);
    System.out.println("sent:  " + returnPayload);
    sender.closeClient();
  }

  private String extractCommandData(JsonObject jsonObject, String dataPart) {
    JsonElement element = jsonObject.get(dataPart);
    if (element != null) {
      return element.getAsString();
    } else {
      return null;
    }
  }

}
