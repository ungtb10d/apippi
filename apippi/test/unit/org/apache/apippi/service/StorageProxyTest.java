/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.apippi.service;

import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.apippi.ServerTestUtils;
import org.apache.apippi.config.Config;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.gms.EndpointState;
import org.apache.apippi.gms.Gossiper;
import org.apache.apippi.gms.HeartBeatState;
import org.apache.apippi.locator.InetAddressAndPort;
import org.apache.apippi.locator.Replica;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;

import static org.apache.apippi.locator.ReplicaUtils.full;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(BMUnitRunner.class)
public class StorageProxyTest
{
    @BeforeClass
    public static void initDD()
    {
        DatabaseDescriptor.daemonInitialization();
        ServerTestUtils.mkdirs();
    }

    @Test
    public void testSetGetPaxosVariant()
    {
        Assert.assertEquals(Config.PaxosVariant.v1, DatabaseDescriptor.getPaxosVariant());
        Assert.assertEquals("v1", StorageProxy.instance.getPaxosVariant());
        StorageProxy.instance.setPaxosVariant("v2");
        Assert.assertEquals("v2", StorageProxy.instance.getPaxosVariant());
        Assert.assertEquals(Config.PaxosVariant.v2, DatabaseDescriptor.getPaxosVariant());
        DatabaseDescriptor.setPaxosVariant(Config.PaxosVariant.v1);
        Assert.assertEquals(Config.PaxosVariant.v1, DatabaseDescriptor.getPaxosVariant());
        Assert.assertEquals(Config.PaxosVariant.v1, DatabaseDescriptor.getPaxosVariant());
    }

    @Test
    public void testShouldHint() throws Exception
    {
        // HAPPY PATH with all defaults
        shouldHintTest(replica -> {
            assertThat(StorageProxy.shouldHint(replica)).isTrue();
            assertThat(StorageProxy.shouldHint(replica, /* tryEnablePersistentWindow */ false)).isTrue();
        });
    }

    @Test
    public void testShouldHintOnWindowExpiry() throws Exception
    {
        shouldHintTest(replica -> {
            // wait for 5 ms, we will shorten the hints window later
            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.MILLISECONDS);

            final int originalHintWindow = DatabaseDescriptor.getMaxHintWindow();
            try
            {
                DatabaseDescriptor.setMaxHintWindow(1); // 1 ms. It should not hint
                assertThat(StorageProxy.shouldHint(replica)).isFalse();
            }
            finally
            {
                DatabaseDescriptor.setMaxHintWindow(originalHintWindow);
            }
        });
    }

    @Test
    @BMRule(name = "Hints size exceeded the limit",
            targetClass="org.apache.apippi.hints.HintsService",
            targetMethod="getTotalHintsSize",
            action="return 2097152;") // 2MB
    public void testShouldHintOnExceedingSize() throws Exception
    {
        shouldHintTest(replica -> {
            final int originalHintsSizeLimit = DatabaseDescriptor.getMaxHintsSizePerHostInMiB();
            try
            {
                DatabaseDescriptor.setMaxHintsSizePerHostInMiB(1);
                assertThat(StorageProxy.shouldHint(replica)).isFalse();
            }
            finally
            {
                DatabaseDescriptor.setMaxHintsSizePerHostInMiB(originalHintsSizeLimit);
            }
        });
    }


    /**
     * Ensure that the timer backing the JMX endpoint to transiently enable blocking read repairs both enables
     * and disables the way we'd expect.
     */
    @Test
    public void testTransientLoggingTimer()
    {
        StorageProxy.instance.logBlockingReadRepairAttemptsForNSeconds(2);
        Assert.assertTrue(StorageProxy.instance.isLoggingReadRepairs());
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        Assert.assertFalse(StorageProxy.instance.isLoggingReadRepairs());
    }

    private void shouldHintTest(Consumer<Replica> test) throws UnknownHostException
    {
        InetAddressAndPort testEp = InetAddressAndPort.getByName("192.168.1.1");
        Replica replica = full(testEp);
        StorageService.instance.getTokenMetadata().updateHostId(UUID.randomUUID(), testEp);
        EndpointState state = new EndpointState(new HeartBeatState(0, 0));
        Gossiper.runInGossipStageBlocking(() -> Gossiper.instance.markDead(replica.endpoint(), state));

        try
        {
            test.accept(replica);
        }
        finally
        {
            StorageService.instance.getTokenMetadata().removeEndpoint(testEp);
        }
    }
}
