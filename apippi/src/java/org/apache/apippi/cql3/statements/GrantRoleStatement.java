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
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.cql3.RoleName;
import org.apache.apippi.exceptions.RequestExecutionException;
import org.apache.apippi.exceptions.RequestValidationException;
import org.apache.apippi.service.ClientState;
import org.apache.apippi.transport.messages.ResultMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class GrantRoleStatement extends RoleManagementStatement
{
    public GrantRoleStatement(RoleName name, RoleName grantee)
    {
        super(name, grantee);
    }

    public ResultMessage execute(ClientState state) throws RequestValidationException, RequestExecutionException
    {
        DatabaseDescriptor.getRoleManager().grantRole(state.getUser(), role, grantee);
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
        return new AuditLogContext(AuditLogEntryType.GRANT);
    }
}
