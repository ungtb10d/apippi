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
package org.apache.apippi.tools.nodetool;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import org.apache.apippi.tools.NodeProbe;
import org.apache.apippi.tools.NodeTool.NodeToolCmd;

@Command(name = "setlogginglevel", description = "Set the log level threshold for a given component or class. Will reset to the initial configuration if called with no parameters.")
public class SetLoggingLevel extends NodeToolCmd
{
    @Arguments(usage = "<component|class> <level>", description = "The component or class to change the level for and the log level threshold to set. Will reset to initial level if omitted. "
        + "Available components:  bootstrap, compaction, repair, streaming, cql, ring")
    private List<String> args = new ArrayList<>();

    @Override
    public void execute(NodeProbe probe)
    {
        String target = args.size() >= 1 ? args.get(0) : EMPTY;
        String level = args.size() == 2 ? args.get(1) : EMPTY;

        List<String> classQualifiers = Collections.singletonList(target);
        if (target.equals("bootstrap"))
        {
            classQualifiers = Lists.newArrayList(
                    "org.apache.apippi.gms",
                    "org.apache.apippi.hints",
                    "org.apache.apippi.schema",
                    "org.apache.apippi.service.StorageService",
                    "org.apache.apippi.db.SystemKeyspace",
                    "org.apache.apippi.batchlog.BatchlogManager",
                    "org.apache.apippi.net.MessagingService");
        }
        else if (target.equals("repair"))
        {
            classQualifiers = Lists.newArrayList(
                    "org.apache.apippi.repair",
                    "org.apache.apippi.db.compaction.CompactionManager",
                    "org.apache.apippi.service.SnapshotVerbHandler");
        }
        else if (target.equals("streaming"))
        {
            classQualifiers = Lists.newArrayList(
                    "org.apache.apippi.streaming",
                    "org.apache.apippi.dht.RangeStreamer");
        }
        else if (target.equals("compaction"))
        {
            classQualifiers = Lists.newArrayList(
                    "org.apache.apippi.db.compaction",
                    "org.apache.apippi.db.ColumnFamilyStore",
                    "org.apache.apippi.io.sstable.IndexSummaryRedistribution");
        }
        else if (target.equals("cql"))
        {
            classQualifiers = Lists.newArrayList(
                    "org.apache.apippi.cql3",
                    "org.apache.apippi.auth",
                    "org.apache.apippi.batchlog",
                    "org.apache.apippi.net.ResponseVerbHandler",
                    "org.apache.apippi.service.AbstractReadExecutor",
                    "org.apache.apippi.service.AbstractWriteResponseHandler",
                    "org.apache.apippi.service.paxos",
                    "org.apache.apippi.service.ReadCallback",
                    "org.apache.apippi.service.ResponseResolver");
        }
        else if (target.equals("ring"))
        {
            classQualifiers = Lists.newArrayList(
                    "org.apache.apippi.gms",
                    "org.apache.apippi.service.PendingRangeCalculatorService",
                    "org.apache.apippi.service.LoadBroadcaster",
                    "org.apache.apippi.transport.Server");
        }

        for (String classQualifier : classQualifiers)
            probe.setLoggingLevel(classQualifier, level);
    }
}
