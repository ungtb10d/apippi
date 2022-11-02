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

package org.apache.apippi.distributed.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.apippi.cql3.CQLStatement;
import org.apache.apippi.cql3.QueryOptions;
import org.apache.apippi.cql3.QueryProcessor;
import org.apache.apippi.db.ConsistencyLevel;
import org.apache.apippi.distributed.api.IIsolatedExecutor;
import org.apache.apippi.service.ClientState;
import org.apache.apippi.service.ClientWarn;
import org.apache.apippi.service.QueryState;
import org.apache.apippi.transport.ProtocolVersion;
import org.apache.apippi.transport.messages.ResultMessage;
import org.apache.apippi.utils.ByteBufferUtil;
import org.apache.apippi.utils.FBUtilities;

import static org.apache.apippi.utils.Clock.Global.nanoTime;

public class Query implements IIsolatedExecutor.SerializableCallable<Object[][]>
{
    private static final long serialVersionUID = 1L;

    final String query;
    final long timestamp;
    final org.apache.apippi.distributed.api.ConsistencyLevel commitConsistencyOrigin;
    final org.apache.apippi.distributed.api.ConsistencyLevel serialConsistencyOrigin;
    final Object[] boundValues;

    public Query(String query, long timestamp, org.apache.apippi.distributed.api.ConsistencyLevel commitConsistencyOrigin, org.apache.apippi.distributed.api.ConsistencyLevel serialConsistencyOrigin, Object[] boundValues)
    {
        this.query = query;
        this.timestamp = timestamp;
        this.commitConsistencyOrigin = commitConsistencyOrigin;
        this.serialConsistencyOrigin = serialConsistencyOrigin;
        this.boundValues = boundValues;
    }

    public Object[][] call()
    {
        ConsistencyLevel commitConsistency = toapippiCL(commitConsistencyOrigin);
        ConsistencyLevel serialConsistency = serialConsistencyOrigin == null ? null : toapippiCL(serialConsistencyOrigin);
        ClientState clientState = Coordinator.makeFakeClientState();
        CQLStatement prepared = QueryProcessor.getStatement(query, clientState);
        List<ByteBuffer> boundBBValues = new ArrayList<>();
        for (Object boundValue : boundValues)
            boundBBValues.add(ByteBufferUtil.objectToBytes(boundValue));

        prepared.validate(QueryState.forInternalCalls().getClientState());

        // Start capturing warnings on this thread. Note that this will implicitly clear out any previous
        // warnings as it sets a new State instance on the ThreadLocal.
        ClientWarn.instance.captureWarnings();

        ResultMessage res = prepared.execute(QueryState.forInternalCalls(),
                                             QueryOptions.create(commitConsistency,
                                                                 boundBBValues,
                                                                 false,
                                                                 Integer.MAX_VALUE,
                                                                 null,
                                                                 serialConsistency,
                                                                 ProtocolVersion.V4,
                                                                 null,
                                                                 timestamp,
                                                                 FBUtilities.nowInSeconds()),
                                             nanoTime());

        // Collect warnings reported during the query.
        if (res != null)
            res.setWarnings(ClientWarn.instance.getWarnings());

        return RowUtil.toQueryResult(res).toObjectArrays();
    }

    public String toString()
    {
        return String.format(query.replaceAll("\\?", "%s") + " AT " + commitConsistencyOrigin, boundValues);
    }

    static org.apache.apippi.db.ConsistencyLevel toapippiCL(org.apache.apippi.distributed.api.ConsistencyLevel cl)
    {
        return org.apache.apippi.db.ConsistencyLevel.fromCode(cl.ordinal());
    }

    static final org.apache.apippi.distributed.api.ConsistencyLevel[] API_CLs = org.apache.apippi.distributed.api.ConsistencyLevel.values();
    static org.apache.apippi.distributed.api.ConsistencyLevel fromapippiCL(org.apache.apippi.db.ConsistencyLevel cl)
    {
        return API_CLs[cl.ordinal()];
    }

}