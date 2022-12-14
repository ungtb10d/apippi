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

package org.apache.apippi.distributed.api;

public enum ConsistencyLevel
{
    ANY(0),
    ONE(1),
    TWO(2),
    THREE(3),
    QUORUM(4),
    ALL(5),
    LOCAL_QUORUM(6),
    EACH_QUORUM(7),
    SERIAL(8),
    LOCAL_SERIAL(9),
    LOCAL_ONE(10),
    NODE_LOCAL(11);

    public final int code;
    ConsistencyLevel(int code)
    {
        this.code = code;
    }
}
