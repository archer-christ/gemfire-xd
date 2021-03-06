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
   
package com.gemstone.gemfire.internal.admin.remote;

import com.gemstone.gemfire.distributed.internal.*;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.*;
//import com.gemstone.gemfire.internal.*;
//import com.gemstone.gemfire.internal.admin.*;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.ExpirationAction;
import java.io.*;
//import java.util.*;

/**
 * A message that is sent to a particular distribution manager to let
 * it know that the sender is an administation console that just connected.
 */
public final class DestroyEntryMessage extends RegionAdminMessage {
  
  private Object key;
  private ExpirationAction action;

  public static DestroyEntryMessage create(Object key, ExpirationAction action) {
    DestroyEntryMessage m = new DestroyEntryMessage();
    m.action = action;
    m.key = key;
    return m;
  }

  @Override
  public void process(DistributionManager dm) {
    Region r = getRegion(dm.getSystem());
    if (r != null) {
      try {
        if (action == ExpirationAction.LOCAL_DESTROY) {
          r.localDestroy(key);
        } else if (action == ExpirationAction.DESTROY) {
          r.destroy(key);
        } else if (action == ExpirationAction.INVALIDATE) {
          r.invalidate(key);
        } else if (action == ExpirationAction.LOCAL_INVALIDATE) {
          r.localInvalidate(key);
        }
      } catch (Exception e) {
        dm.getLoggerI18n().warning(
            LocalizedStrings.DestroEntryMessage_FAILED_ATTEMPT_TO_DESTROY_OR_INVALIDATE_ENTRY_0_1_FROM_CONSOLE_AT_2,
            new Object[] {r.getFullPath(), key, this.getSender()});
      }
    }
  }

  public int getDSFID() {
    return DESTROY_ENTRY_MESSAGE;
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    super.toData(out);
    DataSerializer.writeObject(this.action, out);
    DataSerializer.writeObject(this.key, out);
  }

  @Override
  public void fromData(DataInput in) throws IOException,
      ClassNotFoundException {
    super.fromData(in);
    this.action = (ExpirationAction)DataSerializer.readObject(in);
    this.key = DataSerializer.readObject(in);
  }

  @Override
  public String toString(){
    return LocalizedStrings.DestroyEntryMessage_DESTROYENTRYMESSAGE_FROM_0.toLocalizedString(this.getSender());
  }
}
