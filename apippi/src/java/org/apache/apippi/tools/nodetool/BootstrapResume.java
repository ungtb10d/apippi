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

import java.io.IOError;
import java.io.IOException;

import io.airlift.airline.Option;
import org.apache.apippi.tools.NodeProbe;
import org.apache.apippi.tools.NodeTool.NodeToolCmd;

import static org.apache.apippi.config.apippiRelevantProperties.RESET_BOOTSTRAP_PROGRESS;

@Command(name = "resume", description = "Resume bootstrap streaming")
public class BootstrapResume extends NodeToolCmd
{
    @Option(title = "force",
            name = { "-f", "--force"},
            description = "Use --force to resume bootstrap regardless of apippi.reset_bootstrap_progress environment variable. WARNING: This is potentially dangerous, see apippi-17679")
    boolean force = false;

    @Override
    protected void execute(NodeProbe probe)
    {
        try
        {
            if ((!RESET_BOOTSTRAP_PROGRESS.isPresent() || RESET_BOOTSTRAP_PROGRESS.getBoolean()) && !force)
                throw new RuntimeException("'nodetool bootstrap resume' is disabled.");
            probe.resumeBootstrap(probe.output().out);
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }
}
