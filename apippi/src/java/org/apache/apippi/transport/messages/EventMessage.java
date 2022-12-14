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
package org.apache.apippi.transport.messages;

import io.netty.buffer.ByteBuf;

import org.apache.apippi.transport.Event;
import org.apache.apippi.transport.Message;
import org.apache.apippi.transport.ProtocolVersion;

public class EventMessage extends Message.Response
{
    public static final Message.Codec<EventMessage> codec = new Message.Codec<EventMessage>()
    {
        public EventMessage decode(ByteBuf body, ProtocolVersion version)
        {
            return new EventMessage(Event.deserialize(body, version));
        }

        public void encode(EventMessage msg, ByteBuf dest, ProtocolVersion version)
        {
            msg.event.serialize(dest, version);
        }

        public int encodedSize(EventMessage msg, ProtocolVersion version)
        {
            return msg.event.serializedSize(version);
        }
    };

    public final Event event;

    public EventMessage(Event event)
    {
        super(Message.Type.EVENT);
        this.event = event;
        this.setStreamId(-1);
    }

    @Override
    public String toString()
    {
        return "EVENT " + event;
    }
}
