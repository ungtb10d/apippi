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
package org.apache.apippi.index.internal.composites;

import java.nio.ByteBuffer;

import org.apache.apippi.db.marshal.ByteBufferAccessor;
import org.apache.apippi.schema.ColumnMetadata;
import org.apache.apippi.db.Clustering;
import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.db.marshal.AbstractType;
import org.apache.apippi.db.marshal.CollectionType;
import org.apache.apippi.db.marshal.CompositeType;
import org.apache.apippi.db.rows.Cell;
import org.apache.apippi.db.rows.CellPath;
import org.apache.apippi.db.rows.Row;
import org.apache.apippi.schema.IndexMetadata;

/**
 * Index on the element and value of cells participating in a collection.
 *
 * The row keys for this index are a composite of the collection element
 * and value of indexed columns.
 */
public class CollectionEntryIndex extends CollectionKeyIndexBase
{
    public CollectionEntryIndex(ColumnFamilyStore baseCfs,
                                IndexMetadata indexDef)
    {
        super(baseCfs, indexDef);
    }

    public ByteBuffer getIndexedValue(ByteBuffer partitionKey,
                                      Clustering<?> clustering,
                                      CellPath path, ByteBuffer cellValue)
    {
        return CompositeType.build(ByteBufferAccessor.instance, path.get(0), cellValue);
    }

    public boolean isStale(Row data, ByteBuffer indexValue, int nowInSec)
    {
        ByteBuffer[] components = ((CompositeType)functions.getIndexedValueType(indexedColumn)).split(indexValue);
        ByteBuffer mapKey = components[0];
        ByteBuffer mapValue = components[1];

        ColumnMetadata columnDef = indexedColumn;
        Cell<?> cell = data.getCell(columnDef, CellPath.create(mapKey));
        if (cell == null || !cell.isLive(nowInSec))
            return true;

        AbstractType<?> valueComparator = ((CollectionType)columnDef.type).valueComparator();
        return valueComparator.compare(mapValue, cell.buffer()) != 0;
    }
}
