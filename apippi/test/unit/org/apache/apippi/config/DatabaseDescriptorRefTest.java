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

package org.apache.apippi.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Throwables;
import org.junit.Test;

import org.apache.apippi.utils.Pair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link DatabaseDescriptor#clientInitialization()} } and a couple of <i>apply</i> methods
 * do not somehow lazily initialize any unwanted part of apippi like schema, commit log or start
 * unexpected threads.
 *
 * {@link DatabaseDescriptor#toolInitialization()} is tested via unit tests extending
 * {@link org.apache.apippi.tools.OfflineToolUtils}.
 */
public class DatabaseDescriptorRefTest
{
    static final String[] validClasses = {
    "org.apache.apippi.audit.AuditLogOptions",
    "org.apache.apippi.audit.BinAuditLogger",
    "org.apache.apippi.audit.BinLogAuditLogger",
    "org.apache.apippi.audit.IAuditLogger",
    "org.apache.apippi.auth.AllowAllInternodeAuthenticator",
    "org.apache.apippi.auth.AuthCache$BulkLoader",
    "org.apache.apippi.auth.Cacheable",
    "org.apache.apippi.auth.IInternodeAuthenticator",
    "org.apache.apippi.auth.IAuthenticator",
    "org.apache.apippi.auth.IAuthorizer",
    "org.apache.apippi.auth.IRoleManager",
    "org.apache.apippi.auth.INetworkAuthorizer",
    "org.apache.apippi.config.DatabaseDescriptor",
    "org.apache.apippi.config.apippiRelevantProperties",
    "org.apache.apippi.config.apippiRelevantProperties$PropertyConverter",
    "org.apache.apippi.config.ConfigurationLoader",
    "org.apache.apippi.config.Config",
    "org.apache.apippi.config.Config$1",
    "org.apache.apippi.config.Config$CommitLogSync",
    "org.apache.apippi.config.Config$CommitFailurePolicy",
    "org.apache.apippi.config.Config$DiskAccessMode",
    "org.apache.apippi.config.Config$DiskFailurePolicy",
    "org.apache.apippi.config.Config$DiskOptimizationStrategy",
    "org.apache.apippi.config.Config$FlushCompression",
    "org.apache.apippi.config.Config$InternodeCompression",
    "org.apache.apippi.config.Config$MemtableAllocationType",
    "org.apache.apippi.config.Config$PaxosOnLinearizabilityViolation",
    "org.apache.apippi.config.Config$PaxosStatePurging",
    "org.apache.apippi.config.Config$PaxosVariant",
    "org.apache.apippi.config.Config$RepairCommandPoolFullStrategy",
    "org.apache.apippi.config.Config$UserFunctionTimeoutPolicy",
    "org.apache.apippi.config.Config$CorruptedTombstoneStrategy",
    "org.apache.apippi.config.DatabaseDescriptor$ByteUnit",
    "org.apache.apippi.config.DataRateSpec",
    "org.apache.apippi.config.DataRateSpec$DataRateUnit",
    "org.apache.apippi.config.DataRateSpec$DataRateUnit$1",
    "org.apache.apippi.config.DataRateSpec$DataRateUnit$2",
    "org.apache.apippi.config.DataRateSpec$DataRateUnit$3",
    "org.apache.apippi.config.DataStorageSpec",
    "org.apache.apippi.config.DataStorageSpec$DataStorageUnit",
    "org.apache.apippi.config.DataStorageSpec$DataStorageUnit$1",
    "org.apache.apippi.config.DataStorageSpec$DataStorageUnit$2",
    "org.apache.apippi.config.DataStorageSpec$DataStorageUnit$3",
    "org.apache.apippi.config.DataStorageSpec$DataStorageUnit$4",
    "org.apache.apippi.config.DataStorageSpec$IntBytesBound",
    "org.apache.apippi.config.DataStorageSpec$IntKibibytesBound",
    "org.apache.apippi.config.DataStorageSpec$IntMebibytesBound",
    "org.apache.apippi.config.DataStorageSpec$LongBytesBound",
    "org.apache.apippi.config.DataStorageSpec$LongMebibytesBound",
    "org.apache.apippi.config.DurationSpec",
    "org.apache.apippi.config.DataRateSpec$LongBytesPerSecondBound",
    "org.apache.apippi.config.DurationSpec$LongMillisecondsBound",
    "org.apache.apippi.config.DurationSpec$LongNanosecondsBound",
    "org.apache.apippi.config.DurationSpec$LongSecondsBound",
    "org.apache.apippi.config.DurationSpec$IntMillisecondsBound",
    "org.apache.apippi.config.DurationSpec$IntSecondsBound",
    "org.apache.apippi.config.DurationSpec$IntMinutesBound",
    "org.apache.apippi.config.EncryptionOptions",
    "org.apache.apippi.config.EncryptionOptions$ClientEncryptionOptions",
    "org.apache.apippi.config.EncryptionOptions$ServerEncryptionOptions",
    "org.apache.apippi.config.EncryptionOptions$ServerEncryptionOptions$InternodeEncryption",
    "org.apache.apippi.config.EncryptionOptions$ServerEncryptionOptions$OutgoingEncryptedPortSource",
    "org.apache.apippi.config.GuardrailsOptions",
    "org.apache.apippi.config.GuardrailsOptions$Config",
    "org.apache.apippi.config.GuardrailsOptions$ConsistencyLevels",
    "org.apache.apippi.config.GuardrailsOptions$TableProperties",
    "org.apache.apippi.config.ParameterizedClass",
    "org.apache.apippi.config.ReplicaFilteringProtectionOptions",
    "org.apache.apippi.config.YamlConfigurationLoader",
    "org.apache.apippi.config.YamlConfigurationLoader$PropertiesChecker",
    "org.apache.apippi.config.YamlConfigurationLoader$PropertiesChecker$1",
    "org.apache.apippi.config.YamlConfigurationLoader$CustomConstructor",
    "org.apache.apippi.config.TransparentDataEncryptionOptions",
    "org.apache.apippi.config.StartupChecksOptions",
    "org.apache.apippi.config.SubnetGroups",
    "org.apache.apippi.config.TrackWarnings",
    "org.apache.apippi.db.ConsistencyLevel",
    "org.apache.apippi.db.commitlog.CommitLogSegmentManagerFactory",
    "org.apache.apippi.db.commitlog.DefaultCommitLogSegmentMgrFactory",
    "org.apache.apippi.db.commitlog.AbstractCommitLogSegmentManager",
    "org.apache.apippi.db.commitlog.CommitLogSegmentManagerCDC",
    "org.apache.apippi.db.commitlog.CommitLogSegmentManagerStandard",
    "org.apache.apippi.db.commitlog.CommitLog",
    "org.apache.apippi.db.commitlog.CommitLogMBean",
    "org.apache.apippi.db.guardrails.GuardrailsConfig",
    "org.apache.apippi.db.guardrails.GuardrailsConfigMBean",
    "org.apache.apippi.db.guardrails.GuardrailsConfig$ConsistencyLevels",
    "org.apache.apippi.db.guardrails.GuardrailsConfig$TableProperties",
    "org.apache.apippi.db.guardrails.Values$Config",
    "org.apache.apippi.dht.IPartitioner",
    "org.apache.apippi.distributed.api.IInstance",
    "org.apache.apippi.distributed.api.IIsolatedExecutor",
    "org.apache.apippi.distributed.shared.InstanceClassLoader",
    "org.apache.apippi.distributed.impl.InstanceConfig",
    "org.apache.apippi.distributed.api.IInvokableInstance",
    "org.apache.apippi.distributed.impl.InvokableInstance$CallableNoExcept",
    "org.apache.apippi.distributed.impl.InvokableInstance$InstanceFunction",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableBiConsumer",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableBiFunction",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableCallable",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableConsumer",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableFunction",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableRunnable",
    "org.apache.apippi.distributed.impl.InvokableInstance$SerializableTriFunction",
    "org.apache.apippi.distributed.impl.InvokableInstance$TriFunction",
    "org.apache.apippi.distributed.impl.Message",
    "org.apache.apippi.distributed.impl.NetworkTopology",
    "org.apache.apippi.exceptions.ConfigurationException",
    "org.apache.apippi.exceptions.RequestValidationException",
    "org.apache.apippi.exceptions.apippiException",
    "org.apache.apippi.exceptions.InvalidRequestException",
    "org.apache.apippi.exceptions.TransportException",
    "org.apache.apippi.fql.FullQueryLogger",
    "org.apache.apippi.fql.FullQueryLoggerOptions",
    "org.apache.apippi.gms.IFailureDetector",
    "org.apache.apippi.locator.IEndpointSnitch",
    "org.apache.apippi.io.FSWriteError",
    "org.apache.apippi.io.FSError",
    "org.apache.apippi.io.compress.ICompressor",
    "org.apache.apippi.io.compress.ICompressor$Uses",
    "org.apache.apippi.io.compress.LZ4Compressor",
    "org.apache.apippi.io.sstable.metadata.MetadataType",
    "org.apache.apippi.io.util.BufferedDataOutputStreamPlus",
    "org.apache.apippi.io.util.RebufferingInputStream",
    "org.apache.apippi.io.util.FileInputStreamPlus",
    "org.apache.apippi.io.util.FileOutputStreamPlus",
    "org.apache.apippi.io.util.File",
    "org.apache.apippi.io.util.DataOutputBuffer",
    "org.apache.apippi.io.util.DataOutputBufferFixed",
    "org.apache.apippi.io.util.DataOutputStreamPlus",
    "org.apache.apippi.io.util.DataOutputPlus",
    "org.apache.apippi.io.util.DataInputPlus",
    "org.apache.apippi.io.util.DiskOptimizationStrategy",
    "org.apache.apippi.io.util.SpinningDiskOptimizationStrategy",
    "org.apache.apippi.io.util.PathUtils$IOToLongFunction",
    "org.apache.apippi.locator.Replica",
    "org.apache.apippi.locator.ReplicaCollection",
    "org.apache.apippi.locator.SimpleSeedProvider",
    "org.apache.apippi.locator.SeedProvider",
    "org.apache.apippi.security.ISslContextFactory",
    "org.apache.apippi.security.SSLFactory",
    "org.apache.apippi.security.EncryptionContext",
    "org.apache.apippi.service.CacheService$CacheType",
    "org.apache.apippi.transport.ProtocolException",
    "org.apache.apippi.utils.binlog.BinLogOptions",
    "org.apache.apippi.utils.FBUtilities",
    "org.apache.apippi.utils.FBUtilities$1",
    "org.apache.apippi.utils.CloseableIterator",
    "org.apache.apippi.utils.Pair",
    "org.apache.apippi.utils.concurrent.UncheckedInterruptedException",
    "org.apache.apippi.ConsoleAppender",
    "org.apache.apippi.ConsoleAppender$1",
    "org.apache.apippi.LogbackStatusListener",
    "org.apache.apippi.LogbackStatusListener$ToLoggerOutputStream",
    "org.apache.apippi.LogbackStatusListener$WrappedPrintStream",
    "org.apache.apippi.TeeingAppender",
    // generated classes
    "org.apache.apippi.config.ConfigBeanInfo",
    "org.apache.apippi.config.ConfigCustomizer",
    "org.apache.apippi.config.EncryptionOptionsBeanInfo",
    "org.apache.apippi.config.EncryptionOptionsCustomizer",
    "org.apache.apippi.config.EncryptionOptions$ServerEncryptionOptionsBeanInfo",
    "org.apache.apippi.config.EncryptionOptions$ServerEncryptionOptionsCustomizer",
    "org.apache.apippi.ConsoleAppenderBeanInfo",
    "org.apache.apippi.ConsoleAppenderCustomizer",
    "org.apache.apippi.locator.InetAddressAndPort",
    "org.apache.apippi.cql3.statements.schema.AlterKeyspaceStatement",
    "org.apache.apippi.cql3.statements.schema.CreateKeyspaceStatement"
    };

    static final Set<String> checkedClasses = new HashSet<>(Arrays.asList(validClasses));

    @Test
    @SuppressWarnings({"DynamicRegexReplaceableByCompiledPattern", "UseOfSystemOutOrSystemErr"})
    public void testDatabaseDescriptorRef() throws Throwable
    {
        PrintStream out = System.out;
        PrintStream err = System.err;

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int threadCount = threads.getThreadCount();
        List<Long> existingThreadIDs = Arrays.stream(threads.getAllThreadIds()).boxed().collect(Collectors.toList());

        ClassLoader delegate = Thread.currentThread().getContextClassLoader();

        List<Pair<String, Exception>> violations = Collections.synchronizedList(new ArrayList<>());

        ClassLoader cl = new ClassLoader(null)
        {
            final Map<String, Class<?>> classMap = new HashMap<>();

            public URL getResource(String name)
            {
                return delegate.getResource(name);
            }

            public InputStream getResourceAsStream(String name)
            {
                return delegate.getResourceAsStream(name);
            }

            protected Class<?> findClass(String name) throws ClassNotFoundException
            {
                if (name.startsWith("java."))
                    // Java 11 does not allow a "custom" class loader (i.e. user code)
                    // to define classes in protected packages (like java, java.sql, etc).
                    // Therefore we have to delegate the call to the delegate class loader
                    // itself.
                    return delegate.loadClass(name);

                Class<?> cls = classMap.get(name);
                if (cls != null)
                    return cls;

                if (name.startsWith("org.apache.apippi."))
                {
                    // out.println(name);

                    if (!checkedClasses.contains(name))
                        violations.add(Pair.create(name, new Exception()));
                }

                URL url = delegate.getResource(name.replace('.', '/') + ".class");
                if (url == null)
                {
                    // For Java 11: system class files are not readable via getResource(), so
                    // try it this way
                    cls = Class.forName(name, false, delegate);
                    classMap.put(name, cls);
                    return cls;
                }

                // Java8 way + all non-system class files
                try (InputStream in = url.openConnection().getInputStream())
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    int c;
                    while ((c = in.read()) != -1)
                        os.write(c);
                    byte[] data = os.toByteArray();
                    cls = defineClass(name, data, 0, data.length);
                    classMap.put(name, cls);
                    return cls;
                }
                catch (IOException e)
                {
                    throw new ClassNotFoundException(name, e);
                }
            }
        };

        Thread.currentThread().setContextClassLoader(cl);

        assertEquals("thread started", threadCount, threads.getThreadCount());

        Class<?> databaseDescriptorClass = Class.forName("org.apache.apippi.config.DatabaseDescriptor", true, cl);

        // During DatabaseDescriptor instantiation some threads are spawned. We need to take them into account in
        // threadCount variable, otherwise they will be considered as new threads spawned by methods below. There is a
        // trick: in case of multiple runs of this test in the same JVM the number of such threads will be multiplied by
        // the number of runs. That's because DatabaseDescriptor is instantiated via a different class loader. So in
        // order to keep calculation logic correct, we ignore existing threads that were spawned during the previous
        // runs and change threadCount variable for the new threads only (if they have some specific names).
        for (ThreadInfo threadInfo : threads.getThreadInfo(threads.getAllThreadIds()))
        {
            // All existing threads have been already taken into account in threadCount variable, so we ignore them
            if (existingThreadIDs.contains(threadInfo.getThreadId()))
                continue;
            // Logback AsyncAppender thread needs to be taken into account
            if (threadInfo.getThreadName().equals("AsyncAppender-Worker-ASYNC"))
                threadCount++;
            // Logback basic threads need to be taken into account
            if (threadInfo.getThreadName().matches("logback-\\d+"))
                threadCount++;
            // Dynamic Attach thread needs to be taken into account, generally it is spawned by IDE
            if (threadInfo.getThreadName().equals("Attach Listener"))
                threadCount++;
        }

        for (String methodName : new String[]{
            "clientInitialization",
            "applyAddressConfig",
            "applyTokensConfig",
            // no seed provider in default configuration for clients
            // "applySeedProvider",
            // definitely not safe for clients - implicitly instantiates schema
            // "applyPartitioner",
            // definitely not safe for clients - implicitly instantiates StorageService
            // "applySnitch",
            "applyEncryptionContext",
            // starts "REQUEST-SCHEDULER" thread via RoundRobinScheduler
        })
        {
            Method method = databaseDescriptorClass.getDeclaredMethod(methodName);
            method.invoke(null);

            if (threadCount != threads.getThreadCount())
            {
                for (ThreadInfo threadInfo : threads.getThreadInfo(threads.getAllThreadIds()))
                    out.println("Thread #" + threadInfo.getThreadId() + ": " + threadInfo.getThreadName());
                assertEquals("thread started in " + methodName, threadCount, ManagementFactory.getThreadMXBean().getThreadCount());
            }

            checkViolations(err, violations);
        }
    }

    private void checkViolations(PrintStream err, List<Pair<String, Exception>> violations)
    {
        if (!violations.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (Pair<String, Exception> violation : new ArrayList<>(violations))
                sb.append("\n\n")
                  .append("VIOLATION: ").append(violation.left).append('\n')
                  .append(Throwables.getStackTraceAsString(violation.right));
            String msg = sb.toString();
            err.println(msg);

            fail(msg);
        }
    }
}
