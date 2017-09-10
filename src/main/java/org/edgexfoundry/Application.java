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

package org.edgexfoundry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableDiscoveryClient
public class Application {
  public static ConfigurableApplicationContext ctx;

  private static boolean connected = true;

  public static void main(String[] args) {
    ctx = SpringApplication.run(Application.class, args);
    String welcomeMsg = ctx.getEnvironment().getProperty("app.open.msg");
    System.out.println(welcomeMsg);
    if (!connected) {
      System.out.println("Unable to connect to incoming message queue.  Shutting down!");
      ctx.close();
    }
  }

  public static void exit(int rc) {
    if (ctx != null) {
      SpringApplication.exit(ctx, () -> rc);
    } else {
      System.exit(rc);
    }
  }

  /*
   * Used by the Initializer to signal to shutdown the app if unable to connect to meta data
   *
   * @param connected
   */
  public static void setConnected(boolean c) {
    connected = c;
  }
}
