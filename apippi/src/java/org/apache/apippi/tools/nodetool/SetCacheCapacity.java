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

import static com.google.common.base.Preconditions.checkArgument;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;

import java.util.ArrayList;
import java.util.List;

import org.apache.apippi.tools.NodeProbe;
import org.apache.apippi.tools.NodeTool.NodeToolCmd;

@Command(name = "setcachecapacity", description = "Set global key, row, and counter cache capacities (in MB units)")
public class SetCacheCapacity extends NodeToolCmd
{
    @Arguments(title = "<key-cache-capacity> <row-cache-capacity> <counter-cache-capacity>",
               usage = "<key-cache-capacity> <row-cache-capacity> <counter-cache-capacity>",
               description = "Key cache, row cache, and counter cache (in MB)",
               required = true)
    private List<Integer> args = new ArrayList<>();

    @Override
    public void execute(NodeProbe probe)
    {
        checkArgument(args.size() == 3, "setcachecapacity requires key-cache-capacity, row-cache-capacity, and counter-cache-capacity args.");
        probe.setCacheCapacities(args.get(0), args.get(1), args.get(2));
    }
}
