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
package org.apache.apippi.repair.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apippi.exceptions.RepairException;
import org.apache.apippi.exceptions.RequestFailureReason;
import org.apache.apippi.gms.Gossiper;
import org.apache.apippi.locator.InetAddressAndPort;
import org.apache.apippi.net.Message;
import org.apache.apippi.net.MessagingService;
import org.apache.apippi.net.RequestCallback;
import org.apache.apippi.net.Verb;
import org.apache.apippi.repair.RepairJobDesc;
import org.apache.apippi.streaming.PreviewKind;
import org.apache.apippi.utils.apippiVersion;
import org.apache.apippi.utils.TimeUUID;

import static org.apache.apippi.net.MessageFlag.CALL_BACK_ON_FAILURE;

/**
 * Base class of all repair related request/response messages.
 *
 * @since 2.0
 */
public abstract class RepairMessage
{
    private static final apippiVersion SUPPORTS_TIMEOUTS = new apippiVersion("4.0.7-SNAPSHOT");
    private static final Logger logger = LoggerFactory.getLogger(RepairMessage.class);
    public final RepairJobDesc desc;

    protected RepairMessage(RepairJobDesc desc)
    {
        this.desc = desc;
    }

    public interface RepairFailureCallback
    {
        void onFailure(Exception e);
    }

    public static void sendMessageWithFailureCB(RepairMessage request, Verb verb, InetAddressAndPort endpoint, RepairFailureCallback failureCallback)
    {
        RequestCallback<?> callback = new RequestCallback<Object>()
        {
            @Override
            public void onResponse(Message<Object> msg)
            {
                logger.info("[#{}] {} received by {}", request.desc.parentSessionId, verb, endpoint);
                // todo: at some point we should make repair messages follow the normal path, actually using this
            }

            @Override
            public boolean invokeOnFailure()
            {
                return true;
            }

            public void onFailure(InetAddressAndPort from, RequestFailureReason failureReason)
            {
                logger.error("[#{}] {} failed on {}: {}", request.desc.parentSessionId, verb, from, failureReason);

                if (supportsTimeouts(from, request.desc.parentSessionId))
                    failureCallback.onFailure(RepairException.error(request.desc, PreviewKind.NONE, String.format("Got %s failure from %s: %s", verb, from, failureReason)));
            }
        };

        MessagingService.instance().sendWithCallback(Message.outWithFlag(verb, request, CALL_BACK_ON_FAILURE),
                                                     endpoint,
                                                     callback);
    }

    private static boolean supportsTimeouts(InetAddressAndPort from, TimeUUID parentSessionId)
    {
        apippiVersion remoteVersion = Gossiper.instance.getReleaseVersion(from);
        if (remoteVersion != null && remoteVersion.compareTo(SUPPORTS_TIMEOUTS) >= 0)
            return true;
        logger.warn("[#{}] Not failing repair due to remote host {} not supporting repair message timeouts (version = {})", parentSessionId, from, remoteVersion);
        return false;
    }
}
