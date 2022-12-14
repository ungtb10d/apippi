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
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.apippi.SchemaLoader;
import org.apache.apippi.Util;
import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.db.Keyspace;
import org.apache.apippi.db.RowUpdateBuilder;
import org.apache.apippi.db.compaction.CompactionManager;
import org.apache.apippi.db.compaction.OperationType;
import org.apache.apippi.db.lifecycle.LifecycleTransaction;
import org.apache.apippi.dht.ByteOrderedPartitioner;
import org.apache.apippi.dht.Range;
import org.apache.apippi.dht.Token;
import org.apache.apippi.io.sstable.Descriptor;
import org.apache.apippi.io.sstable.IndexSummaryManager;
import org.apache.apippi.io.sstable.IndexSummaryRedistribution;
import org.apache.apippi.io.sstable.SSTableUtils;
import org.apache.apippi.io.sstable.format.SSTableReader;
import org.apache.apippi.io.util.DataInputBuffer;
import org.apache.apippi.locator.InetAddressAndPort;
import org.apache.apippi.locator.RangesAtEndpoint;
import org.apache.apippi.net.AsyncStreamingOutputPlus;
import org.apache.apippi.net.BufferPoolAllocator;
import org.apache.apippi.net.MessagingService;
import org.apache.apippi.net.SharedDefaultFileRegion;
import org.apache.apippi.schema.KeyspaceParams;
import org.apache.apippi.schema.SchemaTestUtil;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.service.ActiveRepairService;
import org.apache.apippi.streaming.async.NettyStreamingConnectionFactory;
import org.apache.apippi.streaming.OutgoingStream;
import org.apache.apippi.streaming.PreviewKind;
import org.apache.apippi.streaming.SessionInfo;
import org.apache.apippi.streaming.StreamCoordinator;
import org.apache.apippi.streaming.StreamOperation;
import org.apache.apippi.streaming.StreamResultFuture;
import org.apache.apippi.streaming.StreamSession;
import org.apache.apippi.streaming.StreamSummary;
import org.apache.apippi.streaming.messages.StreamMessageHeader;
import org.apache.apippi.utils.ByteBufferUtil;
import org.apache.apippi.utils.FBUtilities;
import org.apache.apippi.utils.Throwables;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;

import static org.apache.apippi.service.ActiveRepairService.NO_PENDING_REPAIR;
import static org.apache.apippi.utils.TimeUUID.Generator.nextTimeUUID;
import static org.junit.Assert.assertTrue;

@RunWith(BMUnitRunner.class)
public class EntireSSTableStreamConcurrentComponentMutationTest
{
    public static final String KEYSPACE = "apippiEntireSSTableStreamLockTest";
    public static final String CF_STANDARD = "Standard1";

    private static final Callable<?> NO_OP = () -> null;

    private static SSTableReader sstable;
    private static Descriptor descriptor;
    private static ColumnFamilyStore store;
    private static RangesAtEndpoint rangesAtEndpoint;

    private static ExecutorService service;

    private static CountDownLatch latch = new CountDownLatch(1);

    @BeforeClass
    public static void defineSchemaAndPrepareSSTable()
    {
        SchemaLoader.prepareServer();
        SchemaLoader.createKeyspace(KEYSPACE,
                                    KeyspaceParams.simple(1),
                                    SchemaLoader.standardCFMD(KEYSPACE, CF_STANDARD));

        Keyspace keyspace = Keyspace.open(KEYSPACE);
        store = keyspace.getColumnFamilyStore("Standard1");

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

        Token start = ByteOrderedPartitioner.instance.getTokenFactory().fromString(Long.toHexString(0));
        Token end = ByteOrderedPartitioner.instance.getTokenFactory().fromString(Long.toHexString(100));
        rangesAtEndpoint = RangesAtEndpoint.toDummyList(Collections.singleton(new Range<>(start, end)));

        service = Executors.newFixedThreadPool(2);
    }

    @AfterClass
    public static void cleanup()
    {
        service.shutdown();
    }

    @Before
    public void init()
    {
        sstable = store.getLiveSSTables().iterator().next();
        descriptor = sstable.descriptor;
    }

    @After
    public void reset() throws IOException
    {
        latch = new CountDownLatch(1);
        // reset repair info to avoid test interfering each other
        descriptor.getMetadataSerializer().mutateRepairMetadata(descriptor, 0, ActiveRepairService.NO_PENDING_REPAIR, false);
    }

    @Test
    public void testStream() throws Throwable
    {
        testStreamWithConcurrentComponentMutation(NO_OP, NO_OP);
    }

    /**
     * Entire-sstable-streaming receiver will throw checksum validation failure because concurrent stats metadata
     * update causes the actual transfered file size to be different from the one in {@link ComponentManifest}
     */
    @Test
    public void testStreamWithStatsMutation() throws Throwable
    {
        testStreamWithConcurrentComponentMutation(() -> {

            Descriptor desc = sstable.descriptor;
            desc.getMetadataSerializer().mutate(desc, "testing", stats -> stats.mutateRepairedMetadata(0, nextTimeUUID(), false));

            return null;
        }, NO_OP);
    }

