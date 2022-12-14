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

package org.apache.apippi.utils.asserts;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.apache.apippi.repair.SyncTask;
import org.assertj.core.api.AbstractListAssert;

public class SyncTaskListAssert extends AbstractListAssert<SyncTaskListAssert, List<SyncTask>, SyncTask, SyncTaskAssert>
implements SizeableObjectAssert<SyncTaskListAssert>
{
    public SyncTaskListAssert(List<SyncTask> syncTasks)
    {
        super(syncTasks, SyncTaskListAssert.class);
    }

    protected SyncTaskAssert toAssert(SyncTask value, String description)
    {
        return SyncTaskAssert.assertThat(value);
    }

    protected SyncTaskListAssert newAbstractIterableAssert(Iterable<? extends SyncTask> iterable)
    {
        return assertThat(iterable);
    }

    public static SyncTaskListAssert assertThat(Iterable<? extends SyncTask> iterable)
    {
        return new SyncTaskListAssert(ImmutableList.copyOf(iterable));
    }

    @Override
    public Object actual()
    {
        return actual;
    }

    public SyncTaskListAssert areAllInstanceOf(Class<?> type)
    {
        actual.forEach(t -> toAssert(t, "").isInstanceOf(type));
        return this;
    }
}
