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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(ignoreUnknownFields = true, prefix = "default.watcher")
public class SimpleWatcher {

  private String name;
  private String profile;
  private String service;

  // TODO 8: [Optional] For discovery enabled device services:
  // Add, delete, or replace the existing identifiers with protocol specific fields
  // Sample here is for BLE. Modify the default watchers in watcher.properties as required
  // If this is modified, must also add setters for fields, then update the following methods:
  // getIdentifier(), getIdentifiers(), toString()
  private String nameIdentifiers;
  private String macIdentifiers;

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

  public void setProfile(String profile) {
    this.profile = profile;
  }

  public void setService(String service) {
    this.service = service;
  }

  public Map<String, String[]> getIdentifier() {
    Map<String, String[]> ident = new HashMap<String, String[]>();

    if (nameIdentifiers != null) {
      ident.put("name", nameIdentifiers.split(","));
    }

    if (macIdentifiers != null) {
      ident.put("address", macIdentifiers.split(","));
    }

    return ident;
  }

  public void setNameIdentifiers(String nameIdentifiers) {
    this.nameIdentifiers = nameIdentifiers;
  }

  public void setMacIdentifiers(String macIdentifiers) {
    this.macIdentifiers = macIdentifiers;
  }

  public List<Map<String, String>> getIdentifiers() {
    Integer len = this.name.split(",").length;
    ArrayList<Map<String, String>> identifyList = new ArrayList<Map<String, String>>();

    for (int i = 0; i < len; i++) {
      Map<String, String> ident = new HashMap<String, String>();

      if (nameIdentifiers != null) {
        String nameIdent = nameIdentifiers.split(",")[i];

        if (nameIdent != "") {
          ident.put("name", nameIdent);
        }
      }

      if (macIdentifiers != null) {
        String macIdent = macIdentifiers.split(",")[i];

        if (macIdent != "") {
          ident.put("address", macIdent);
        }
      }

      if (!ident.isEmpty()) {
        identifyList.add(ident);
      }
    }
    return identifyList;
  }

  public String[] getProfile() {
    return profile.split(",");
  }

  public String[] getService() {
    return service.split(",");
  }

  @Override
  public String toString() {
    return "SimpleWatcher [name=" + name + ", identifiers=" + nameIdentifiers + macIdentifiers
        + "]";
  }
}
