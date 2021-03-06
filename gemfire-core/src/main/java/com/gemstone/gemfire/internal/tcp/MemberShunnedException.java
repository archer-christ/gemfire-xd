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

package com.gemstone.gemfire.internal.tcp;

import com.gemstone.gemfire.GemFireException;

/**
 * MemberShunnedException may be thrown to prevent ack-ing a message
 * received from a member that has been removed from membership.  It
 * is currently only thrown by JGroupMembershipManager.processMessage()
 * @author bruce
 */
public class MemberShunnedException extends GemFireException
{
  private static final long serialVersionUID = -8453126202477831557L;
  private Stub member;
  
  /**
   * constructor
   * @param member the member that was shunned
   */
  public MemberShunnedException(Stub member) {
    super("");
    this.member = member;
  }
  
  /**
   * @return the member that was shunned
   */
  public Stub getShunnedMember() {
    return this.member;
  }

}
