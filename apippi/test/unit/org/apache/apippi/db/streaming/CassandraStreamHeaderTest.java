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
package org.apache.apippi.db.streaming;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.SchemaLoader;
import org.apache.apippi.Util;
import org.apache.apippi.cql3.statements.schema.CreateTableStatement;
import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.db.DecoratedKey;
import org.apache.apippi.db.Keyspace;
import org.apache.apippi.db.RowUpdateBuilder;
import org.apache.apippi.db.SerializationHeader;
import org.apache.apippi.db.compaction.CompactionManager;
import org.apache.apippi.db.streaming.apippiStreamHeader.apippiStreamHeaderSerializer;
import org.apache.apippi.dht.Murmur3Partitioner;
import org.apache.apippi.dht.Range;
import org.apache.apippi.dht.Token;
import org.apache.apippi.io.sstable.Component;
import org.apache.apippi.io.sstable.format.SSTableFormat;
import org.apache.apippi.io.sstable.format.SSTableReader;
import org.apache.apippi.io.sstable.format.big.BigFormat;
import org.apache.apippi.io.util.DataInputPlus;
import org.apache.apippi.schema.CompressionParams;
import org.apache.apippi.schema.KeyspaceParams;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.serializers.SerializationUtils;
import org.apache.apippi.utils.ByteBufferUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class apippiStreamHeaderTest
{
    public static final String KEYSPACE = "apippiStreamHeaderTest";
    public static final String CF_COMPRESSED = "compressed";

    private static SSTableReader sstable;
    private static ColumnFamilyStore store;

    @BeforeClass
    public static void defineSchemaAndPrepareSSTable()
    {
        SchemaLoader.prepareServer();
        SchemaLoader.createKeyspace(KEYSPACE,
                                    KeyspaceParams.simple(1),
                                    SchemaLoader.standardCFMD(KEYSPACE, CF_COMPRESSED).compression(CompressionParams.DEFAULT));

        Keyspace keyspace = Keyspace.open(KEYSPACE);
        store = keyspace.getColumnFamilyStore(CF_COMPRESSED);

        // insert data and compact to a single sstable
        CompactionManager.instance.disableAutoCompaction();
        for (int j = 0; j < 10; j++)
        {
            new RowUpdateBuilder(store.metadata(), j, String.valueOf(j))
            .clustering("0")
            .add("val", ByteBufferUtil.EMPTY_BYTE_BUFFER)
            .build()
            .applyUnsafe();
        }
        Util.flush(store);
        CompactionManager.instance.performMaximal(store, false);

        sstable = store.getLiveSSTables().iterator().next();
    }

    @Test
    public void transferedSizeWithCompressionTest()
    {
        // compression info is lazily initialized to reduce GC, compute size based on compressionMetadata
        apippiStreamHeader header = header(false, true);
        long transferedSize = header.size();
        assertEquals(transferedSize, header.calculateSize());

        // computing file chunks before sending over network, and verify size is the same
        header.compressionInfo.chunks();
        assertEquals(transferedSize, header.calculateSize());

        SerializationUtils.assertSerializationCycle(header, apippiStreamHeader.serializer);
    }

    @Test
    public void transferedSizeWithZeroCopyStreamingTest()
    {
        // verify all component on-disk length is used for ZCS
        apippiStreamHeader header = header(true, true);
        long transferedSize = header.size();
        assertEquals(ComponentManifest.create(sstable.descriptor).totalSize(), transferedSize);
        assertEquals(transferedSize, header.calculateSize());

        // verify that computing file chunks doesn't change transferred size for ZCS
        header.compressionInfo.chunks();
        assertEquals(transferedSize, header.calculateSize());

        SerializationUtils.assertSerializationCycle(header, apippiStreamHeader.serializer);
    }

    @Test
    public void transferedSizeWithoutCompressionTest()
    {
        // verify section size is used as transferred size
        apippiStreamHeader header = header(false, false);
        long transferedSize = header.size();
        assertNull(header.compressionInfo);
        assertEquals(sstable.uncompressedLength(), transferedSize);
        assertEquals(transferedSize, header.calculateSize());

        SerializationUtils.assertSerializationCycle(header, apippiStreamHeader.serializer);
    }

    private apippiStreamHeader header(boolean entireSSTable, boolean compressed)
    {
        List<Range<Token>> requestedRanges = Collections.singletonList(new Range<>(store.getPartitioner().getMinimumToken(), sstable.last.getToken()));
        requestedRanges = Range.normalize(requestedRanges);

        List<SSTableReader.PartitionPositionBounds> sections = sstable.getPositionsForRanges(requestedRanges);
        CompressionInfo compressionInfo = compressed ? CompressionInfo.newLazyInstance(sstable.getCompressionMetadata(), sections)
                                                     : null;

        TableMetadata metadata = store.metadata();
        SerializationHeader.Component serializationHeader = SerializationHeader.makeWithoutStats(metadata).toComponent();
        ComponentManifest componentManifest = entireSSTable ? ComponentManifest.create(sstable.descriptor) : null;
        DecoratedKey firstKey = entireSSTable ? sstable.first : null;
        return apippiStreamHeader.builder()
                                    .withSSTableFormat(SSTableFormat.Type.BIG)
                                    .withSSTableVersion(BigFormat.latestVersion)
                                    .withSSTableLevel(0)
                                    .withEstimatedKeys(10)
                                    .withCompressionInfo(compressionInfo)
                                    .withSections(sections)
                                    .isEntireSSTable(entireSSTable)
                                    .withComponentManifest(componentManifest)
                                    .withFirstKey(firstKey)
                                    .withSerializationHeader(serializationHeader)
                                    .withTableId(metadata.id)
                                    .build();
    }

    @Test
    public void serializerTest()
    {
        String ddl = "CREATE TABLE tbl (k INT PRIMARY KEY, v INT)";
        TableMetadata metadata = CreateTableStatement.parse(ddl, "ks").build();
        apippiStreamHeader header =
            apippiStreamHeader.builder()
                                 .withSSTableFormat(SSTableFormat.Type.BIG)
                                 .withSSTableVersion(BigFormat.latestVersion)
                                 .withSSTableLevel(0)
                                 .withEstimatedKeys(0)
                                 .withSections(Collections.emptyList())
                                 .withSerializationHeader(SerializationHeader.makeWithoutStats(metadata).toComponent())
                                 .withTableId(metadata.id)
                                 .build();

        SerializationUtils.assertSerializationCycle(header, apippiStreamHeader.serializer);
    }

    @Test
    public void serializerTest_EntireSSTableTransfer()
    {
        String ddl = "CREATE TABLE tbl (k INT PRIMARY KEY, v INT)";
        TableMetadata metadata = CreateTableStatement.parse(ddl, "ks").build();

        ComponentManifest manifest = new ComponentManifest(new LinkedHashMap<Component, Long>() {{ put(Component.DATA, 100L); }});

        apippiStreamHeader header =
            apippiStreamHeader.builder()
                                 .withSSTableFormat(SSTableFormat.Type.BIG)
                                 .withSSTableVersion(BigFormat.latestVersion)
                                 .withSSTableLevel(0)
                                 .withEstimatedKeys(0)
                                 .withSections(Collections.emptyList())
                                 .withSerializationHeader(SerializationHeader.makeWithoutStats(metadata).toComponent())
                                 .withComponentManifest(manifest)
                                 .isEntireSSTable(true)
                                 .withFirstKey(Murmur3Partitioner.instance.decorateKey(ByteBufferUtil.EMPTY_BYTE_BUFFER))
                                 .withTableId(metadata.id)
                                 .build();

        SerializationUtils.assertSerializationCycle(header, new TestableapippiStreamHeaderSerializer());
    }

    private static class TestableapippiStreamHeaderSerializer extends apippiStreamHeaderSerializer
    {
        @Override
        public apippiStreamHeader deserialize(DataInputPlus in, int version) throws IOException
        {
            return deserialize(in, version, tableId -> Murmur3Partitioner.instance);
        }
    }
}
