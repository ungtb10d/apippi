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

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Iterables;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.SchemaLoader;
import org.apache.apippi.auth.AuthCacheService;
import org.apache.apippi.auth.AuthKeyspace;
import org.apache.apippi.auth.AuthTestUtils;
import org.apache.apippi.auth.AuthenticatedUser;
import org.apache.apippi.auth.DataResource;
import org.apache.apippi.auth.IResource;
import org.apache.apippi.auth.Permission;
import org.apache.apippi.auth.Roles;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.schema.KeyspaceParams;
import org.apache.apippi.schema.SchemaConstants;
import org.apache.apippi.schema.TableMetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClientStateTest
{
    @BeforeClass
    public static void beforeClass()
    {
        System.setProperty("org.apache.apippi.disable_mbean_registration", "true");
        SchemaLoader.prepareServer();
        DatabaseDescriptor.setAuthFromRoot(true);
        // create the system_auth keyspace so the IRoleManager can function as normal
        SchemaLoader.createKeyspace(SchemaConstants.AUTH_KEYSPACE_NAME,
                                    KeyspaceParams.simple(1),
                                    Iterables.toArray(AuthKeyspace.metadata().tables, TableMetadata.class));

        AuthCacheService.initializeAndRegisterCaches();
    }

    @AfterClass
    public static void afterClass()
    {
        System.clearProperty("org.apache.apippi.disable_mbean_registration");
    }

    @Test
    public void permissionsCheckStartsAtHeadOfResourceChain()
    {
        // verify that when performing a permissions check, we start from the
        // root IResource in the applicable hierarchy and proceed to the more
        // granular resources until we find the required permission (or until
        // we reach the end of the resource chain). This is because our typical
        // usage is to grant blanket permissions on the root resources to users
        // and so we save lookups, cache misses and cache space by traversing in
        // this order. e.g. for DataResources, we typically grant perms on the
        // 'data' resource, so when looking up a users perms on a specific table
        // it makes sense to follow: data -> keyspace -> table

        final AtomicInteger getPermissionsRequestCount = new AtomicInteger(0);
        final IResource rootResource = DataResource.root();
        final IResource tableResource = DataResource.table("test_ks", "test_table");
        final AuthenticatedUser testUser = new AuthenticatedUser("test_user")
        {
            public Set<Permission> getPermissions(IResource resource)
            {
                getPermissionsRequestCount.incrementAndGet();
                if (resource.equals(rootResource))
                    return Permission.ALL;

                fail(String.format("Permissions requested for unexpected resource %s", resource));
                // need a return to make the compiler happy
                return null;
            }

            public boolean canLogin() { return true; }
        };

        Roles.cache.invalidate();

        // finally, need to configure apippiAuthorizer so we don't shortcircuit out of the authz process
        DatabaseDescriptor.setAuthorizer(new AuthTestUtils.LocalapippiAuthorizer());

        // check permissions on the table, which should check for the root resource first
        // & return successfully without needing to proceed further
        ClientState state = ClientState.forInternalCalls();
        state.login(testUser);
        state.ensurePermission(Permission.SELECT, tableResource);
        assertEquals(1, getPermissionsRequestCount.get());
    }
}