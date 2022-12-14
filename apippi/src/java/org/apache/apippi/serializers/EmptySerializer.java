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

package org.apache.apippi.serializers;

import java.nio.ByteBuffer;

import org.apache.apippi.db.marshal.ValueAccessor;
import org.apache.apippi.utils.ByteBufferUtil;

public class EmptySerializer extends TypeSerializer<Void>
{
    public static final EmptySerializer instance = new EmptySerializer();

    public <V> Void deserialize(V value, ValueAccessor<V> accessor)
    {
        validate(value, accessor);
        return null;
    }

    public ByteBuffer serialize(Void value)
    {
        return ByteBufferUtil.EMPTY_BYTE_BUFFER;
    }

    public <V> void validate(V value, ValueAccessor<V> accessor) throws MarshalException
    {
        if (!accessor.isEmpty(value))
            throw new MarshalException("EmptyType only accept empty values");
    }

    public String toString(Void value)
    {
        return "";
    }

    public Class<Void> getType()
    {
        return Void.class;
    }
}
