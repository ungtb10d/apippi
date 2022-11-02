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

package org.apache.apippi.repair.consistent;

import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.SchemaLoader;
import org.apache.apippi.cql3.statements.schema.CreateTableStatement;
import org.apache.apippi.repair.AbstractRepairTest;
import org.apache.apippi.repair.NoSuchRepairSessionException;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.schema.Schema;
import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.locator.InetAddressAndPort;
import org.apache.apippi.repair.messages.FailSession;
import org.apache.apippi.repair.messages.FinalizePromise;
import org.apache.apippi.repair.messages.PrepareConsistentResponse;
import org.apache.apippi.schema.KeyspaceParams;
import org.apache.apippi.service.ActiveRepairService;
import org.apache.apippi.utils.TimeUUID;

import static org.apache.apippi.utils.TimeUUID.Generator.nextTimeUUID;

public class CoordinatorSessionsTest extends AbstractRepairTest
{
    private static TableMetadata cfm;
    private static ColumnFamilyStore cfs;

    // to check CoordinatorSessions is passing the messages to the coordinator session correctly
    private static class InstrumentedCoordinatorSession extends CoordinatorSession
    {

        public InstrumentedCoordinatorSession(Builder builder)
        {
            super(builder);
        }

        int prepareResponseCalls = 0;
        InetAddressAndPort preparePeer = null;
        boolean prepareSuccess = false;
        public synchronized void handlePrepareResponse(InetAddressAndPort participant, boolean success)
        {
            prepareResponseCalls++;
            preparePeer = participant;
            prepareSuccess = success;
        }

        int finalizePromiseCalls = 0;
        InetAddressAndPort promisePeer = null;
        boolean promiseSuccess = false;
        public synchronized void handleFinalizePromise(InetAddressAndPort participant, boolean success)
        {
            finalizePromiseCalls++;
            promisePeer = participant;
            promiseSuccess = success;
        }

        int failCalls = 0;
        public synchronized void fail()
        {
            failCalls++;
        }
    }

    private static class InstrumentedCoordinatorSessions extends CoordinatorSessions
    {
        protected CoordinatorSession buildSession(CoordinatorSession.Builder builder)
        {
            return new InstrumentedCoordinatorSession(builder);
        }

        public InstrumentedCoordinatorSession getSession(TimeUUID sessionId)
        {
            return (InstrumentedCoordinatorSession) super.getSession(sessionId);
        }

        public InstrumentedCoordinatorSession registerSession(TimeUUID sessionId, Set<InetAddressAndPort> peers, boolean isForced) throws NoSuchRepairSessionException
        {
            return (InstrumentedCoordinatorSession) super.registerSession(sessionId, peers, isForced);
        }
    }

    @BeforeClass
    public static void setupClass()
    {
        SchemaLoader.prepareServer();
        cfm = CreateTableStatement.parse("CREATE TABLE tbl (k INT PRIMARY KEY, v INT)", "coordinatorsessiontest").build();
        SchemaLoader.createKeyspace("coordinatorsessiontest", KeyspaceParams.simple(1), cfm);
        cfs = Schema.instance.getColumnFamilyStoreInstance(cfm.id);
    }

    private static TimeUUID registerSession()
    {
        return registerSession(cfs, true, true);
    }

    @Test
    public void registerSessionTest() throws NoSuchRepairSessionException
    {
        CoordinatorSessions sessions = new CoordinatorSessions();
        TimeUUID sessionID = registerSession();
        CoordinatorSession session = sessions.registerSession(sessionID, PARTICIPANTS, false);

        Assert.assertEquals(ConsistentSession.State.PREPARING, session.getState());
        Assert.assertEquals(sessionID, session.sessionID);
        Assert.assertEquals(COORDINATOR, session.coordinator);
        Assert.assertEquals(Sets.newHashSet(cfm.id), session.tableIds);

        ActiveRepairService.ParentRepairSession prs = ActiveRepairService.instance.getParentRepairSession(sessionID);
        Assert.assertEquals(prs.repairedAt, session.repairedAt);
        Assert.assertEquals(prs.getRanges(), session.ranges);
        Assert.assertEquals(PARTICIPANTS, session.participants);

        Assert.assertSame(session, sessions.getSession(sessionID));
    }

    @Test
    public void handlePrepareResponse() throws NoSuchRepairSessionException
    {
        InstrumentedCoordinatorSessions sessions = new InstrumentedCoordinatorSessions();
        TimeUUID sessionID = registerSession();

        InstrumentedCoordinatorSession session = sessions.registerSession(sessionID, PARTICIPANTS, false);
        Assert.assertEquals(0, session.prepareResponseCalls);

        sessions.handlePrepareResponse(new PrepareConsistentResponse(sessionID, PARTICIPANT1, true));
        Assert.assertEquals(1, session.prepareResponseCalls);
        Assert.assertEquals(PARTICIPANT1, session.preparePeer);
        Assert.assertTrue(session.prepareSuccess);
    }

    @Test
    public void handlePrepareResponseNoSession()
    {
        InstrumentedCoordinatorSessions sessions = new InstrumentedCoordinatorSessions();
        TimeUUID fakeID = nextTimeUUID();

        sessions.handlePrepareResponse(new PrepareConsistentResponse(fakeID, PARTICIPANT1, true));
        Assert.assertNull(sessions.getSession(fakeID));
    }

    @Test
    public void handlePromiseResponse() throws NoSuchRepairSessionException
    {
        InstrumentedCoordinatorSessions sessions = new InstrumentedCoordinatorSessions();
        TimeUUID sessionID = registerSession();

        InstrumentedCoordinatorSession session = sessions.registerSession(sessionID, PARTICIPANTS, false);
        Assert.assertEquals(0, session.finalizePromiseCalls);

        sessions.handleFinalizePromiseMessage(new FinalizePromise(sessionID, PARTICIPANT1, true));
        Assert.assertEquals(1, session.finalizePromiseCalls);
        Assert.assertEquals(PARTICIPANT1, session.promisePeer);
        Assert.assertTrue(session.promiseSuccess);
    }

    @Test
    public void handlePromiseResponseNoSession()
    {
        InstrumentedCoordinatorSessions sessions = new InstrumentedCoordinatorSessions();
        TimeUUID fakeID = nextTimeUUID();

        sessions.handleFinalizePromiseMessage(new FinalizePromise(fakeID, PARTICIPANT1, true));
        Assert.assertNull(sessions.getSession(fakeID));
    }

    @Test
    public void handleFailureMessage() throws NoSuchRepairSessionException
    {
        InstrumentedCoordinatorSessions sessions = new InstrumentedCoordinatorSessions();
        TimeUUID sessionID = registerSession();

        InstrumentedCoordinatorSession session = sessions.registerSession(sessionID, PARTICIPANTS, false);
        Assert.assertEquals(0, session.failCalls);

        sessions.handleFailSessionMessage(new FailSession(sessionID));
        Assert.assertEquals(1, session.failCalls);
    }

    @Test
    public void handleFailureMessageNoSession()
    {
        InstrumentedCoordinatorSessions sessions = new InstrumentedCoordinatorSessions();
        TimeUUID fakeID = nextTimeUUID();

        sessions.handleFailSessionMessage(new FailSession(fakeID));
        Assert.assertNull(sessions.getSession(fakeID));
    }
}