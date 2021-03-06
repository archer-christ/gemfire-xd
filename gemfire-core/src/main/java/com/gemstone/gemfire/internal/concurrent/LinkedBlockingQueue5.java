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
package com.gemstone.gemfire.internal.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * LBQ implementation for JDK 5.
 * @author darrel
 */
class LinkedBlockingQueue5
  extends java.util.concurrent.LinkedBlockingQueue
  implements BQ {
  private static final long serialVersionUID = -4118982556450283495L;
  public LinkedBlockingQueue5() {
    super();
  }
  public LinkedBlockingQueue5(int capacity) {
    super(capacity);
  }
  public LinkedBlockingQueue5(java.util.Collection c) {
    super(c);
  }
  public boolean offer(Object e, long msTimeout) throws InterruptedException {
    return offer(e, msTimeout, TimeUnit.MILLISECONDS);
  }
  public Object poll(long msTimeout) throws InterruptedException {
    return poll(msTimeout, TimeUnit.MILLISECONDS);
  }
}
