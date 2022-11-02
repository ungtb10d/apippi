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

package org.apache.apippi.cql3;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.apippi.cql3.functions.UDHelper;
import org.apache.apippi.db.marshal.AbstractType;
import org.apache.apippi.db.marshal.AsciiType;
import org.apache.apippi.db.marshal.BooleanType;
import org.apache.apippi.db.marshal.ByteType;
import org.apache.apippi.db.marshal.BytesType;
import org.apache.apippi.db.marshal.CounterColumnType;
import org.apache.apippi.db.marshal.DateType;
import org.apache.apippi.db.marshal.DecimalType;
import org.apache.apippi.db.marshal.DoubleType;
import org.apache.apippi.db.marshal.FloatType;
import org.apache.apippi.db.marshal.InetAddressType;
import org.apache.apippi.db.marshal.Int32Type;
import org.apache.apippi.db.marshal.IntegerType;
import org.apache.apippi.db.marshal.LongType;
import org.apache.apippi.db.marshal.ReversedType;
import org.apache.apippi.db.marshal.ShortType;
import org.apache.apippi.db.marshal.SimpleDateType;
import org.apache.apippi.db.marshal.TimeType;
import org.apache.apippi.db.marshal.TimeUUIDType;
import org.apache.apippi.db.marshal.TimestampType;
import org.apache.apippi.db.marshal.UTF8Type;
import org.apache.apippi.db.marshal.UUIDType;
import org.apache.apippi.db.marshal.ValueAccessor;
import org.apache.apippi.serializers.MarshalException;
import org.apache.apippi.serializers.TypeSerializer;
import org.apache.apippi.utils.ByteBufferUtil;

public class UDHelperTest
{
    static class UFTestCustomType extends AbstractType<String>
    {
        protected UFTestCustomType()
        {
            super(ComparisonType.CUSTOM);
        }

        public ByteBuffer fromString(String source) throws MarshalException
        {
            return ByteBuffer.wrap(source.getBytes());
        }

        public Term fromJSONObject(Object parsed) throws MarshalException
        {
            throw new UnsupportedOperationException();
        }

        public TypeSerializer<String> getSerializer()
        {
            return UTF8Type.instance.getSerializer();
        }

        public <VL, VR> int compareCustom(VL left, ValueAccessor<VL> accessorL, VR right, ValueAccessor<VR> accessorR)
        {
            return ValueAccessor.compare(left, accessorL, right, accessorR);
        }
    }

    @Test
    public void testEmptyVariableLengthTypes()
    {
        AbstractType<?>[] types = new AbstractType<?>[]{
                                                       AsciiType.instance,
                                                       BytesType.instance,
                                                       UTF8Type.instance,
                                                       new UFTestCustomType()
        };

        for (AbstractType<?> type : types)
        {
            Assert.assertFalse("type " + type.getClass().getName(),
                               UDHelper.isNullOrEmpty(type, ByteBufferUtil.EMPTY_BYTE_BUFFER));
        }
    }

    @Test
    public void testNonEmptyPrimitiveTypes()
    {
        AbstractType<?>[] types = new AbstractType<?>[]{
                                                       TimeType.instance,
                                                       SimpleDateType.instance,
                                                       ByteType.instance,
                                                       ShortType.instance
        };

        for (AbstractType<?> type : types)
        {
            try
            {
                type.getSerializer().validate(ByteBufferUtil.EMPTY_BYTE_BUFFER);
                Assert.fail(type.getClass().getSimpleName());
            }
            catch (MarshalException e)
            {
                //
            }
        }
    }

    @Test
    public void testEmptiableTypes()
    {
        AbstractType<?>[] types = new AbstractType<?>[]{
                                                       BooleanType.instance,
                                                       CounterColumnType.instance,
                                                       DateType.instance,
                                                       DecimalType.instance,
                                                       DoubleType.instance,
                                                       FloatType.instance,
                                                       InetAddressType.instance,
                                                       Int32Type.instance,
                                                       IntegerType.instance,
                                                       LongType.instance,
                                                       TimestampType.instance,
                                                       TimeUUIDType.instance,
                                                       UUIDType.instance
        };

        for (AbstractType<?> type : types)
        {
            Assert.assertTrue(type.getClass().getSimpleName(), UDHelper.isNullOrEmpty(type, ByteBufferUtil.EMPTY_BYTE_BUFFER));
            Assert.assertTrue("reversed " + type.getClass().getSimpleName(),
                              UDHelper.isNullOrEmpty(ReversedType.getInstance(type), ByteBufferUtil.EMPTY_BYTE_BUFFER));
        }
    }
}
