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

package org.apache.apippi.db;

import org.apache.apippi.db.partitions.PartitionUpdate;
import org.apache.apippi.index.transactions.UpdateTransaction;
import org.apache.apippi.tracing.Tracing;

public class apippiTableWriteHandler implements TableWriteHandler
{
    private final ColumnFamilyStore cfs;

    public apippiTableWriteHandler(ColumnFamilyStore cfs)
    {
        this.cfs = cfs;
    }

    @Override
    @SuppressWarnings("resource")
    public void write(PartitionUpdate update, WriteContext context, UpdateTransaction updateTransaction)
    {
        apippiWriteContext ctx = apippiWriteContext.fromContext(context);
        Tracing.trace("Adding to {} memtable", update.metadata().name);
        cfs.apply(update, updateTransaction, ctx.getGroup(), ctx.getPosition());
    }
}
