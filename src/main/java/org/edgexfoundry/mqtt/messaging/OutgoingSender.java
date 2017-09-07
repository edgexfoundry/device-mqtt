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

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class OutgoingSender implements MqttCallback {

  private static final Logger logger = Logger.getLogger(OutgoingSender.class);
  private MqttClient client = null;

  // needed in order to get acknowledgement
  private static final int OUTGOING_MQTT_QOS = 0;
  private static final int OUTGOING_MQTT_KEEP_ALIVE = 3600;

  private String broker;
  private String clientId;
  private String user;
  private String password;
  private String topic;

  public OutgoingSender(String broker, String clientId, String user, String password,
      String topic) {
    super();
    this.broker = broker;
    this.clientId = clientId;
    this.user = user;
    this.password = password;
    this.topic = topic;
    this.connectClient();
  }

  public boolean sendMessage(byte[] messagePayload) {
    // connectClient();
    if (client != null) {
      try {
        MqttMessage message = new MqttMessage(messagePayload);
        message.setQos(OUTGOING_MQTT_QOS);
        message.setRetained(false);
        client.publish(topic, message);
        return true;
      } catch (Exception e) {
        logger.error(
            "Failed to send outbound message (unexpected issue): " + new String(messagePayload));
        logger.error(e.getLocalizedMessage());
        e.printStackTrace();
      }
    }
    return false;

  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  private void connectClient() {
    try {
      client = new MqttClient(broker, clientId);
      client.setCallback(this);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setUserName(user);
      connOpts.setPassword(password.toCharArray());
      connOpts.setCleanSession(true);
      connOpts.setKeepAliveInterval(OUTGOING_MQTT_KEEP_ALIVE);
      logger.debug("Connecting to broker:  " + broker);
      client.connect(connOpts);
      logger.debug("Connected");
    } catch (MqttException e) {
      logger.error("Failed to connect to MQTT client ( " + broker + "/" + clientId
          + ") for outbound messages");
      logger.error(e.getLocalizedMessage());
      e.printStackTrace();
    }
  }

  public void closeClient() {
    try {
      if (client != null) {
        client.disconnect();
        client.close();
      }
    } catch (MqttException e) {
      logger.error("Problems disconnecting and closing the client.");
      logger.error(e.getLocalizedMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void connectionLost(Throwable cause) {
    logger.error("Outgoing Sendor publisher connection lost:" + cause.getLocalizedMessage());
    try {
      client.close();
    } catch (MqttException e) {
      logger.error("Unable to close the client.");
      logger.error(e.getLocalizedMessage());
      e.printStackTrace();
    }
    connectClient();
  }

  @Override
  public void messageArrived(String topic, MqttMessage message) throws Exception {
    logger.error("Message received on Outgoing Sender which should not happen.  Payload:  "
        + message.getPayload().toString());
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    logger.debug("Message delivered successfully by Outgoing Sender.  Token:  " + token.toString());
  }
}
