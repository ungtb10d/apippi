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

import com.google.common.math.DoubleMath;

import io.airlift.airline.Command;
import io.airlift.airline.Option;
import org.apache.apippi.tools.NodeProbe;
import org.apache.apippi.tools.NodeTool.NodeToolCmd;

@Command(name = "getinterdcstreamthroughput", description = "Print the throughput cap for inter-datacenter streaming and entire SSTable inter-datacenter streaming in the system" +
                                                            "in rounded megabits. For precise number, please, use option -d")
public class GetInterDCStreamThroughput extends NodeToolCmd
{
    @SuppressWarnings("UnusedDeclaration")
    @Option(name = { "-e", "--entire-sstable-throughput" }, description = "Print entire SSTable streaming throughput in MiB/s")
    private boolean entireSSTableThroughput;

    @SuppressWarnings("UnusedDeclaration")
    @Option(name = { "-m", "--mib" }, description = "Print the throughput cap for inter-datacenter streaming in MiB/s")
    private boolean interDCStreamThroughputMiB;

    @SuppressWarnings("UnusedDeclaration")
    @Option(name = { "-d", "--precise-mbit" }, description = "Print the throughput cap for inter-datacenter streaming in precise Mbits (double)")
    private boolean interDCStreamThroughputDoubleMbit;

    @Override
    public void execute(NodeProbe probe)
    {
        int throughput;
        double throughputInDouble;

        if (entireSSTableThroughput)
        {
            if (interDCStreamThroughputDoubleMbit || interDCStreamThroughputMiB)
                throw new IllegalArgumentException("You cannot use more than one flag with this command");

            throughputInDouble = probe.getEntireSSTableInterDCStreamThroughput();
            probe.output().out.printf("Current entire SSTable inter-datacenter stream throughput: %s%n",
                                      throughputInDouble > 0 ? throughputInDouble + " MiB/s" : "unlimited");
        }
        else if (interDCStreamThroughputMiB)
        {
            if (interDCStreamThroughputDoubleMbit)
                throw new IllegalArgumentException("You cannot use more than one flag with this command");

            throughputInDouble = probe.getInterDCStreamThroughputMibAsDouble();
            probe.output().out.printf("Current inter-datacenter stream throughput: %s%n",
                                      throughputInDouble > 0 ? throughputInDouble + " MiB/s" : "unlimited");

        }
        else if (interDCStreamThroughputDoubleMbit)
        {
            throughputInDouble = probe.getInterDCStreamThroughputAsDouble();
            probe.output().out.printf("Current stream throughput: %s%n",
                                      throughputInDouble > 0 ? throughputInDouble + " Mb/s" : "unlimited");
        }
        else
        {
            throughputInDouble = probe.getInterDCStreamThroughputAsDouble();
            throughput = probe.getInterDCStreamThroughput();

            if (throughput <= 0)
                probe.output().out.printf("Current inter-datacenter stream throughput: unlimited%n");
            else if (DoubleMath.isMathematicalInteger(throughputInDouble))
                probe.output().out.printf(throughputInDouble + "Current inter-datacenter stream throughput: %s%n", throughput + " Mb/s");
            else
                throw new RuntimeException("Use the -d flag to quiet this error and get the exact throughput in megabits/s");
        }
    }
}
