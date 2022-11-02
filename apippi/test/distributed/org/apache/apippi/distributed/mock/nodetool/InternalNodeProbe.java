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

package org.apache.apippi.distributed.mock.nodetool;

import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.Map;
import javax.management.ListenerNotFoundException;

import com.google.common.collect.Multimap;

import org.apache.apippi.batchlog.BatchlogManager;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.db.ColumnFamilyStoreMBean;
import org.apache.apippi.db.Keyspace;
import org.apache.apippi.db.compaction.CompactionManager;
import org.apache.apippi.gms.FailureDetector;
import org.apache.apippi.gms.FailureDetectorMBean;
import org.apache.apippi.gms.Gossiper;
import org.apache.apippi.hints.HintsService;
import org.apache.apippi.locator.DynamicEndpointSnitchMBean;
import org.apache.apippi.locator.EndpointSnitchInfo;
import org.apache.apippi.locator.EndpointSnitchInfoMBean;
import org.apache.apippi.metrics.apippiMetricsRegistry;
import org.apache.apippi.net.MessagingService;
import org.apache.apippi.service.ActiveRepairService;
import org.apache.apippi.service.CacheService;
import org.apache.apippi.service.CacheServiceMBean;
import org.apache.apippi.service.GCInspector;
import org.apache.apippi.service.StorageProxy;
import org.apache.apippi.service.StorageService;
import org.apache.apippi.service.StorageServiceMBean;
import org.apache.apippi.streaming.StreamManager;
import org.apache.apippi.tools.NodeProbe;
import org.mockito.Mockito;

public class InternalNodeProbe extends NodeProbe
{
    private final boolean withNotifications;

    public InternalNodeProbe(boolean withNotifications)
    {
        this.withNotifications = withNotifications;
        connect();
    }

    protected void connect()
    {
        // note that we are not connecting via JMX for testing
        mbeanServerConn = null;
        jmxc = null;

        if (withNotifications)
        {
            ssProxy = StorageService.instance;
        }
        else
        {
            // replace the notification apis with a no-op method
            StorageServiceMBean mock = Mockito.spy(StorageService.instance);
            Mockito.doNothing().when(mock).addNotificationListener(Mockito.any(), Mockito.any(), Mockito.any());
            try
            {
                Mockito.doNothing().when(mock).removeNotificationListener(Mockito.any(), Mockito.any(), Mockito.any());
                Mockito.doNothing().when(mock).removeNotificationListener(Mockito.any());
            }
            catch (ListenerNotFoundException e)
            {
                throw new AssertionError(e);
            }
            ssProxy = mock;
        }
        msProxy = MessagingService.instance();
        streamProxy = StreamManager.instance;
        compactionProxy = CompactionManager.instance;
        fdProxy = (FailureDetectorMBean) FailureDetector.instance;
        cacheService = CacheService.instance;
        spProxy = StorageProxy.instance;
        hsProxy = HintsService.instance;

        gcProxy = new GCInspector();
        gossProxy = Gossiper.instance;
        bmProxy = BatchlogManager.instance;
        arsProxy = ActiveRepairService.instance;
        memProxy = ManagementFactory.getMemoryMXBean();
        runtimeProxy = ManagementFactory.getRuntimeMXBean();
    }

    @Override
    public void close()
    {
        // nothing to close. no-op
    }

    @Override
    // overrides all the methods referenced mbeanServerConn/jmxc in super
    public EndpointSnitchInfoMBean getEndpointSnitchInfoProxy()
    {
        return new EndpointSnitchInfo();
    }

	@Override
    public DynamicEndpointSnitchMBean getDynamicEndpointSnitchInfoProxy()
    {
        return (DynamicEndpointSnitchMBean) DatabaseDescriptor.createEndpointSnitch(true, DatabaseDescriptor.getRawConfig().endpoint_snitch);
    }

    public CacheServiceMBean getCacheServiceMBean()
    {
        return cacheService;
    }

    @Override
    public ColumnFamilyStoreMBean getCfsProxy(String ks, String cf)
    {
        return Keyspace.open(ks).getColumnFamilyStore(cf);
    }

    // The below methods are only used by the commands (i.e. Info, TableHistogram, TableStats, etc.) that display informations. Not useful for dtest, so disable it.
    @Override
    public Object getCacheMetric(String cacheType, String metricName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<String, ColumnFamilyStoreMBean>> getColumnFamilyStoreMBeanProxies()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Multimap<String, String> getThreadPools()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getThreadPoolMetric(String pathName, String poolName, String metricName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getColumnFamilyMetric(String ks, String cf, String metricName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public apippiMetricsRegistry.JmxTimerMBean getProxyMetric(String scope)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public apippiMetricsRegistry.JmxTimerMBean getMessagingQueueWaitMetrics(String verb)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getCompactionMetric(String metricName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getClientMetric(String metricName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getStorageMetric(String metricName)
    {
        throw new UnsupportedOperationException();
    }
}
