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

package org.apache.apippi.simulator.cluster;

import java.util.List;
import java.util.Map;

import org.apache.apippi.simulator.ActionList;
import org.apache.apippi.simulator.Actions.ReliableAction;

import static java.util.stream.IntStream.range;
import static org.apache.apippi.simulator.Action.Modifiers.NONE;
import static org.apache.apippi.simulator.Action.Modifiers.RELIABLE_NO_TIMEOUTS;

public class OnClusterRepairRanges extends ReliableAction
{
    public OnClusterRepairRanges(KeyspaceActions actions, int[] on, boolean repairPaxos, boolean repairOnlyPaxos, List<Map.Entry<String, String>> ranges)
    {
        super("Repair ranges", NONE, RELIABLE_NO_TIMEOUTS,
              () -> ActionList.of(range(0, on.length)
                              .mapToObj(
                                    i -> new OnInstanceRepair(actions, on[i], repairPaxos, repairOnlyPaxos, ranges.get(i), true))));
    }
}
