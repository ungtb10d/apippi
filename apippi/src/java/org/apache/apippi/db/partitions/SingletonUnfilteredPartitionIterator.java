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
package org.apache.apippi.db.partitions;

import java.util.NoSuchElementException;

import org.apache.apippi.schema.TableMetadata;
import org.apache.apippi.db.rows.UnfilteredRowIterator;

public class SingletonUnfilteredPartitionIterator implements UnfilteredPartitionIterator
{
    private final UnfilteredRowIterator iter;
    private boolean returned;

    public SingletonUnfilteredPartitionIterator(UnfilteredRowIterator iter)
    {
        this.iter = iter;
    }

    public TableMetadata metadata()
    {
        return iter.metadata();
    }

    public boolean hasNext()
    {
        return !returned;
    }

    public UnfilteredRowIterator next()
    {
        if (returned)
            throw new NoSuchElementException();

        returned = true;
        return iter;
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    public void close()
    {
        if (!returned)
            iter.close();
    }
}
