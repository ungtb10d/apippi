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

package org.apache.apippi.distributed.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ch.qos.logback.classic.Level;
import org.apache.apippi.db.virtual.LogMessagesTable;
import org.apache.apippi.db.virtual.LogMessagesTable.LogMessage;
import org.apache.apippi.distributed.Cluster;
import org.apache.apippi.distributed.api.Feature;
import org.apache.apippi.distributed.api.SimpleQueryResult;
import org.apache.apippi.schema.SchemaConstants;
import org.apache.apippi.utils.logging.VirtualTableAppender;

import static java.lang.String.format;
import static org.apache.apippi.db.virtual.LogMessagesTable.LEVEL_COLUMN_NAME;
import static org.apache.apippi.db.virtual.LogMessagesTable.LOGGER_COLUMN_NAME;
import static org.apache.apippi.db.virtual.LogMessagesTable.MESSAGE_COLUMN_NAME;
import static org.apache.apippi.db.virtual.LogMessagesTable.ORDER_IN_MILLISECOND_COLUMN_NAME;
import static org.apache.apippi.db.virtual.LogMessagesTable.TIMESTAMP_COLUMN_NAME;
import static org.apache.apippi.distributed.api.ConsistencyLevel.ONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VirtualTableLogsTest extends TestBaseImpl
{
    @Test
    public void testVTableOutput() throws Throwable
    {
        System.setProperty("logback.configurationFile", "test/conf/logback-dtest_with_vtable_appender.xml");

        try (Cluster cluster = Cluster.build(1)
                                      .withConfig(c -> c.with(Feature.values()))
                                      .start())
        {
            List<TestingLogMessage> rows = getRows(cluster);
            assertFalse(rows.isEmpty());

            rows.forEach(message -> assertTrue(Level.toLevel(message.level).isGreaterOrEqual(Level.INFO)));
        }
        finally
        {
            System.clearProperty("logback.configurationFile");
        }
    }

    @Test
    public void testMultipleAppendersFailToStartNode() throws Throwable
    {
        System.setProperty("logback.configurationFile", "test/conf/logback-dtest_with_vtable_appender_invalid.xml");

        try (Cluster ignored = Cluster.build(1)
                                      .withConfig(c -> c.with(Feature.values()))
                                      .start())
        {
            fail("Node should not start as there is supposed to be invalid logback configuration file.");
        }
        catch (IllegalStateException ex)
        {
            assertEquals(format("There are multiple appenders of class %s " +
                                "of names CQLLOG,CQLLOG2. There is only one appender of such class allowed.",
                                VirtualTableAppender.class.getName()),
                         ex.getMessage());
        }
        finally
        {
            System.clearProperty("logback.configurationFile");
        }
    }

    private List<TestingLogMessage> getRows(Cluster cluster)
    {
        SimpleQueryResult simpleQueryResult = cluster.coordinator(1).executeWithResult(query("select * from %s"), ONE);
        List<TestingLogMessage> rows = new ArrayList<>();
        simpleQueryResult.forEachRemaining(row -> {
            long timestamp = row.getTimestamp(TIMESTAMP_COLUMN_NAME).getTime();
            String logger = row.getString(LOGGER_COLUMN_NAME);
            String level = row.getString(LEVEL_COLUMN_NAME);
            String message = row.getString(MESSAGE_COLUMN_NAME);
            int order = row.getInteger(ORDER_IN_MILLISECOND_COLUMN_NAME);
            TestingLogMessage logMessage = new TestingLogMessage(timestamp, logger, level, message, order);
            rows.add(logMessage);
        });
        return rows;
    }

    private String query(String template)
    {
        return format(template, getTableName());
    }

    private String getTableName()
    {
        return format("%s.%s", SchemaConstants.VIRTUAL_VIEWS, LogMessagesTable.TABLE_NAME);
    }

    private static class TestingLogMessage extends LogMessage
    {
        private int order;

        public TestingLogMessage(long timestamp, String logger, String level, String message, int order)
        {
            super(timestamp, logger, level, message);
            this.order = order;
        }
    }
}
