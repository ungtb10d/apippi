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
package org.apache.apippi.db.compaction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.SchemaLoader;
import org.apache.apippi.Util;
import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.db.Keyspace;
import org.apache.apippi.db.RowUpdateBuilder;
import org.apache.apippi.exceptions.ConfigurationException;
import org.apache.apippi.schema.CompactionParams;
import org.apache.apippi.schema.KeyspaceParams;
import org.apache.apippi.utils.ByteBufferUtil;

import static org.junit.Assert.assertEquals;


public class OneCompactionTest
{
    public static final String KEYSPACE1 = "OneCompactionTest";
    private static final String CF_STANDARD1 = "Standard1";
    private static final String CF_STANDARD2 = "Standard2";

    @BeforeClass
    public static void defineSchema() throws ConfigurationException
    {
        SchemaLoader.prepareServer();
        SchemaLoader.createKeyspace(KEYSPACE1,
                                    KeyspaceParams.simple(1),
                                    SchemaLoader.standardCFMD(KEYSPACE1, CF_STANDARD1)
                                                .compaction(CompactionParams.lcs(Collections.singletonMap("sstable_size_in_mb", "1"))),
                                    SchemaLoader.standardCFMD(KEYSPACE1, CF_STANDARD2));
    }

    private void testCompaction(String columnFamilyName, int insertsPerTable)
    {
        CompactionManager.instance.disableAutoCompaction();

        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore store = keyspace.getColumnFamilyStore(columnFamilyName);

        Set<String> inserted = new HashSet<>();
        for (int j = 0; j < insertsPerTable; j++) {
            String key = String.valueOf(j);
            new RowUpdateBuilder(store.metadata(), j, key)
                .clustering("0")
                .add("val", ByteBufferUtil.EMPTY_BYTE_BUFFER)
                .build()
                .applyUnsafe();

            inserted.add(key);
            Util.flush(store);
            assertEquals(inserted.size(), Util.getAll(Util.cmd(store).build()).size());
        }
        CompactionManager.instance.performMaximal(store, false);
        assertEquals(1, store.getLiveSSTables().size());
    }

    @Test
    public void testCompaction1()
    {
        testCompaction("Standard1", 1);
    }

    @Test
    public void testCompaction2()
    {
        testCompaction("Standard2", 2);
    }
}
