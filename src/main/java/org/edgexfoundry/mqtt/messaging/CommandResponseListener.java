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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommandResponseListener implements MqttCallback {

  private static final Logger logger = Logger.getLogger(CommandResponseListener.class);
  private MqttClient client;

  @Value("${RESPONSE_MQTT_BROKER_PROTO}")
  private String cmdrespMqttBrokerProtocol;
  @Value("${RESPONSE_MQTT_BROKER}")
  private String cmdrespMqttBroker;
  @Value("${RESPONSE_MQTT_BROKER_PORT}")
  private String cmdrespMqttBrokerPort;
  @Value("${RESPONSE_MQTT_CLIENT_ID}")
  private String cmdrespMqttClientId;
  @Value("${RESPONSE_MQTT_TOPIC}")
  private String cmdrespMqttTopic;
  @Value("${RESPONSE_MQTT_QOS}")
  private int cmdrespMqttQos;
  @Value("${RESPONSE_MQTT_USER}")
  private String cmdrespMqttUser;
  @Value("${RESPONSE_MQTT_PASS}")
  private String cmdrespMqttPassword;
  @Value("${RESPONSE_MQTT_KEEP_ALIVE}")
  private int cmdrespMqttKeepAlive;

  @Autowired
  CommandResponseMessageProcessor processor;

  /**
   * Called after Spring creates the listener. It starts the listening for MQTT messages off the
   * topic.
   *
   * @throws ClassNotFoundException
   */
  @PostConstruct
  public void init() throws ClassNotFoundException {
    startListening();
  }

  /**
   * Called just before the bean is cleaned up on a shutdown by Spring. Specifically, it disconnects
   * and closes the MQTT Client.
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
    logger.error("Response subscription connection lost:" + cause.getLocalizedMessage());
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
  public void deliveryComplete(IMqttDeliveryToken arg0) {
    logger.debug("Response message delivery complete.");
  }

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang. String,
   * org.eclipse.paho.client.mqttv3.MqttMessage)
   *
   */
  @Override
  public void messageArrived(String topic, MqttMessage message) throws Exception {
    logger.debug("Response message arrived:  " + new String(message.getPayload()));
    if (cmdrespMqttTopic.equals(topic)) {
      processor.process(message.getPayload());
    }
  }

  private void startListening() {
    logger.debug("Starting listening for response traffic");
    try {
      String url =
          cmdrespMqttBrokerProtocol + "://" + cmdrespMqttBroker + ":" + cmdrespMqttBrokerPort;
      client = new MqttClient(url, cmdrespMqttClientId);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setUserName(cmdrespMqttUser);
      connOpts.setPassword(cmdrespMqttPassword.toCharArray());
      connOpts.setCleanSession(true);
      connOpts.setKeepAliveInterval(cmdrespMqttKeepAlive);
      logger.debug("Connecting to response message broker:  " + cmdrespMqttBroker);
      client.connect(connOpts);
      logger.debug("Connected to response message broker");
      client.setCallback(this);
      client.subscribe(cmdrespMqttTopic, cmdrespMqttQos);
    } catch (MqttException e) {
      logger.error("Unable to connect to response message queue.  "
          + "Unable to respond to command requests.");
      e.printStackTrace();
      client = null;
    }
  }
}
