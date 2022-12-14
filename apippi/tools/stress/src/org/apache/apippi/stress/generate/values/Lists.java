/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.apippi.stress.generate.values;


import java.util.Arrays;
import java.util.List;

import org.apache.apippi.db.marshal.ListType;

public class Lists<T> extends Generator<List<T>>
{
    final Generator<T> valueType;
    final T[] buffer;

    @SuppressWarnings("unchecked")
    public Lists(String name, Generator<T> valueType, GeneratorConfig config)
    {
        super(ListType.getInstance(valueType.type, true), config, name, List.class);
        this.valueType = valueType;
        buffer = (T[]) new Object[(int) sizeDistribution.maxValue()];
    }

    public void setSeed(long seed)
    {
        super.setSeed(seed);
        valueType.setSeed(seed * 31);
    }

    @Override
    public List<T> generate()
    {
        int size = (int) sizeDistribution.next();
        for (int i = 0 ; i < size ; i++)
            buffer[i] = valueType.generate();
        return com.google.common.collect.Lists.newArrayList(Arrays.copyOf(buffer, size));
    }
}
