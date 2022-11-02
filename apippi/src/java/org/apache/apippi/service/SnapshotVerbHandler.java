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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apippi.db.SnapshotCommand;
import org.apache.apippi.db.Keyspace;
import org.apache.apippi.net.IVerbHandler;
import org.apache.apippi.net.Message;
import org.apache.apippi.net.MessagingService;
import org.apache.apippi.utils.DiagnosticSnapshotService;

public class SnapshotVerbHandler implements IVerbHandler<SnapshotCommand>
{
    public static final SnapshotVerbHandler instance = new SnapshotVerbHandler();
    private static final Logger logger = LoggerFactory.getLogger(SnapshotVerbHandler.class);

    public void doVerb(Message<SnapshotCommand> message)
    {
        SnapshotCommand command = message.payload;
        if (command.clear_snapshot)
        {
            StorageService.instance.clearSnapshot(command.snapshot_name, command.keyspace);
        }
        else if (DiagnosticSnapshotService.isDiagnosticSnapshotRequest(command))
        {
            DiagnosticSnapshotService.snapshot(command, message.from());
        }
        else
        {
            Keyspace.open(command.keyspace).getColumnFamilyStore(command.column_family).snapshot(command.snapshot_name);
        }

        logger.debug("Enqueuing response to snapshot request {} to {}", command.snapshot_name, message.from());
        MessagingService.instance().send(message.emptyResponse(), message.from());
    }
}
