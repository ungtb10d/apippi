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
package org.apache.apippi.db.compaction.writers;


import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.db.Directories;
import org.apache.apippi.db.SerializationHeader;
import org.apache.apippi.db.rows.UnfilteredRowIterator;
import org.apache.apippi.db.lifecycle.LifecycleTransaction;
import org.apache.apippi.io.sstable.format.SSTableReader;
import org.apache.apippi.io.sstable.format.SSTableWriter;
import org.apache.apippi.io.sstable.metadata.MetadataCollector;

/**
 * The default compaction writer - creates one output file in L0
 */
public class DefaultCompactionWriter extends CompactionAwareWriter
{
    protected static final Logger logger = LoggerFactory.getLogger(DefaultCompactionWriter.class);
    private final int sstableLevel;

    public DefaultCompactionWriter(ColumnFamilyStore cfs, Directories directories, LifecycleTransaction txn, Set<SSTableReader> nonExpiredSSTables)
    {
        this(cfs, directories, txn, nonExpiredSSTables, false, 0);
    }

    @Deprecated
    public DefaultCompactionWriter(ColumnFamilyStore cfs, Directories directories, LifecycleTransaction txn, Set<SSTableReader> nonExpiredSSTables, boolean offline, boolean keepOriginals, int sstableLevel)
    {
        this(cfs, directories, txn, nonExpiredSSTables, keepOriginals, sstableLevel);
    }

    @SuppressWarnings("resource")
    public DefaultCompactionWriter(ColumnFamilyStore cfs, Directories directories, LifecycleTransaction txn, Set<SSTableReader> nonExpiredSSTables, boolean keepOriginals, int sstableLevel)
    {
        super(cfs, directories, txn, nonExpiredSSTables, keepOriginals);
        this.sstableLevel = sstableLevel;
    }

    @Override
    public boolean realAppend(UnfilteredRowIterator partition)
    {
        return sstableWriter.append(partition) != null;
    }

    @Override
    public void switchCompactionLocation(Directories.DataDirectory directory)
    {
        sstableDirectory = directory;

        @SuppressWarnings("resource")
        SSTableWriter writer = SSTableWriter.create(cfs.newSSTableDescriptor(getDirectories().getLocationForDisk(directory)),
                                                    estimatedTotalKeys,
                                                    minRepairedAt,
                                                    pendingRepair,
                                                    isTransient,
                                                    cfs.metadata,
                                                    new MetadataCollector(txn.originals(), cfs.metadata().comparator, sstableLevel),
                                                    SerializationHeader.make(cfs.metadata(), nonExpiredSSTables),
                                                    cfs.indexManager.listIndexes(),
                                                    txn);
        sstableWriter.switchWriter(writer);
    }

    @Override
    public long estimatedKeys()
    {
        return estimatedTotalKeys;
    }
}
