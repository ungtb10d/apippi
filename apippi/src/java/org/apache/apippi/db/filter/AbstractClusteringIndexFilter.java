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
package org.apache.apippi.db.filter;

import java.io.IOException;

import org.apache.apippi.schema.ColumnMetadata;
import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.db.*;
import org.apache.apippi.db.marshal.ReversedType;
import org.apache.apippi.io.util.DataInputPlus;
import org.apache.apippi.io.util.DataOutputPlus;

public abstract class AbstractClusteringIndexFilter implements ClusteringIndexFilter
{
    static final Serializer serializer = new FilterSerializer();

    protected final boolean reversed;

    protected AbstractClusteringIndexFilter(boolean reversed)
    {
        this.reversed = reversed;
    }

    public boolean isReversed()
    {
        return reversed;
    }

    public boolean isEmpty(ClusteringComparator comparator)
    {
        return false;
    }

    protected abstract void serializeInternal(DataOutputPlus out, int version) throws IOException;
    protected abstract long serializedSizeInternal(int version);

    protected void appendOrderByToCQLString(TableMetadata metadata, StringBuilder sb)
    {
        if (reversed)
        {
            sb.append(" ORDER BY ");
            int i = 0;
            for (ColumnMetadata column : metadata.clusteringColumns())
            {
                sb.append(i++ == 0 ? "" : ", ")
                  .append(column.name.toCQLString())
                  .append(column.type instanceof ReversedType ? " ASC" : " DESC");
            }
        }
    }

    private static class FilterSerializer implements Serializer
    {
        public void serialize(ClusteringIndexFilter pfilter, DataOutputPlus out, int version) throws IOException
        {
            AbstractClusteringIndexFilter filter = (AbstractClusteringIndexFilter)pfilter;

            out.writeByte(filter.kind().ordinal());
            out.writeBoolean(filter.isReversed());

            filter.serializeInternal(out, version);
        }

        public ClusteringIndexFilter deserialize(DataInputPlus in, int version, TableMetadata metadata) throws IOException
        {
            Kind kind = Kind.values()[in.readUnsignedByte()];
            boolean reversed = in.readBoolean();

            return kind.deserializer.deserialize(in, version, metadata, reversed);
        }

        public long serializedSize(ClusteringIndexFilter pfilter, int version)
        {
            AbstractClusteringIndexFilter filter = (AbstractClusteringIndexFilter)pfilter;

            return 1
                 + TypeSizes.sizeof(filter.isReversed())
                 + filter.serializedSizeInternal(version);
        }
    }
}
