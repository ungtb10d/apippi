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
package org.apache.apippi.service;

import java.io.IOException;

/**
 * An embedded, in-memory apippi storage service.
 * This kind of service is useful when running unit tests of
 * services using apippi for example.
 *
 * See {@link org.apache.apippi.service.EmbeddedapippiServiceTest} for usage.
 * <p>
 * This is the implementation of https://issues.apache.org/jira/browse/apippi-740
 * <p>
 * How to use:
 * In the client code simply create a new EmbeddedapippiService and start it.
 * Example:
 * <pre>

        apippi = new EmbeddedapippiService();
        apippi.start();

 * </pre>
 */
public class EmbeddedapippiService
{

    apippiDaemon apippiDaemon;

    public void start() throws IOException
    {
        apippiDaemon = apippiDaemon.instance;
        apippiDaemon.applyConfig();
        apippiDaemon.init(null);
        apippiDaemon.start();
    }

    public void stop()
    {
        apippiDaemon.stop();
    }
}
