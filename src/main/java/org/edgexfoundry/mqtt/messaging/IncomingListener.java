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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.edgexfoundry.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IncomingListener implements MqttCallback {

  private static final Logger logger = Logger.getLogger(IncomingListener.class);
  private MqttClient client;

  @Value("${INCOMING_MQTT_BROKER_PROTO}")
  private String incomingMqttBrokerProtocol;
  @Value("${INCOMING_MQTT_BROKER}")
  private String incomingMqttBroker;
  @Value("${INCOMING_MQTT_BROKER_PORT}")
  private String incomingMqttBrokerPort;
  @Value("${INCOMING_MQTT_CLIENT_ID}")
  private String incomingMqttClientId;
  @Value("${INCOMING_MQTT_TOPIC}")
  private String incomingMqttTopic;
  @Value("${INCOMING_MQTT_QOS}")
  private int incomingMqttQos;
  @Value("${INCOMING_MQTT_USER}")
  private String incomingMqttUser;
  @Value("${INCOMING_MQTT_PASS}")
  private String incomingMqttPassword;
  @Value("${INCOMING_MQTT_KEEP_ALIVE}")
  private int incomingMqttKeepAlive;

  @Autowired
  private MessageProcessor processor;

  /**
   * Called after Spring creates the listener. It starts the listening for Mqtt messages off the
   * topic.
   *
   * @throws ClassNotFoundException
   */
  @PostConstruct
  public void init() throws ClassNotFoundException {
    startListening();
    // if incoming message queue client is not available, shut the service
    // down (no messages will ever hit the service under the circumstances)
    if (client == null) {
      Application.setConnected(false);
    }
  }

  /**
   * Called just before the bean is cleaned up on a shutdown by Spring. Specifically, it disconnects
   * and closes the Mqtt Client.
   *
   * @throws MqttException
   */
  @PreDestroy
  public void cleanup() throws MqttException {
    client.disconnect();
    client.close();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#connectionLost(java.lang. Throwable)
   */
  @Override
  public void connectionLost(Throwable cause) {
    logger.error("Incoming subscription connection lost:" + cause.getLocalizedMessage());
    // cause.printStackTrace();
    try {
      client.close();
    } catch (MqttException e) {
      logger.error("Unable to close the client.");
      e.printStackTrace();
    }
    startListening();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#deliveryComplete(org.eclipse.
   * paho.client.mqttv3.IMqttDeliveryToken)
   */
  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    logger.error("Incoming message delivery complete.");
  }

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang. String,
   * org.eclipse.paho.client.mqttv3.MqttMessage)
   *
   */
  @Override
  public void messageArrived(String topic, MqttMessage message) {
    logger.info("Incoming message arrived:  " + new String(message.getPayload()));
    if (incomingMqttTopic.equals(topic)) {
      processor.process(message.getPayload());
    }
  }

  private void startListening() {
    logger.debug("Starting listening for incoming traffic");
    try {
      String url =
          incomingMqttBrokerProtocol + "://" + incomingMqttBroker + ":" + incomingMqttBrokerPort;
      client = new MqttClient(url, incomingMqttClientId);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setUserName(incomingMqttUser);
      connOpts.setPassword(incomingMqttPassword.toCharArray());
      connOpts.setCleanSession(true);
      connOpts.setKeepAliveInterval(incomingMqttKeepAlive);
      logger.debug("Connecting to incoming message broker:  " + incomingMqttBroker);
      client.connect(connOpts);
      logger.debug("Connected to incoming message broker");
      client.setCallback(this);
      client.subscribe(incomingMqttTopic, incomingMqttQos);
    } catch (MqttException e) {
      logger.error("Unable to connect to incoming message queue.");
      e.printStackTrace();
      client = null;
    }
  }
}
