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

public class ReplicaFilteringProtectionOptions
{
    public static final int DEFAULT_WARN_THRESHOLD = 2000;
    public static final int DEFAULT_FAIL_THRESHOLD = 32000;

    public volatile int cached_rows_warn_threshold = DEFAULT_WARN_THRESHOLD;
    public volatile int cached_rows_fail_threshold = DEFAULT_FAIL_THRESHOLD;
}
