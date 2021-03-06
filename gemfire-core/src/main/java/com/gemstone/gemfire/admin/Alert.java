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
package com.gemstone.gemfire.admin;

/**
 * An administration alert that is issued by a member of a GemFire
 * distributed system.  It is similar to a {@linkplain
 * com.gemstone.gemfire.i18n.LogWriterI18n log message}.
 *
 * @author    Kirk Lund
 * @see       AlertListener
 * @since     3.5
 * @deprecated as of 7.0 use the {@link com.gemstone.gemfire.management} package instead
 */
public interface Alert {
  
  /** The level at which this alert is issued */
  public AlertLevel getLevel();

  /**
   * The member of the distributed system that issued the alert, or
   * null if the issuer is no longer a member of the distributed system.
   */
  public SystemMember getSystemMember();

  /** 
   * The name of the {@linkplain
   * com.gemstone.gemfire.distributed.DistributedSystem#getName
   * distributed system}) through which the alert was issued.
   */
  public String getConnectionName();

  /** The id of the source of the alert (such as a thread in a VM) */
  public String getSourceId();

  /** The alert's message */
  public String getMessage();

  /** The time at which the alert was issued */
  public java.util.Date getDate();

}
