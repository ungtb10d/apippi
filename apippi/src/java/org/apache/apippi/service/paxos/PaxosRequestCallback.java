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

package org.apache.apippi.service.paxos;

import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apippi.config.apippiRelevantProperties;
import org.apache.apippi.exceptions.RequestFailureReason;
import org.apache.apippi.exceptions.WriteTimeoutException;
import org.apache.apippi.locator.InetAddressAndPort;
import org.apache.apippi.net.Message;
import org.apache.apippi.service.FailureRecordingCallback;

import static org.apache.apippi.exceptions.RequestFailureReason.TIMEOUT;
import static org.apache.apippi.exceptions.RequestFailureReason.UNKNOWN;
import static org.apache.apippi.utils.FBUtilities.getBroadcastAddressAndPort;

public abstract class PaxosRequestCallback<T> extends FailureRecordingCallback<T>
{
    private static final Logger logger = LoggerFactory.getLogger(PaxosRequestCallback.class);
    private static final boolean USE_SELF_EXECUTION = apippiRelevantProperties.PAXOS_EXECUTE_ON_SELF.getBoolean();

    protected abstract void onResponse(T response, InetAddressAndPort from);

    @Override
    public void onResponse(Message<T> message)
    {
        onResponse(message.payload, message.from());
    }

    protected <I> void executeOnSelf(I parameter, BiFunction<I, InetAddressAndPort, T> execute)
    {
        T response;
        try
        {
            response = execute.apply(parameter, getBroadcastAddressAndPort());
            if (response == null)
                return;
        }
        catch (Exception ex)
        {
            RequestFailureReason reason = UNKNOWN;
            if (ex instanceof WriteTimeoutException) reason = TIMEOUT;
            else logger.error("Failed to apply {} locally", parameter, ex);

            onFailure(getBroadcastAddressAndPort(), reason);
            return;
        }

        onResponse(response, getBroadcastAddressAndPort());
    }

    static boolean shouldExecuteOnSelf(InetAddressAndPort replica)
    {
        return USE_SELF_EXECUTION && replica.equals(getBroadcastAddressAndPort());
    }
}
