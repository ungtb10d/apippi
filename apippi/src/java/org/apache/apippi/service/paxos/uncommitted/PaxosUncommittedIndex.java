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

package org.apache.apippi.service.paxos.uncommitted;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Callables;

import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.cql3.Operator;
import org.apache.apippi.db.*;
import org.apache.apippi.db.filter.ColumnFilter;
import org.apache.apippi.db.filter.RowFilter;
import org.apache.apippi.db.lifecycle.View;
import org.apache.apippi.db.marshal.AbstractType;
import org.apache.apippi.db.memtable.Memtable;
import org.apache.apippi.db.partitions.*;
import org.apache.apippi.db.rows.Row;
import org.apache.apippi.dht.Range;
import org.apache.apippi.dht.Token;
import org.apache.apippi.exceptions.InvalidRequestException;
import org.apache.apippi.index.Index;
import org.apache.apippi.index.IndexRegistry;
import org.apache.apippi.index.transactions.IndexTransaction;
import org.apache.apippi.io.sstable.format.SSTableReadsListener;
import org.apache.apippi.schema.ColumnMetadata;
import org.apache.apippi.schema.IndexMetadata;
import org.apache.apippi.schema.Indexes;
import org.apache.apippi.schema.TableId;
import org.apache.apippi.utils.CloseableIterator;

import static java.util.Collections.*;
import static org.apache.apippi.schema.SchemaConstants.SYSTEM_KEYSPACE_NAME;
import static org.apache.apippi.service.paxos.PaxosState.ballotTracker;
import static org.apache.apippi.service.paxos.PaxosState.uncommittedTracker;

/**
 * A 2i implementation made specifically for system.paxos that listens for changes to paxos state by interpreting
 * mutations against system.paxos and updates the uncommitted tracker accordingly.
 *
 * No read expressions are supported by the index.
 *
 * This is implemented as a 2i so it can piggy back off the commit log and paxos table flushes, and avoid worrying
 * about implementing a parallel log/flush system for the tracker and potential bugs there. It also means we don't
 * have to worry about cases where the tracker can become out of sync with the paxos table due to failure/edge cases
 * outside of the PaxosTableState class itself.
 */
public class PaxosUncommittedIndex implements Index, PaxosUncommittedTracker.UpdateSupplier
{
    public final ColumnFamilyStore baseCfs;
    protected IndexMetadata metadata;

    private static final DataRange FULL_RANGE = DataRange.allData(DatabaseDescriptor.getPartitioner());
    private final ColumnFilter memtableColumnFilter;

    public PaxosUncommittedIndex(ColumnFamilyStore baseTable, IndexMetadata metadata)
    {
        Preconditions.checkState(baseTable.metadata.keyspace.equals(SYSTEM_KEYSPACE_NAME));
        Preconditions.checkState(baseTable.metadata.name.equals(SystemKeyspace.PAXOS));

        this.baseCfs = baseTable;
        this.metadata = metadata;

        this.memtableColumnFilter = ColumnFilter.all(baseTable.metadata.get());
        PaxosUncommittedTracker.unsafSetUpdateSupplier(this);
    }

    public static IndexMetadata indexMetadata()
    {
        Map<String, String> options = new HashMap<>();
        options.put("class_name", PaxosUncommittedIndex.class.getName());
        options.put("target", "");
        return IndexMetadata.fromSchemaMetadata("PaxosUncommittedIndex", IndexMetadata.Kind.CUSTOM, options);
    }

    public static Indexes indexes()
    {
        return Indexes.builder().add(indexMetadata()).build();
    }

    public Callable<?> getInitializationTask()
    {
        return Callables.returning(null);
    }

    public IndexMetadata getIndexMetadata()
    {
        return metadata;
    }

    public Callable<?> getMetadataReloadTask(IndexMetadata indexMetadata)
    {
        return Callables.returning(null);
    }

    public void register(IndexRegistry registry)
    {
        registry.registerIndex(this);
    }

    public Optional<ColumnFamilyStore> getBackingTable()
    {
        return Optional.empty();
    }

