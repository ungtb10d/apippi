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
package org.apache.apippi.net;

import io.netty.channel.MessageSizeEstimator;

/**
 * We want to manage the bytes we have in-flight, so this class asks Netty not to by returning zero for every object.
 */
class NoSizeEstimator implements MessageSizeEstimator, MessageSizeEstimator.Handle
{
    public static final NoSizeEstimator instance = new NoSizeEstimator();
    private NoSizeEstimator() {}
    public Handle newHandle() { return this; }
    public int size(Object o) { return 0; }
}
