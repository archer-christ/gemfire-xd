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
/**
 * 
 */
package com.gemstone.gemfire.internal.cache.tier.sockets.command;

import com.gemstone.gemfire.internal.cache.EventID;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.tier.CachedRegionHelper;
import com.gemstone.gemfire.internal.cache.tier.Command;
import com.gemstone.gemfire.internal.cache.tier.MessageType;
import com.gemstone.gemfire.internal.cache.tier.sockets.*;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.internal.security.AuthorizeRequest;
import com.gemstone.gemfire.cache.operations.RegionClearOperationContext;
import com.gemstone.gemfire.distributed.internal.DistributionStats;
import java.io.IOException;
import java.nio.ByteBuffer;


public class ClearRegion extends BaseCommand {

  private final static ClearRegion singleton = new ClearRegion();

  public static Command getCommand() {
    return singleton;
  }

  private ClearRegion() {
  }

  @Override
  public void cmdExecute(Message msg, ServerConnection servConn, long start)
      throws IOException, InterruptedException {
    Part regionNamePart = null,  callbackArgPart = null;
    String regionName = null;
    Object callbackArg = null;
    Part eventPart = null;
    CachedRegionHelper crHelper = servConn.getCachedRegionHelper();
    CacheServerStats stats = servConn.getCacheServerStats();
    servConn.setAsTrue(REQUIRES_RESPONSE);

    {
      long oldStart = start;
      start = DistributionStats.getStatTime();
      stats.incReadClearRegionRequestTime(start - oldStart);
    }
    // Retrieve the data from the message parts
    regionNamePart = msg.getPart(0);
    eventPart = msg.getPart(1);
//    callbackArgPart = null; (redundant assignment)
    if (msg.getNumberOfParts() > 2) {
      callbackArgPart = msg.getPart(2);
      try {
        callbackArg = callbackArgPart.getObject();
      }
      catch (Exception e) {
        writeException(msg, e, false, servConn);
        servConn.setAsTrue(RESPONDED);
        return;
      }
    }
    regionName = regionNamePart.getString();
    if (logger.fineEnabled()) {
      logger.fine(servConn.getName() + ": Received clear region request ("
          + msg.getPayloadLength() + " bytes) from "
          + servConn.getSocketString() + " for region " + regionName);
    }

    // Process the clear region request
    if (regionName == null) {
      if (logger.warningEnabled()) {
        logger.warning(
          LocalizedStrings.ClearRegion_0_THE_INPUT_REGION_NAME_FOR_THE_CLEAR_REGION_REQUEST_IS_NULL,
          servConn.getName());
      }
      String errMessage = LocalizedStrings.ClearRegion_THE_INPUT_REGION_NAME_FOR_THE_CLEAR_REGION_REQUEST_IS_NULL.toLocalizedString();

      writeErrorResponse(msg, MessageType.CLEAR_REGION_DATA_ERROR, errMessage,
          servConn);
      servConn.setAsTrue(RESPONDED);
    }
    else {
      LocalRegion region = (LocalRegion)crHelper.getRegion(regionName);
      if (region == null) {
        String reason = LocalizedStrings.ClearRegion_WAS_NOT_FOUND_DURING_CLEAR_REGION_REGUEST.toLocalizedString();
        writeRegionDestroyedEx(msg, regionName, reason, servConn);
        servConn.setAsTrue(RESPONDED);
      }
      else {
        // Clear the region

        ByteBuffer eventIdPartsBuffer = ByteBuffer.wrap(eventPart
            .getSerializedForm());
        long threadId = EventID
            .readEventIdPartsFromOptmizedByteArray(eventIdPartsBuffer);
        long sequenceId = EventID
            .readEventIdPartsFromOptmizedByteArray(eventIdPartsBuffer);
        EventID eventId = new EventID(servConn.getEventMemberIDByteArray(),
            threadId, sequenceId);

        try {
          AuthorizeRequest authzRequest = servConn.getAuthzRequest();
          if (authzRequest != null) {
            RegionClearOperationContext clearContext = authzRequest
                .clearAuthorize(regionName, callbackArg);
            callbackArg = clearContext.getCallbackArg();
          }
          region.basicBridgeClear(callbackArg, servConn.getProxyID(),
              true /* boolean from cache Client */, eventId);
        }
        catch (Exception e) {
          // If an interrupted exception is thrown , rethrow it
          checkForInterrupt(servConn, e);

          // If an exception occurs during the clear, preserve the connection
          writeException(msg, e, false, servConn);
          servConn.setAsTrue(RESPONDED);
          return;
        }

        // Update the statistics and write the reply
        {
          long oldStart = start;
          start = DistributionStats.getStatTime();
          stats.incProcessClearRegionTime(start - oldStart);
        }
        writeReply(msg, servConn);
        servConn.setAsTrue(RESPONDED);
        if (logger.fineEnabled()) {
          logger.fine(servConn.getName()
              + ": Sent clear region response for region " + regionName);
        }
        stats.incWriteClearRegionResponseTime(DistributionStats.getStatTime()
            - start);
      }
    }

  }

}
