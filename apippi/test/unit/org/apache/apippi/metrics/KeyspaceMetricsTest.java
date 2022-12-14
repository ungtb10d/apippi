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

package org.apache.apippi.metrics;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.apippi.ServerTestUtils;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.exceptions.ConfigurationException;
import org.apache.apippi.service.EmbeddedapippiService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeyspaceMetricsTest
{
    private static Session session;
    private static Cluster cluster;
    private static EmbeddedapippiService apippi;

    @BeforeClass
    public static void setup() throws ConfigurationException, IOException
    {
        apippi = ServerTestUtils.startEmbeddedapippiService();

        cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(DatabaseDescriptor.getNativeTransportPort()).build();
        session = cluster.connect();
    }

    @Test
    public void testMetricsCleanupOnDrop()
    {
        String keyspace = "keyspacemetricstest_metrics_cleanup";
        apippiMetricsRegistry registry = apippiMetricsRegistry.Metrics;
        Supplier<Stream<String>> metrics = () -> registry.getNames().stream().filter(m -> m.contains(keyspace));

        // no metrics before creating
        assertEquals(0, metrics.get().count());

        session.execute(String.format("CREATE KEYSPACE %s WITH replication = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };", keyspace));
        // some metrics
        assertTrue(metrics.get().count() > 0);

        session.execute(String.format("DROP KEYSPACE %s;", keyspace));
        // no metrics after drop
        assertEquals(metrics.get().collect(Collectors.joining(",")), 0, metrics.get().count());
    }

    @AfterClass
    public static void tearDown()
    {
        if (cluster != null)
            cluster.close();
        if (apippi != null)
            apippi.stop();
    }
}
