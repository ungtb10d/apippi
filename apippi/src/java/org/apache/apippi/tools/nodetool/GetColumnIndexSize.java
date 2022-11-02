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

import io.airlift.airline.Command;
import org.apache.apippi.tools.NodeProbe;
import org.apache.apippi.tools.NodeTool.NodeToolCmd;

@Command(name = "getcolumnindexsize", description = "Print the granularity of the collation index of rows within a partition in KiB")
public class GetColumnIndexSize extends NodeToolCmd
{
    @Override
    protected void execute(NodeProbe probe)
    {
        probe.output().out.println("Current value for column_index_size: " + probe.getColumnIndexSizeInKB() + " KiB");
    }
}