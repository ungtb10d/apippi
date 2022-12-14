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

package org.apache.apippi.cql3.validation.entities.udfverify;

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.apippi.cql3.functions.types.TypeCodec;
import org.apache.apippi.cql3.functions.JavaUDF;
import org.apache.apippi.cql3.functions.UDFContext;
import org.apache.apippi.transport.ProtocolVersion;

/**
 * Used by {@link org.apache.apippi.cql3.validation.entities.UFVerifierTest}.
 */
public final class UseOfSynchronizedWithWaitLI extends JavaUDF
{
    public UseOfSynchronizedWithWaitLI(TypeCodec<Object> returnDataType, TypeCodec<Object>[] argDataTypes, UDFContext udfContext)
    {
        super(returnDataType, argDataTypes, udfContext);
    }

    protected Object executeAggregateImpl(ProtocolVersion protocolVersion, Object firstParam, List<ByteBuffer> params)
    {
        throw new UnsupportedOperationException();
    }

    protected ByteBuffer executeImpl(ProtocolVersion protocolVersion, List<ByteBuffer> params)
    {
        synchronized (this)
        {
            try
            {
                wait(1000L, 100);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
