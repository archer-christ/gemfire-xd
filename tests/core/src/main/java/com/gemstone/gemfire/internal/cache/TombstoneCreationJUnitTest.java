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
package com.gemstone.gemfire.internal.cache;

import java.net.InetAddress;
import java.util.Properties;

import junit.framework.TestCase;

import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.EntryNotFoundException;
import com.gemstone.gemfire.cache.Operation;
import com.gemstone.gemfire.cache.RegionFactory;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.Assert;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;


public class TombstoneCreationJUnitTest extends TestCase {

  @Override
  public void tearDown() {
    InternalDistributedSystem system = InternalDistributedSystem.getConnectedInstance();
    if (system != null) {
      system.disconnect();
    }
  }

  public void testDestroyCreatesTombstone() throws Exception {
    String name = getName();
    Properties props = new Properties();
    props.put(DistributionConfig.LOCATORS_NAME, "");
    props.put(DistributionConfig.MCAST_PORT_NAME, "0");
    props.put(DistributionConfig.LOG_LEVEL_NAME, "config");
    GemFireCacheImpl cache = (GemFireCacheImpl)CacheFactory.create(DistributedSystem.connect(props));
    RegionFactory f = cache.createRegionFactory(RegionShortcut.REPLICATE);
    DistributedRegion region = (DistributedRegion)f.create(name);
    
    EntryEventImpl ev = EntryEventImpl.create(region,
        Operation.DESTROY, "myDestroyedKey", null, null, true, new InternalDistributedMember(InetAddress.getLocalHost(), 1234));
     VersionTag tag = VersionTag.create((InternalDistributedMember)ev.getDistributedMember());
     tag.setIsRemoteForTesting();
     tag.setEntryVersion(2);
     tag.setRegionVersion(12345);
     tag.setVersionTimeStamp(System.currentTimeMillis());
     tag.setDistributedSystemId(1);
     ev.setVersionTag(tag);
     cache.getLogger().info("destroyThread is trying to destroy the entry: " + region.getRegionEntry("myDestroyedKey"));
     region.basicDestroy(ev,
         false,
         null); // expectedOldValue not supported on
     RegionEntry entry = region.getRegionEntry("myDestroyedKey");
     Assert.assertTrue(entry != null, "expected to find a region entry for myDestroyedKey");
     Assert.assertTrue(entry.isTombstone(), "expected entry to be found and be a tombstone but it is " + entry);
     
  }
  
  /**
   * In bug #47868 a thread puts a REMOVED_PHASE1 entry in the map but is
   * unable to lock the entry before a Destroy thread gets it.  The Destroy
   * thread did not apply its operation but threw an EntryNotFoundException.
   * It is supposed to create a Tombstone.
   * @throws Exception
   */
  public void testConcurrentCreateAndDestroy() throws Exception {
    String name = getName();
    Properties props = new Properties();
    props.put(DistributionConfig.LOCATORS_NAME, "");
    props.put(DistributionConfig.MCAST_PORT_NAME, "0");
    props.put(DistributionConfig.LOG_LEVEL_NAME, "config");
    final GemFireCacheImpl cache = (GemFireCacheImpl)CacheFactory.create(DistributedSystem.connect(props));
    RegionFactory f = cache.createRegionFactory(RegionShortcut.REPLICATE);
    final DistributedRegion region = (DistributedRegion)f.create(name);
    
    // simulate a put() getting into AbstractRegionMap.basicPut() and creating an entry
    // that has not yet been initialized with values.  Then do a destroy that will encounter
    // the entry
    String key = "destroyedKey1";
    VersionedThinRegionEntryHeap entry = new VersionedThinRegionEntryHeap(region, key, Token.REMOVED_PHASE1);
    ((AbstractRegionMap)region.getRegionMap()).putEntryIfAbsentForTest(entry);
    cache.getLogger().info("entry inserted into cache: " + entry);

    EntryEventImpl ev = EntryEventImpl.create(region,
        Operation.DESTROY, key, null, null, true, new InternalDistributedMember(InetAddress.getLocalHost(), 1234));
    VersionTag tag = VersionTag.create((InternalDistributedMember)ev.getDistributedMember());
    tag.setIsRemoteForTesting();
    tag.setEntryVersion(2);
    tag.setRegionVersion(12345);
    tag.setVersionTimeStamp(System.currentTimeMillis());
    tag.setDistributedSystemId(1);
    ev.setVersionTag(tag);
    cache.getLogger().info("destroyThread is trying to destroy the entry: " + region.getRegionEntry(key));
    region.basicDestroy(ev,
        false,
        null); // expectedOldValue not supported on
    entry = (VersionedThinRegionEntryHeap)region.getRegionEntry(key);
    region.dumpBackingMap();
    Assert.assertTrue(entry != null, "expected to find a region entry for " + key);
    Assert.assertTrue(entry.isTombstone(), "expected entry to be found and be a tombstone but it is " + entry);
    Assert.assertTrue(entry.getVersionStamp().getEntryVersion() == tag.getEntryVersion(),
       "expected " + tag.getEntryVersion() + 
         " but found " + entry.getVersionStamp().getEntryVersion());
    
    
    RegionMap map = region.getRegionMap();
    tag = entry.asVersionTag();
    map.removeTombstone(entry, tag, false, true);
    
    // now do an op that has local origin
    entry = new VersionedThinRegionEntryHeap(region, key, Token.REMOVED_PHASE1);
    ((AbstractRegionMap)region.getRegionMap()).putEntryIfAbsentForTest(entry);
    cache.getLogger().info("entry inserted into cache: " + entry);

    ev = EntryEventImpl.create(region,
        Operation.DESTROY, key, null, null, false, cache.getMyId());
    tag = VersionTag.create((InternalDistributedMember)ev.getDistributedMember());
    tag.setEntryVersion(2);
    tag.setRegionVersion(12345);
    tag.setVersionTimeStamp(System.currentTimeMillis());
    tag.setDistributedSystemId(1);
    ev.setVersionTag(tag);
    cache.getLogger().info("destroyThread is trying to destroy the entry: " + region.getRegionEntry(key));
    boolean caught = false;
    try {
      region.basicDestroy(ev,
        false,
        null); // expectedOldValue not supported on
    } catch (EntryNotFoundException e) {
      caught = true;
    }
    Assert.assertTrue(caught, "expected an EntryNotFoundException for origin=local destroy operation");
  }

}
