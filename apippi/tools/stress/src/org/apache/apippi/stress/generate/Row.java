package org.apache.apippi.stress.generate;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


public class Row
{
    private static final Object[] EMPTY_ROW_DATA = new Object[0];

    public final Object[] partitionKey;
    public final Object[] row;

    public Row(Object[] partitionKey)
    {
        this.partitionKey = partitionKey;
        this.row = EMPTY_ROW_DATA;
    }

    public Row(Object[] partitionKey, Object[] row)
    {
        this.partitionKey = partitionKey;
        this.row = row;
    }

    public Object get(int column)
    {
        if (column < 0)
            return partitionKey[-1-column];
        return row[column];
    }

    public Row copy()
    {
        return new Row(partitionKey.clone(), row.clone());
    }
}
