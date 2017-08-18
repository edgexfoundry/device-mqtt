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
package org.edgexfoundry.controller;

import org.apache.log4j.Logger;
import org.edgexfoundry.domain.meta.CallbackAlert;
import org.edgexfoundry.exception.controller.ServiceException;
import org.edgexfoundry.handler.SchedulerCallbackHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/${service.callback}")
public class ScheduleController {
	
	@Autowired
	private SchedulerCallbackHandler callbackHandler;
	
	@Autowired
	private ObjectMapper mapper;
	
	private static final Logger logger = Logger.getLogger(ScheduleController.class);

	@RequestMapping(method = RequestMethod.PUT)
	public String handlePUT(@RequestBody String body) {
		try {
			logger.debug("put callback : '" + body + "'");
			CallbackAlert data = mapper.readValue(body, CallbackAlert.class);
			return (callbackHandler.handlePUT(data) == true) ? "true" : "false";
		} catch (Exception e) {
			logger.error("put error : " + e.getMessage());
			throw new ServiceException(e);
		}
	}

	@RequestMapping(method = RequestMethod.POST)
	public String handlePOST(@RequestBody String body) {
		try {
			logger.debug("post callback : '" + body + "'");
			CallbackAlert data = mapper.readValue(body, CallbackAlert.class);
			return (callbackHandler.handlePOST(data) == true) ? "true" : "false";
		} catch (Exception e) {
			logger.error("post error : " + e.getMessage());
			throw new ServiceException(e);
		}
	}

	@RequestMapping(method = RequestMethod.DELETE)
	public String handleDELETE(@RequestBody String body) {
		try {
			logger.debug("delete callback : '" + body + "'" );
			CallbackAlert data = mapper.readValue(body, CallbackAlert.class);
			return (callbackHandler.handleDELETE(data) == true) ? "true" : "false";
		} catch (Exception e) {
			logger.error("delete error : " + e.getMessage());
			throw new ServiceException(e);
		}
	}
	
}
