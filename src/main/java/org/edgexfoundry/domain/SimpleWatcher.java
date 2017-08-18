/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @microservice:  device-mqtt
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
	private String name_identifiers;
	private String mac_identifiers;
	
	public Integer getSize() {
		if (name == null) return 0;
		if ((name.split(",").length == 1) && name.equals("null"))
			return 0;
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
	
	public Map<String,String[]> getIdentifier() {
		Map<String,String[]> ident = new HashMap<String,String[]>();
		if (name_identifiers != null)
			ident.put("name", name_identifiers.split(","));
		if (mac_identifiers != null)
			ident.put("address", mac_identifiers.split(","));
		return ident;
	}
	
	public void setNameIdentifiers(String name_identifiers) {
		this.name_identifiers = name_identifiers;
	}
	
	public void setMacIdentifiers(String mac_identifiers) {
		this.mac_identifiers = mac_identifiers;
	}
	
	public List<Map<String, String>> getIdentifiers() {
		Integer len = this.name.split(",").length;
		ArrayList<Map<String, String>> identifyList = new ArrayList<Map<String, String>>();
		for (int i = 0; i < len; i++){
			Map<String, String> ident = new HashMap<String, String>();
			if (name_identifiers != null) {
				String nIdent = name_identifiers.split(",")[i];
				if (nIdent != "")
					ident.put("name", nIdent);
			}
			if (mac_identifiers != null) {
				String mIdent = mac_identifiers.split(",")[i];
				if (mIdent != "")
					ident.put("address", mIdent);
			}
			if (!ident.isEmpty())
				identifyList.add(ident);
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
		return "SimpleWatcher [name=" + name + ", identifiers=" + name_identifiers + mac_identifiers + "]";
	}
}
