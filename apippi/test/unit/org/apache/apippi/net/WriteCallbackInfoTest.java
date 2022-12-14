/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.apippi.net;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.junit.Assert;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.db.ConsistencyLevel;
import org.apache.apippi.db.Mutation;
import org.apache.apippi.db.RegularAndStaticColumns;
import org.apache.apippi.db.partitions.PartitionUpdate;
import org.apache.apippi.locator.InetAddressAndPort;
import org.apache.apippi.schema.MockSchema;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.service.StorageService;
import org.apache.apippi.service.paxos.Commit;
import org.apache.apippi.utils.ByteBufferUtil;

import static org.apache.apippi.locator.ReplicaUtils.full;
import static org.apache.apippi.service.paxos.Ballot.Flag.NONE;
import static org.apache.apippi.service.paxos.BallotGenerator.Global.nextBallot;

public class WriteCallbackInfoTest
{
    private InetAddressAndPort testEp;

    @BeforeClass
    public static void initDD()
    {
        DatabaseDescriptor.daemonInitialization();
    }

    @Before
    public void setup() throws Exception
    {
        testEp = InetAddressAndPort.getByName("192.168.1.1");
        StorageService.instance.getTokenMetadata().updateHostId(UUID.randomUUID(), testEp);
    }

    @After
    public void teardown()
    {
        StorageService.instance.getTokenMetadata().removeEndpoint(testEp);
    }

    @Test
    public void testShouldHint() throws Exception
    {
        testShouldHint(Verb.COUNTER_MUTATION_REQ, ConsistencyLevel.ALL, true, false);
        for (Verb verb : new Verb[] { Verb.PAXOS_COMMIT_REQ, Verb.MUTATION_REQ })
        {
            testShouldHint(verb, ConsistencyLevel.ALL, true, true);
            testShouldHint(verb, ConsistencyLevel.ANY, true, false);
            testShouldHint(verb, ConsistencyLevel.ALL, false, false);
        }
    }

    private void testShouldHint(Verb verb, ConsistencyLevel cl, boolean allowHints, boolean expectHint)
    {
        TableMetadata metadata = MockSchema.newTableMetadata("", "");
        Object payload = verb == Verb.PAXOS_COMMIT_REQ
                         ? new Commit(nextBallot(NONE), new PartitionUpdate.Builder(metadata, ByteBufferUtil.EMPTY_BYTE_BUFFER, RegularAndStaticColumns.NONE, 1).build())
                         : new Mutation(PartitionUpdate.simpleBuilder(metadata, "").build());

        RequestCallbacks.WriteCallbackInfo wcbi = new RequestCallbacks.WriteCallbackInfo(Message.out(verb, payload), full(testEp), null, cl, allowHints);
        Assert.assertEquals(expectHint, wcbi.shouldHint());
        if (expectHint)
        {
            Assert.assertNotNull(wcbi.mutation());
        }
        else
        {
            boolean fail = false;
            try
            {
                wcbi.mutation();
            }
            catch (Throwable t)
            {
                fail = true;
            }
            Assert.assertTrue(fail);
        }
    }
}
