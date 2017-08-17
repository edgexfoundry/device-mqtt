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
import java.util.UUID;

public class Transaction {	
	private String transactionId;
	private List<ResponseObject> responses;
	private Map<String, Boolean> opIds;
	private Boolean finished = true;
	
	public Transaction() {
		setTransactionId(UUID.randomUUID().toString());
		setResponses(new ArrayList<ResponseObject>());
		opIds = new HashMap<String, Boolean>();
	}
	
	private void setResponses(List<ResponseObject> responses) {
		this.responses = responses;
	}

	private void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String newOpId() {
		String opId = UUID.randomUUID().toString();
		opIds.put(opId, false);
		finished = false;
		return opId;
	}
	
	public void finishOp(String opId, List<ResponseObject> responses) {
		addResponses(responses);
		opIds.put(opId, true);
		if (!opIds.values().contains(false))
			finished = true;
	}
	
	public Boolean isFinished() {
		return finished;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public List<ResponseObject> getResponses() {
		return responses;
	}

	private void addResponses(List<ResponseObject> responses) {
		if (responses != null)
			this.responses.addAll(responses);
	}

}
