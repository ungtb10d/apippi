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
package org.apache.apippi.index.sasi;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import com.googlecode.concurrenttrees.common.Iterables;

import org.apache.apippi.config.*;
import org.apache.apippi.cql3.Operator;
import org.apache.apippi.cql3.statements.schema.IndexTarget;
import org.apache.apippi.db.*;
import org.apache.apippi.db.compaction.CompactionManager;
import org.apache.apippi.db.compaction.OperationType;
import org.apache.apippi.db.filter.RowFilter;
import org.apache.apippi.db.lifecycle.Tracker;
import org.apache.apippi.db.marshal.AbstractType;
import org.apache.apippi.db.partitions.PartitionIterator;
import org.apache.apippi.db.partitions.PartitionUpdate;
import org.apache.apippi.db.rows.Row;
import org.apache.apippi.dht.Murmur3Partitioner;
import org.apache.apippi.exceptions.ConfigurationException;
import org.apache.apippi.exceptions.InvalidRequestException;
import org.apache.apippi.index.Index;
import org.apache.apippi.index.IndexRegistry;
import org.apache.apippi.index.SecondaryIndexBuilder;
import org.apache.apippi.index.TargetParser;
import org.apache.apippi.index.sasi.conf.ColumnIndex;
import org.apache.apippi.index.sasi.conf.IndexMode;
import org.apache.apippi.index.sasi.disk.OnDiskIndexBuilder.Mode;
import org.apache.apippi.index.sasi.disk.PerSSTableIndexWriter;
import org.apache.apippi.index.sasi.plan.QueryPlan;
import org.apache.apippi.index.transactions.IndexTransaction;
import org.apache.apippi.io.sstable.Descriptor;
import org.apache.apippi.io.sstable.format.SSTableFlushObserver;
import org.apache.apippi.io.sstable.format.SSTableReader;
import org.apache.apippi.notifications.*;
import org.apache.apippi.schema.ColumnMetadata;
import org.apache.apippi.schema.IndexMetadata;
import org.apache.apippi.schema.Schema;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.utils.FBUtilities;
import org.apache.apippi.utils.Pair;
import org.apache.apippi.utils.concurrent.OpOrder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class SASIIndex implements Index, INotificationConsumer
{
    public final static String USAGE_WARNING = "SASI indexes are experimental and are not recommended for production use.";

    private static class SASIIndexBuildingSupport implements IndexBuildingSupport
    {
        public SecondaryIndexBuilder getIndexBuildTask(ColumnFamilyStore cfs,
                                                       Set<Index> indexes,
                                                       Collection<SSTableReader> sstablesToRebuild)
        {
            NavigableMap<SSTableReader, Map<ColumnMetadata, ColumnIndex>> sstables = new TreeMap<>(SSTableReader.idComparator);

            indexes.stream()
                   .filter((i) -> i instanceof SASIIndex)
                   .forEach((i) -> {
                       SASIIndex sasi = (SASIIndex) i;
                       sasi.index.dropData(sstablesToRebuild);
                       sstablesToRebuild.stream()
                                        .filter((sstable) -> !sasi.index.hasSSTable(sstable))
                                        .forEach((sstable) -> {
                                            Map<ColumnMetadata, ColumnIndex> toBuild = sstables.get(sstable);
                                            if (toBuild == null)
                                                sstables.put(sstable, (toBuild = new HashMap<>()));

                                            toBuild.put(sasi.index.getDefinition(), sasi.index);
                                        });
                   });

            return new SASIIndexBuilder(cfs, sstables);
        }
    }

    private static final SASIIndexBuildingSupport INDEX_BUILDER_SUPPORT = new SASIIndexBuildingSupport();

    private final ColumnFamilyStore baseCfs;
    private final IndexMetadata config;
    private final ColumnIndex index;

    public SASIIndex(ColumnFamilyStore baseCfs, IndexMetadata config)
    {
        this.baseCfs = baseCfs;
        this.config = config;

        ColumnMetadata column = TargetParser.parse(baseCfs.metadata(), config).left;
        this.index = new ColumnIndex(baseCfs.metadata().partitionKeyType, column, config);

        Tracker tracker = baseCfs.getTracker();
        tracker.subscribe(this);

        SortedMap<SSTableReader, Map<ColumnMetadata, ColumnIndex>> toRebuild = new TreeMap<>(SSTableReader.idComparator);

        for (SSTableReader sstable : index.init(tracker.getView().liveSSTables()))
        {
            Map<ColumnMetadata, ColumnIndex> perSSTable = toRebuild.get(sstable);
            if (perSSTable == null)
                toRebuild.put(sstable, (perSSTable = new HashMap<>()));

            perSSTable.put(index.getDefinition(), index);
        }

        CompactionManager.instance.submitIndexBuild(new SASIIndexBuilder(baseCfs, toRebuild));
    }

    /**
     * Called via reflection at {@link IndexMetadata#validateCustomIndexOptions}
     */
    public static Map<String, String> validateOptions(Map<String, String> options, TableMetadata metadata)
    {
        if (!(metadata.partitioner instanceof Murmur3Partitioner))
            throw new ConfigurationException("SASI only supports Murmur3Partitioner.");

        String targetColumn = options.get("target");
        if (targetColumn == null)
            throw new ConfigurationException("unknown target column");

        Pair<ColumnMetadata, IndexTarget.Type> target = TargetParser.parse(metadata, targetColumn);
        if (target == null)
            throw new ConfigurationException("failed to retrieve target column for: " + targetColumn);

        if (target.left.isComplex())
            throw new ConfigurationException("complex columns are not yet supported by SASI");

        if (target.left.isPartitionKey())
            throw new ConfigurationException("partition key columns are not yet supported by SASI");

        IndexMode.validateAnalyzer(options, target.left);

        IndexMode mode = IndexMode.getMode(target.left, options);
        if (mode.mode == Mode.SPARSE)
        {
            if (mode.isLiteral)
                throw new ConfigurationException("SPARSE mode is only supported on non-literal columns.");

            if (mode.isAnalyzed)
                throw new ConfigurationException("SPARSE mode doesn't support analyzers.");
        }

        return Collections.emptyMap();
    }

    public void register(IndexRegistry registry)
    {
        registry.registerIndex(this);
    }

    public IndexMetadata getIndexMetadata()
    {
        return config;
    }

    public Callable<?> getInitializationTask()
    {
        return null;
    }

    public Callable<?> getMetadataReloadTask(IndexMetadata indexMetadata)
    {
        return null;
    }

    public Callable<?> getBlockingFlushTask()
    {
        return null; // SASI indexes are flushed along side memtable
    }

    public Callable<?> getInvalidateTask()
    {
        return getTruncateTask(FBUtilities.timestampMicros());
    }

    public Callable<?> getTruncateTask(long truncatedAt)
    {
        return () -> {
            index.dropData(truncatedAt);
            return null;
        };
    }

    public boolean shouldBuildBlocking()
    {
        return true;
    }

    public Optional<ColumnFamilyStore> getBackingTable()
    {
        return Optional.empty();
    }

    public boolean indexes(RegularAndStaticColumns columns)
    {
        return columns.contains(index.getDefinition());
    }

    public boolean dependsOn(ColumnMetadata column)
    {
        return index.getDefinition().compareTo(column) == 0;
    }

    public boolean supportsExpression(ColumnMetadata column, Operator operator)
    {
        return dependsOn(column) && index.supports(operator);
    }

    public AbstractType<?> customExpressionValueType()
    {
        return null;
    }

    public RowFilter getPostIndexQueryFilter(RowFilter filter)
    {
        return filter.withoutExpressions();
    }

    public long getEstimatedResultRows()
    {
        // this is temporary (until proper QueryPlan is integrated into apippi)
        // and allows us to priority SASI indexes if any in the query since they
        // are going to be more efficient, to query and intersect, than built-in indexes.
        return Long.MIN_VALUE;
    }

    public void validate(PartitionUpdate update) throws InvalidRequestException
    {}

    @Override
    public boolean supportsReplicaFilteringProtection(RowFilter rowFilter)
    {
        return false;
    }

    public Indexer indexerFor(DecoratedKey key, RegularAndStaticColumns columns, int nowInSec, WriteContext context, IndexTransaction.Type transactionType)
    {
        return new Indexer()
        {
            public void begin()
            {}

            public void partitionDelete(DeletionTime deletionTime)
            {}

            public void rangeTombstone(RangeTombstone tombstone)
            {}

            public void insertRow(Row row)
            {
                if (isNewData())
                    adjustMemtableSize(index.index(key, row), apippiWriteContext.fromContext(context).getGroup());
            }

            public void updateRow(Row oldRow, Row newRow)
            {
                insertRow(newRow);
            }

            public void removeRow(Row row)
            {}

            public void finish()
            {}

            // we are only interested in the data from Memtable
            // everything else is going to be handled by SSTableWriter observers
            private boolean isNewData()
            {
                return transactionType == IndexTransaction.Type.UPDATE;
            }

            public void adjustMemtableSize(long additionalSpace, OpOrder.Group opGroup)
            {
                baseCfs.getTracker().getView().getCurrentMemtable().markExtraOnHeapUsed(additionalSpace, opGroup);
            }
        };
    }

    public Searcher searcherFor(ReadCommand command) throws InvalidRequestException
    {
        TableMetadata config = command.metadata();
        ColumnFamilyStore cfs = Schema.instance.getColumnFamilyStoreInstance(config.id);
        return controller -> new QueryPlan(cfs, command, DatabaseDescriptor.getRangeRpcTimeout(MILLISECONDS)).execute(controller);
    }

    public SSTableFlushObserver getFlushObserver(Descriptor descriptor, OperationType opType)
    {
        return newWriter(baseCfs.metadata().partitionKeyType, descriptor, Collections.singletonMap(index.getDefinition(), index), opType);
    }

    public BiFunction<PartitionIterator, ReadCommand, PartitionIterator> postProcessorFor(ReadCommand command)
    {
        return (partitionIterator, readCommand) -> partitionIterator;
    }

    public IndexBuildingSupport getBuildTaskSupport()
    {
        return INDEX_BUILDER_SUPPORT;
    }

    public void handleNotification(INotification notification, Object sender)
    {
        // unfortunately, we can only check the type of notification via instanceof :(
        if (notification instanceof SSTableAddedNotification)
        {
            SSTableAddedNotification notice = (SSTableAddedNotification) notification;
            index.update(Collections.<SSTableReader>emptyList(), Iterables.toList(notice.added));
        }
        else if (notification instanceof SSTableListChangedNotification)
        {
            SSTableListChangedNotification notice = (SSTableListChangedNotification) notification;
            index.update(notice.removed, notice.added);
        }
        else if (notification instanceof MemtableRenewedNotification)
        {
            index.switchMemtable();
        }
        else if (notification instanceof MemtableSwitchedNotification)
        {
            index.switchMemtable(((MemtableSwitchedNotification) notification).memtable);
        }
        else if (notification instanceof MemtableDiscardedNotification)
        {
            index.discardMemtable(((MemtableDiscardedNotification) notification).memtable);
        }
    }

    public ColumnIndex getIndex()
    {
        return index;
    }

    protected static PerSSTableIndexWriter newWriter(AbstractType<?> keyValidator,
                                                     Descriptor descriptor,
                                                     Map<ColumnMetadata, ColumnIndex> indexes,
                                                     OperationType opType)
    {
        return new PerSSTableIndexWriter(keyValidator, descriptor, opType, indexes);
    }
}
