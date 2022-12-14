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
package org.apache.apippi.db.marshal;

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.apippi.cql3.Term;
import org.apache.apippi.exceptions.ConfigurationException;
import org.apache.apippi.exceptions.SyntaxException;
import org.apache.apippi.serializers.TypeSerializer;
import org.apache.apippi.serializers.MarshalException;
import org.apache.apippi.transport.ProtocolVersion;

/**
 * A fake type that is only used for parsing type strings that include frozen types.
 */
public class FrozenType extends AbstractType<Void>
{
    protected FrozenType()
    {
        super(ComparisonType.NOT_COMPARABLE);
    }

    public static AbstractType<?> getInstance(TypeParser parser) throws ConfigurationException, SyntaxException
    {
        List<AbstractType<?>> innerTypes = parser.getTypeParameters();
        if (innerTypes.size() != 1)
            throw new SyntaxException("FrozenType() only accepts one parameter");

        AbstractType<?> innerType = innerTypes.get(0);
        return innerType.freeze();
    }

    public <V> String getString(V value, ValueAccessor<V> accessor)
    {
        throw new UnsupportedOperationException();
    }

    public ByteBuffer fromString(String source) throws MarshalException
    {
        throw new UnsupportedOperationException();
    }

    public Term fromJSONObject(Object parsed) throws MarshalException
    {
        throw new UnsupportedOperationException();
    }

    public String toJSONString(ByteBuffer buffer, ProtocolVersion protocolVersion)
    {
        throw new UnsupportedOperationException();
    }

    public TypeSerializer<Void> getSerializer()
    {
        throw new UnsupportedOperationException();
    }
}
