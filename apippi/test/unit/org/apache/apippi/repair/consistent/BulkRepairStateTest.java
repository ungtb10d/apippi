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
package org.apache.apippi.repair.consistent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.apippi.dht.Murmur3Partitioner;
import org.apache.apippi.dht.Range;
import org.apache.apippi.dht.Token;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.assertEquals;

public class BulkRepairStateTest
{
    private static Token tk(long t)
    {
        return new Murmur3Partitioner.LongToken(t);
    }

    private static Range<Token> range(long left, long right)
    {
        return new Range<>(tk(left), tk(right));
    }

    private static List<Range<Token>> ranges(long... tokens)
    {
        assert tokens.length % 2 == 0;
        List<Range<Token>> ranges = new ArrayList<>();
        for (int i = 0; i < tokens.length; i += 2)
        {
            ranges.add(range(tokens[i], tokens[i + 1]));
        }
        return ranges;
    }

    private static RepairedState.Level level(Collection<Range<Token>> ranges, long repairedAt)
    {
        return new RepairedState.Level(ranges, repairedAt);
    }

    private static RepairedState.Section sect(Range<Token> range, long repairedAt)
    {
        return new RepairedState.Section(range, repairedAt);
    }

    private static RepairedState.Section sect(int l, int r, long time)
    {
        return sect(range(l, r), time);
    }

    private static <T> List<T> l(T... contents)
    {
        return Lists.newArrayList(contents);
    }

    @Test
    public void mergeOverlapping()
    {
        RepairedState repairs = new RepairedState();
        List<RepairedState.Level> list = new ArrayList<>();
        list.add(new RepairedState.Level(ranges(100, 300), 5));
        list.add(new RepairedState.Level(ranges(200, 400), 6));
        repairs.addAll(list);

        RepairedState.State state = repairs.state();
        assertEquals(l(level(ranges(200, 400), 6), level(ranges(100, 200), 5)), state.levels);
        assertEquals(l(sect(range(100, 200), 5), sect(range(200, 400), 6)), state.sections);
        assertEquals(ranges(100, 400), state.covered);
    }

    @Test
    public void mergeSameRange()
    {
        RepairedState repairs = new RepairedState();
        List<RepairedState.Level> list = new ArrayList<>();
        list.add(new RepairedState.Level(ranges(100, 400), 5));
        list.add(new RepairedState.Level(ranges(100, 400), 6));
        repairs.addAll(list);

        RepairedState.State state = repairs.state();
        assertEquals(l(level(ranges(100, 400), 6)), state.levels);
        assertEquals(l(sect(range(100, 400), 6)), state.sections);
        assertEquals(ranges(100, 400), state.covered);
    }

    @Test
    public void mergeLargeRange()
    {
        RepairedState repairs = new RepairedState();

        List<RepairedState.Level> list = new ArrayList<>();
        list.add(new RepairedState.Level(ranges(200, 300), 5));
        list.add(new RepairedState.Level(ranges(100, 400), 6));
        repairs.addAll(list);

        RepairedState.State state = repairs.state();
        assertEquals(l(level(ranges(100, 400), 6)), state.levels);
        assertEquals(l(sect(range(100, 400), 6)), state.sections);
        assertEquals(ranges(100, 400), state.covered);
    }

    @Test
    public void mergeSmallRange()
    {
        RepairedState repairs = new RepairedState();

        List<RepairedState.Level> list = new ArrayList<>();
        list.add(new RepairedState.Level(ranges(100, 400), 5));
        list.add(new RepairedState.Level(ranges(200, 300), 6));
        repairs.addAll(list);

        RepairedState.State state = repairs.state();
        assertEquals(l(level(ranges(200, 300), 6), level(ranges(100, 200, 300, 400), 5)), state.levels);
        assertEquals(l(sect(range(100, 200), 5), sect(range(200, 300), 6), sect(range(300, 400), 5)), state.sections);
        assertEquals(ranges(100, 400), state.covered);
    }

    @Test
    public void repairedAt()
    {
        RepairedState rs;

        // overlapping
        rs = new RepairedState();
        List<RepairedState.Level> list = new ArrayList<>();
        list.add(new RepairedState.Level(ranges(100, 300), 5));
        list.add(new RepairedState.Level(ranges(200, 400), 6));
        rs.addAll(list);

        assertEquals(5, rs.minRepairedAt(ranges(150, 250)));
        assertEquals(5, rs.minRepairedAt(ranges(150, 160)));
        assertEquals(5, rs.minRepairedAt(ranges(100, 200)));
        assertEquals(6, rs.minRepairedAt(ranges(200, 400)));
        assertEquals(0, rs.minRepairedAt(ranges(200, 401)));
        assertEquals(0, rs.minRepairedAt(ranges(99, 200)));
        assertEquals(0, rs.minRepairedAt(ranges(50, 450)));
        assertEquals(0, rs.minRepairedAt(ranges(50, 60)));
        assertEquals(0, rs.minRepairedAt(ranges(450, 460)));
    }
}

