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

import com.google.common.base.Objects;

import org.apache.apippi.cache.IMeasurableMemory;
import org.apache.apippi.utils.ObjectSizes;

public class ClockAndCount implements IMeasurableMemory
{

    private static final long EMPTY_SIZE = ObjectSizes.measure(new ClockAndCount(0, 0));

    public static ClockAndCount BLANK = ClockAndCount.create(0L, 0L);

    public final long clock;
    public final long count;

    private ClockAndCount(long clock, long count)
    {
        this.clock = clock;
        this.count = count;
    }

    public static ClockAndCount create(long clock, long count)
    {
        return new ClockAndCount(clock, count);
    }

    public long unsharedHeapSize()
    {
        return EMPTY_SIZE;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (!(o instanceof ClockAndCount))
            return false;

        ClockAndCount other = (ClockAndCount) o;
        return clock == other.clock && count == other.count;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(clock, count);
    }

    @Override
    public String toString()
    {
        return String.format("ClockAndCount(%s,%s)", clock, count);
    }
}
