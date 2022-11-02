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

package org.apache.apippi.repair;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.SchemaLoader;
import org.apache.apippi.cql3.statements.schema.CreateTableStatement;
import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.repair.messages.SyncRequest;
import org.apache.apippi.schema.KeyspaceParams;
import org.apache.apippi.schema.Schema;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.service.ActiveRepairService;
import org.apache.apippi.streaming.PreviewKind;
import org.apache.apippi.streaming.StreamPlan;
import org.apache.apippi.utils.TimeUUID;

import static org.apache.apippi.utils.TimeUUID.Generator.nextTimeUUID;

public class StreamingRepairTaskTest extends AbstractRepairTest
{
    protected String ks;
    protected final String tbl = "tbl";
    protected TableMetadata cfm;
    protected ColumnFamilyStore cfs;

    @BeforeClass
    public static void setupClass()
    {
        SchemaLoader.prepareServer();
    }

    @Before
    public void setup()
    {
        ks = "ks_" + System.currentTimeMillis();
        cfm = CreateTableStatement.parse(String.format("CREATE TABLE %s.%s (k INT PRIMARY KEY, v INT)", ks, tbl), ks).build();
        SchemaLoader.createKeyspace(ks, KeyspaceParams.simple(1), cfm);
        cfs = Schema.instance.getColumnFamilyStoreInstance(cfm.id);
    }

    @Test
    public void incrementalStreamPlan() throws NoSuchRepairSessionException
    {
        TimeUUID sessionID = registerSession(cfs, true, true);
        ActiveRepairService.ParentRepairSession prs = ActiveRepairService.instance.getParentRepairSession(sessionID);
        RepairJobDesc desc = new RepairJobDesc(sessionID, nextTimeUUID(), ks, tbl, prs.getRanges());

        SyncRequest request = new SyncRequest(desc, PARTICIPANT1, PARTICIPANT2, PARTICIPANT3, prs.getRanges(), PreviewKind.NONE, false);
        StreamingRepairTask task = new StreamingRepairTask(desc, request.initiator, request.src, request.dst, request.ranges, desc.sessionId, PreviewKind.NONE, false);

        StreamPlan plan = task.createStreamPlan(request.dst);
        Assert.assertFalse(plan.getFlushBeforeTransfer());
    }

    @Test
    public void fullStreamPlan() throws Exception
    {
        TimeUUID sessionID = registerSession(cfs, false, true);
        ActiveRepairService.ParentRepairSession prs = ActiveRepairService.instance.getParentRepairSession(sessionID);
        RepairJobDesc desc = new RepairJobDesc(sessionID, nextTimeUUID(), ks, tbl, prs.getRanges());
        SyncRequest request = new SyncRequest(desc, PARTICIPANT1, PARTICIPANT2, PARTICIPANT3, prs.getRanges(), PreviewKind.NONE, false);
        StreamingRepairTask task = new StreamingRepairTask(desc, request.initiator, request.src, request.dst, request.ranges, null, PreviewKind.NONE, false);

        StreamPlan plan = task.createStreamPlan(request.dst);
        Assert.assertTrue(plan.getFlushBeforeTransfer());
    }
}