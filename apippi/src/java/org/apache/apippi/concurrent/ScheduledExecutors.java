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
package org.apache.apippi.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.annotations.VisibleForTesting;

import org.apache.apippi.utils.ExecutorUtils;

import static org.apache.apippi.concurrent.ExecutorFactory.Global.executorFactory;

/**
 * Centralized location for shared executors
 */
public class ScheduledExecutors
{
    /**
     * This pool is used for periodic fast (sub-microsecond) tasks.
     */
    public static final ScheduledExecutorPlus scheduledFastTasks = executorFactory().scheduled("ScheduledFastTasks");

    /**
     * This pool is used for periodic short (sub-second) tasks.
     */
     public static final ScheduledExecutorPlus scheduledTasks = executorFactory().scheduled("ScheduledTasks");

    /**
     * This executor is used for tasks that can have longer execution times, and usually are non periodic.
     */
    public static final ScheduledExecutorPlus nonPeriodicTasks = executorFactory().scheduled("NonPeriodicTasks");

    /**
     * This executor is used for tasks that do not need to be waited for on shutdown/drain.
     */
    public static final ScheduledExecutorPlus optionalTasks = executorFactory().scheduled(false, "OptionalTasks");

    @VisibleForTesting
    public static void shutdownNowAndWait(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException
    {
        ExecutorUtils.shutdownNowAndWait(timeout, unit, scheduledTasks, scheduledFastTasks, nonPeriodicTasks, optionalTasks);
    }
}
