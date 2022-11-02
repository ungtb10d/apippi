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

import org.apache.apippi.db.filter.ClusteringIndexFilter;
import org.apache.apippi.db.filter.ColumnFilter;
import org.apache.apippi.db.partitions.PartitionUpdate;
import org.apache.apippi.db.rows.UnfilteredRowIterator;
import org.apache.apippi.db.rows.UnfilteredRowIteratorWithLowerBound;
import org.apache.apippi.io.sstable.format.SSTableReader;
import org.apache.apippi.io.sstable.format.SSTableReadsListener;
import org.apache.apippi.schema.TableId;
import org.apache.apippi.utils.FBUtilities;

public interface StorageHook
{
    public static final StorageHook instance = createHook();

    public void reportWrite(TableId tableId, PartitionUpdate partitionUpdate);
    public void reportRead(TableId tableId, DecoratedKey key);
    public UnfilteredRowIteratorWithLowerBound makeRowIteratorWithLowerBound(ColumnFamilyStore cfs,
                                                                      DecoratedKey partitionKey,
                                                                      SSTableReader sstable,
                                                                      ClusteringIndexFilter filter,
                                                                      ColumnFilter selectedColumns,
                                                                      SSTableReadsListener listener);
    public UnfilteredRowIterator makeRowIterator(ColumnFamilyStore cfs,
                                                 SSTableReader sstable,
                                                 DecoratedKey key,
                                                 Slices slices,
                                                 ColumnFilter selectedColumns,
                                                 boolean reversed,
                                                 SSTableReadsListener listener);

    static StorageHook createHook()
    {
        String className =  System.getProperty("apippi.storage_hook");
        if (className != null)
        {
            return FBUtilities.construct(className, StorageHook.class.getSimpleName());
        }

        return new StorageHook()
        {
            public void reportWrite(TableId tableId, PartitionUpdate partitionUpdate) {}

            public void reportRead(TableId tableId, DecoratedKey key) {}

            public UnfilteredRowIteratorWithLowerBound makeRowIteratorWithLowerBound(ColumnFamilyStore cfs,
                                                                                     DecoratedKey partitionKey,
                                                                                     SSTableReader sstable,
                                                                                     ClusteringIndexFilter filter,
                                                                                     ColumnFilter selectedColumns,
                                                                                     SSTableReadsListener listener)
            {
                return new UnfilteredRowIteratorWithLowerBound(partitionKey,
                                                               sstable,
                                                               filter,
                                                               selectedColumns,
                                                               listener);
            }

            public UnfilteredRowIterator makeRowIterator(ColumnFamilyStore cfs,
                                                         SSTableReader sstable,
                                                         DecoratedKey key,
                                                         Slices slices,
                                                         ColumnFilter selectedColumns,
                                                         boolean reversed,
                                                         SSTableReadsListener listener)
            {
                return sstable.rowIterator(key, slices, selectedColumns, reversed, listener);
            }
        };
    }
}
