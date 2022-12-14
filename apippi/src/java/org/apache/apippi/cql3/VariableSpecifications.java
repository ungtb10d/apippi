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
package org.apache.apippi.cql3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.apippi.schema.ColumnMetadata;
import org.apache.apippi.schema.TableMetadata;

public class VariableSpecifications
{
    private final List<ColumnIdentifier> variableNames;
    private final List<ColumnSpecification> specs;
    private final ColumnMetadata[] targetColumns;

    public VariableSpecifications(List<ColumnIdentifier> variableNames)
    {
        this.variableNames = variableNames;
        this.specs = Arrays.asList(new ColumnSpecification[variableNames.size()]);
        this.targetColumns = new ColumnMetadata[variableNames.size()];
    }

    /**
     * Returns an empty instance of <code>VariableSpecifications</code>.
     * @return an empty instance of <code>VariableSpecifications</code>
     */
    public static VariableSpecifications empty()
    {
        return new VariableSpecifications(Collections.emptyList());
    }

    public boolean isEmpty()
    {
        return variableNames.isEmpty();
    }

    public List<ColumnSpecification> getBindVariables()
    {
        return specs;
    }

    /**
     * Returns an array with the same length as the number of partition key columns for the table corresponding
     * to table.  Each short in the array represents the bind index of the marker that holds the value for that
     * partition key column.  If there are no bind markers for any of the partition key columns, null is returned.
     *
     * Callers of this method should ensure that all statements operate on the same table.
     */
    public short[] getPartitionKeyBindVariableIndexes(TableMetadata metadata)
    {
        short[] partitionKeyPositions = new short[metadata.partitionKeyColumns().size()];
        boolean[] set = new boolean[partitionKeyPositions.length];
        for (int i = 0; i < targetColumns.length; i++)
        {
            ColumnMetadata targetColumn = targetColumns[i];
            if (targetColumn != null && targetColumn.isPartitionKey())
            {
                assert targetColumn.ksName.equals(metadata.keyspace) && targetColumn.cfName.equals(metadata.name);
                partitionKeyPositions[targetColumn.position()] = (short) i;
                set[targetColumn.position()] = true;
            }
        }

        for (boolean b : set)
            if (!b)
                return null;

        return partitionKeyPositions;
    }

    public void add(int bindIndex, ColumnSpecification spec)
    {
        if (spec instanceof ColumnMetadata)
            targetColumns[bindIndex] = (ColumnMetadata) spec;

        ColumnIdentifier bindMarkerName = variableNames.get(bindIndex);
        // Use the user name, if there is one
        if (bindMarkerName != null)
            spec = new ColumnSpecification(spec.ksName, spec.cfName, bindMarkerName, spec.type);
        specs.set(bindIndex, spec);
    }

    @Override
    public String toString()
    {
        return specs.toString();
    }
}
