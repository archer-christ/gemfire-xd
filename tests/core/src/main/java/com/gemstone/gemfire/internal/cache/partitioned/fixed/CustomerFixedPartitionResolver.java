/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.gemstone.gemfire.internal.cache.partitioned.fixed;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.cache.EntryOperation;
import com.gemstone.gemfire.cache.FixedPartitionResolver;
import com.gemstone.gemfire.internal.cache.execute.data.CustId;
import com.gemstone.gemfire.internal.cache.execute.data.OrderId;
import com.gemstone.gemfire.internal.cache.execute.data.ShipmentId;

public class CustomerFixedPartitionResolver implements FixedPartitionResolver, DataSerializable
     {

  public CustomerFixedPartitionResolver() {
  }

  public String getPartitionName(EntryOperation opDetails,
      Set allAvailablePartitions) {

    int customerID = -1;

    if (opDetails.getKey() instanceof ShipmentId) {
      ShipmentId shipmentId = (ShipmentId)opDetails.getKey();
      customerID = shipmentId.getOrderId().getCustId().getCustId();
    }
    if (opDetails.getKey() instanceof OrderId) {
      OrderId orderId = (OrderId)opDetails.getKey();
      customerID = orderId.getCustId().getCustId();
    }
    else if (opDetails.getKey() instanceof CustId) {
      CustId custId = (CustId)opDetails.getKey();
      customerID = custId.getCustId();
    }

    if (customerID >= 1 && customerID <= 10) {
      return "10";
    }
    else if (customerID > 10 && customerID <= 20) {
      return "20";
    }
    else if (customerID > 20 && customerID <= 30) {
      return "30";
    }
    else if (customerID > 30 && customerID <= 40) {
      return "40";
    }
    else {
      return "Invalid";
    }

  }

  public Serializable getRoutingObject(EntryOperation opDetails) {

    Serializable routingbject = null;

    if (opDetails.getKey() instanceof ShipmentId) {
      ShipmentId shipmentId = (ShipmentId)opDetails.getKey();
      routingbject = shipmentId.getOrderId().getCustId();
    }
    if (opDetails.getKey() instanceof OrderId) {
      OrderId orderId = (OrderId)opDetails.getKey();
      routingbject = orderId.getCustId();
    }
    else if (opDetails.getKey() instanceof CustId) {
      CustId custId = (CustId)opDetails.getKey();
      routingbject = custId.getCustId();
    }
    return routingbject;
  }


  public String getName() {
    return "CustomerFixedPartitionResolver";
  }

  public void close() {
    // TODO Auto-generated method stub
    
  }

  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    // TODO Auto-generated method stub
    
  }

  public void toData(DataOutput out) throws IOException {
    // TODO Auto-generated method stub
    
  }

}