    @Test
    @BMRule(name = "Delay saving index summary, manifest may link partially written file if there is no lock",
            targetClass = "SSTableReader",
            targetMethod = "saveSummary(Descriptor, DecoratedKey, DecoratedKey, IndexSummary)",
            targetLocation = "AFTER INVOKE serialize",
            condition = "$descriptor.cfname.contains(\"Standard1\")",
            action = "org.apache.apippi.db.streaming.EntireSSTableStreamConcurrentComponentMutationTest.countDown();Thread.sleep(5000);")
    public void testStreamWithIndexSummaryRedistributionDelaySavingSummary() throws Throwable
    {
        testStreamWithConcurrentComponentMutation(() -> {
            // wait until new index summary is partially written
            latch.await(1, TimeUnit.MINUTES);
            return null;
        }, this::indexSummaryRedistribution);
    }

    // used by byteman
    private static void countDown()
    {
        latch.countDown();
    }

    private void testStreamWithConcurrentComponentMutation(Callable<?> runBeforeStreaming, Callable<?> runConcurrentWithStreaming) throws Throwable
    {
        ByteBuf serializedFile = Unpooled.buffer(8192);
        InetAddressAndPort peer = FBUtilities.getBroadcastAddressAndPort();
        StreamSession session = setupStreamingSessionForTest();
        Collection<OutgoingStream> outgoingStreams = store.getStreamManager().createOutgoingStreams(session, rangesAtEndpoint, NO_PENDING_REPAIR, PreviewKind.NONE);
        apippiOutgoingFile outgoingFile = (apippiOutgoingFile) Iterables.getOnlyElement(outgoingStreams);

        Future<?> streaming = executeAsync(() -> {
            runBeforeStreaming.call();

            try (AsyncStreamingOutputPlus out = new AsyncStreamingOutputPlus(createMockNettyChannel(serializedFile)))
            {
                outgoingFile.write(session, out, MessagingService.current_version);
                assertTrue(sstable.descriptor.getTemporaryFiles().isEmpty());
            }
            return null;
        });

        Future<?> concurrentMutations = executeAsync(runConcurrentWithStreaming);

        streaming.get(3, TimeUnit.MINUTES);
        concurrentMutations.get(3, TimeUnit.MINUTES);

        session.prepareReceiving(new StreamSummary(sstable.metadata().id, 1, 5104));
        StreamMessageHeader messageHeader = new StreamMessageHeader(sstable.metadata().id, peer, session.planId(), false, 0, 0, 0, null);

        try (DataInputBuffer in = new DataInputBuffer(serializedFile.nioBuffer(), false))
        {
            apippiStreamHeader header = apippiStreamHeader.serializer.deserialize(in, MessagingService.current_version);
            apippiEntireSSTableStreamReader reader = new apippiEntireSSTableStreamReader(messageHeader, header, session);
            SSTableReader streamedSSTable = Iterables.getOnlyElement(reader.read(in).finished());

            SSTableUtils.assertContentEquals(sstable, streamedSSTable);
        }
    }

    private boolean indexSummaryRedistribution() throws IOException
    {
        long nonRedistributingOffHeapSize = 0;
        long memoryPoolBytes = 1024 * 1024;

        // rewrite index summary file with new min/max index interval
        TableMetadata origin = store.metadata();
        SchemaTestUtil.announceTableUpdate(origin.unbuild().minIndexInterval(1).maxIndexInterval(2).build());

        try (LifecycleTransaction txn = store.getTracker().tryModify(sstable, OperationType.INDEX_SUMMARY))
        {
            IndexSummaryManager.redistributeSummaries(new IndexSummaryRedistribution(ImmutableMap.of(store.metadata().id, txn),
                                                                                     nonRedistributingOffHeapSize,
                                                                                     memoryPoolBytes));
        }

        // reset min/max index interval
        SchemaTestUtil.announceTableUpdate(origin);
        return true;
    }

    private Future<?> executeAsync(Callable<?> task)
    {
        return service.submit(() -> {
            try
            {
                task.call();
            }
            catch (Exception e)
            {
                throw Throwables.unchecked(e);
            }
        });
    }

    private EmbeddedChannel createMockNettyChannel(ByteBuf serializedFile)
    {
        WritableByteChannel wbc = new WritableByteChannel()
        {
            private boolean isOpen = true;
            public int write(ByteBuffer src)
            {
                int size = src.limit();
                serializedFile.writeBytes(src);
                return size;
            }

            public boolean isOpen()
            {
                return isOpen;
            }

            public void close()
            {
                isOpen = false;
            }
        };

        return new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
                {
                    if (msg instanceof BufferPoolAllocator.Wrapped)
                    {

                        ByteBuffer buf = ((BufferPoolAllocator.Wrapped) msg).adopt();
                        wbc.write(buf);
                    }
                    else
                    {
                        ((SharedDefaultFileRegion) msg).transferTo(wbc, 0);
                    }
                    super.write(ctx, msg, promise);
                }
            });
    }

    private StreamSession setupStreamingSessionForTest()
    {
        StreamCoordinator streamCoordinator = new StreamCoordinator(StreamOperation.BOOTSTRAP, 1, new NettyStreamingConnectionFactory(), false, false, null, PreviewKind.NONE);
        StreamResultFuture future = StreamResultFuture.createInitiator(nextTimeUUID(), StreamOperation.BOOTSTRAP, Collections.emptyList(), streamCoordinator);

        InetAddressAndPort peer = FBUtilities.getBroadcastAddressAndPort();
        streamCoordinator.addSessionInfo(new SessionInfo(peer, 0, peer, Collections.emptyList(), Collections.emptyList(), StreamSession.State.INITIALIZED));

        StreamSession session = streamCoordinator.getOrCreateOutboundSession(peer);
        session.init(future);
        return session;
    }
}