    private CloseableIterator<PaxosKeyState> getPaxosUpdates(List<UnfilteredPartitionIterator> iterators, TableId filterByTableId, boolean materializeLazily)
    {
        Preconditions.checkArgument((filterByTableId == null) == materializeLazily);

        return PaxosRows.toIterator(UnfilteredPartitionIterators.merge(iterators, UnfilteredPartitionIterators.MergeListener.NOOP), filterByTableId, materializeLazily);
    }

    public CloseableIterator<PaxosKeyState> repairIterator(TableId tableId, Collection<Range<Token>> ranges)
    {
        Preconditions.checkNotNull(tableId);

        View view = baseCfs.getTracker().getView();
        List<Memtable> memtables = view.flushingMemtables.isEmpty()
                                   ? view.liveMemtables
                                   : ImmutableList.<Memtable>builder().addAll(view.flushingMemtables).addAll(view.liveMemtables).build();

        List<DataRange> dataRanges = ranges.stream().map(DataRange::forTokenRange).collect(Collectors.toList());
        List<UnfilteredPartitionIterator> iters = new ArrayList<>(memtables.size() * ranges.size());

        for (int j=0, jsize=dataRanges.size(); j<jsize; j++)
        {
            for (int i=0, isize=memtables.size(); i<isize; i++)
                iters.add(memtables.get(i).partitionIterator(memtableColumnFilter, dataRanges.get(j), SSTableReadsListener.NOOP_LISTENER));
        }

        return getPaxosUpdates(iters, tableId, false);
    }

    public CloseableIterator<PaxosKeyState> flushIterator(Memtable flushing)
    {
        List<UnfilteredPartitionIterator> iters = singletonList(flushing.partitionIterator(memtableColumnFilter, FULL_RANGE, SSTableReadsListener.NOOP_LISTENER));
        return getPaxosUpdates(iters, null, true);
    }

    public Callable<?> getBlockingFlushTask()
    {
        return (Callable<Object>) () -> {
            ballotTracker().flush();
            return null;
        };
    }

    public Callable<?> getBlockingFlushTask(Memtable paxos)
    {
        return (Callable<Object>) () -> {
            uncommittedTracker().flushUpdates(paxos);
            ballotTracker().flush();
            return null;
        };
    }

    public Callable<?> getInvalidateTask()
    {
        return (Callable<Object>) () -> {
            uncommittedTracker().truncate();
            ballotTracker().truncate();
            return null;
        };
    }

    public Callable<?> getTruncateTask(long truncatedAt)
    {
        return (Callable<Object>) () -> {
            uncommittedTracker().truncate();
            ballotTracker().truncate();
            return null;
        };
    }

    public boolean shouldBuildBlocking()
    {
        return false;
    }

    public boolean dependsOn(ColumnMetadata column)
    {
        return false;
    }

    public boolean supportsExpression(ColumnMetadata column, Operator operator)
    {
        // should prevent this from ever being used
        return false;
    }

    public AbstractType<?> customExpressionValueType()
    {
        return null;
    }

    public RowFilter getPostIndexQueryFilter(RowFilter filter)
    {
        return null;
    }

    public long getEstimatedResultRows()
    {
        return 0;
    }

    public void validate(PartitionUpdate update) throws InvalidRequestException
    {

    }

    public Indexer indexerFor(DecoratedKey key, RegularAndStaticColumns columns, int nowInSec, WriteContext ctx, IndexTransaction.Type transactionType)
    {
        return indexer;
    }

    public BiFunction<PartitionIterator, ReadCommand, PartitionIterator> postProcessorFor(ReadCommand command)
    {
        return null;
    }

    public Searcher searcherFor(ReadCommand command)
    {
        throw new UnsupportedOperationException();
    }

    private final Indexer indexer = new Indexer()
    {
        public void begin() {}
        public void partitionDelete(DeletionTime deletionTime) {}
        public void rangeTombstone(RangeTombstone tombstone) {}

        public void insertRow(Row row)
        {
            ballotTracker().onUpdate(row);
        }

        public void updateRow(Row oldRowData, Row newRowData)
        {
            ballotTracker().onUpdate(newRowData);
        }

        public void removeRow(Row row) {}
        public void finish() {}
    };
}
