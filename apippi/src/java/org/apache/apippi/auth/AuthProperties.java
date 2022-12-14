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

package org.apache.apippi.auth;

import javax.management.ObjectName;

import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.db.ConsistencyLevel;
import org.apache.apippi.utils.MBeanWrapper;

public class AuthProperties implements AuthPropertiesMXBean
{
    public static AuthProperties instance = new AuthProperties(DatabaseDescriptor.getAuthWriteConsistencyLevel(),
                                                               DatabaseDescriptor.getAuthReadConsistencyLevel(),
                                                               true);

    public AuthProperties(ConsistencyLevel writeConsistencyLevel, ConsistencyLevel readConsistencyLevel, boolean registerMBean)
    {
        setWriteConsistencyLevel(writeConsistencyLevel);
        setReadConsistencyLevel(readConsistencyLevel);

        if (registerMBean)
        {
            try
            {
                MBeanWrapper.instance.registerMBean(this, new ObjectName("org.apache.apippi.auth:type=AuthProperties"));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void setWriteConsistencyLevel(ConsistencyLevel cl)
    {
        DatabaseDescriptor.setAuthWriteConsistencyLevel(cl);
    }

    public ConsistencyLevel getWriteConsistencyLevel()
    {
        return DatabaseDescriptor.getAuthWriteConsistencyLevel();
    }

    public void setReadConsistencyLevel(ConsistencyLevel cl)
    {
        DatabaseDescriptor.setAuthReadConsistencyLevel(cl);
    }

    public ConsistencyLevel getReadConsistencyLevel()
    {
        return DatabaseDescriptor.getAuthReadConsistencyLevel();
    }
}