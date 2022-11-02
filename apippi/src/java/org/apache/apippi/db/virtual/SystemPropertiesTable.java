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
package org.apache.apippi.db.virtual;

import java.util.Arrays;

import org.apache.apippi.config.apippiRelevantEnv;
import org.apache.apippi.config.apippiRelevantProperties;
import org.apache.apippi.config.Config;
import org.apache.apippi.db.DecoratedKey;
import org.apache.apippi.db.marshal.*;
import org.apache.apippi.dht.LocalPartitioner;
import org.apache.apippi.schema.TableMetadata;

final class SystemPropertiesTable extends AbstractVirtualTable
{
    private static final String NAME = "name";
    private static final String VALUE = "value";

    SystemPropertiesTable(String keyspace)
    {
        super(TableMetadata.builder(keyspace, "system_properties")
                           .comment("apippi relevant system properties")
                           .kind(TableMetadata.Kind.VIRTUAL)
                           .partitioner(new LocalPartitioner(UTF8Type.instance))
                           .addPartitionKeyColumn(NAME, UTF8Type.instance)
                           .addRegularColumn(VALUE, UTF8Type.instance)
                           .build());
    }

    public DataSet data()
    {
        SimpleDataSet result = new SimpleDataSet(metadata());

        System.getenv().keySet()
              .stream()
              .filter(SystemPropertiesTable::isapippiRelevant)
              .forEach(name -> addRow(result, name, System.getenv(name)));

        System.getProperties().stringPropertyNames()
              .stream()
              .filter(SystemPropertiesTable::isapippiRelevant)
              .forEach(name -> addRow(result, name, System.getProperty(name)));

        return result;
    }

    @Override
    public DataSet data(DecoratedKey partitionKey)
    {
        SimpleDataSet result = new SimpleDataSet(metadata());
        String name = UTF8Type.instance.compose(partitionKey.getKey());
        if (isapippiRelevant(name))
            addRow(result, name, System.getProperty(name, System.getenv(name)));

        return result;
    }

    static boolean isapippiRelevant(String name)
    {
        return name.startsWith(Config.PROPERTY_PREFIX)
               || Arrays.stream(apippiRelevantProperties.values()).anyMatch(p -> p.getKey().equals(name))
               || Arrays.stream(apippiRelevantEnv.values()).anyMatch(p -> p.getKey().equals(name));
    }

    private static void addRow(SimpleDataSet result, String name, String value)
    {
        result.row(name).column(VALUE, value);
    }
}
