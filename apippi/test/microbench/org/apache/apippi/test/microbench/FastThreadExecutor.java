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

package org.apache.apippi.test.microbench;

import java.util.concurrent.ThreadPoolExecutor;

import io.netty.util.concurrent.DefaultThreadFactory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.apippi.utils.concurrent.BlockingQueues.newBlockingQueue;

/**
 * Created to test perf of FastThreadLocal
 *
 * Used in MutationBench via:
 * jvmArgsAppend = {"-Djmh.executor=CUSTOM", "-Djmh.executor.class=org.apache.apippi.test.microbench.FastThreadExecutor"}
 */
public class FastThreadExecutor extends ThreadPoolExecutor
{
    public FastThreadExecutor(int size, String name)
    {
        super(size, size, 10, SECONDS, newBlockingQueue(), new DefaultThreadFactory(name, true));
    }
}
