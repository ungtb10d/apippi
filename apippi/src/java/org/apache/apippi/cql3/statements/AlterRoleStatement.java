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
package org.apache.apippi.cql3.statements;

import org.apache.apippi.audit.AuditLogContext;
import org.apache.apippi.audit.AuditLogEntryType;
import org.apache.apippi.auth.*;
import org.apache.apippi.auth.IRoleManager.Option;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.cql3.PasswordObfuscator;
import org.apache.apippi.cql3.RoleName;
import org.apache.apippi.exceptions.*;
import org.apache.apippi.service.ClientState;
import org.apache.apippi.transport.messages.ResultMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import static org.apache.apippi.cql3.statements.RequestValidations.*;

public class AlterRoleStatement extends AuthenticationStatement
{
    private final RoleResource role;
    private final RoleOptions opts;
    final DCPermissions dcPermissions;
    private final boolean ifExists;

    public AlterRoleStatement(RoleName name, RoleOptions opts)
    {
        this(name, opts, null, false);
    }

    public AlterRoleStatement(RoleName name, RoleOptions opts, DCPermissions dcPermissions, boolean ifExists)
    {
        this.role = RoleResource.role(name.getName());
        this.opts = opts;
        this.dcPermissions = dcPermissions;
        this.ifExists = ifExists;
    }

    public void validate(ClientState state) throws RequestValidationException
    {
        opts.validate();

        if (dcPermissions != null)
        {
            dcPermissions.validate();
        }

        if (opts.isEmpty() && dcPermissions == null)
            throw new InvalidRequestException("ALTER [ROLE|USER] can't be empty");

        // validate login here before authorize to avoid leaking user existence to anonymous users.
        state.ensureNotAnonymous();
        if (!DatabaseDescriptor.getRoleManager().isExistingRole(role))
        {
            checkTrue(ifExists, "Role %s doesn't exist", role.getRoleName());
        }
    }

    public void authorize(ClientState state) throws UnauthorizedException
    {
        AuthenticatedUser user = state.getUser();
        boolean isSuper = user.isSuper();

        if (opts.getSuperuser().isPresent() && user.getRoles().contains(role))
            throw new UnauthorizedException("You aren't allowed to alter your own superuser " +
                                            "status or that of a role granted to you");

        if (opts.getSuperuser().isPresent() && !isSuper)
            throw new UnauthorizedException("Only superusers are allowed to alter superuser status");

        // superusers can do whatever else they like
        if (isSuper)
            return;

        // a role may only modify the subset of its own attributes as determined by IRoleManager#alterableOptions
        if (user.getName().equals(role.getRoleName()))
        {
            for (Option option : opts.getOptions().keySet())
            {
                if (!DatabaseDescriptor.getRoleManager().alterableOptions().contains(option))
                    throw new UnauthorizedException(String.format("You aren't allowed to alter %s", option));
            }
        }
        else
        {
            // if not attempting to alter another role, ensure we have ALTER permissions on it
            super.checkPermission(state, Permission.ALTER, role);
        }
    }

    public ResultMessage execute(ClientState state) throws RequestValidationException, RequestExecutionException
    {
        if (!opts.isEmpty())
            DatabaseDescriptor.getRoleManager().alterRole(state.getUser(), role, opts);
        if (dcPermissions != null)
            DatabaseDescriptor.getNetworkAuthorizer().setRoleDatacenters(role, dcPermissions);
        return null;
    }

    @Override
    public String toString()
    {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public AuditLogContext getAuditLogContext()
    {
        return new AuditLogContext(AuditLogEntryType.ALTER_ROLE);
    }

    @Override
    public String obfuscatePassword(String query)
    {
        return PasswordObfuscator.obfuscate(query, opts);
    }
}