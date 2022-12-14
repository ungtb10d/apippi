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
package org.apache.apippi.cql3.statements.schema;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apippi.audit.AuditLogContext;
import org.apache.apippi.audit.AuditLogEntryType;
import org.apache.apippi.auth.DataResource;
import org.apache.apippi.auth.FunctionResource;
import org.apache.apippi.auth.IResource;
import org.apache.apippi.auth.Permission;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.cql3.CQLStatement;
import org.apache.apippi.db.guardrails.Guardrails;
import org.apache.apippi.exceptions.AlreadyExistsException;
import org.apache.apippi.locator.LocalStrategy;
import org.apache.apippi.locator.SimpleStrategy;
import org.apache.apippi.schema.KeyspaceMetadata;
import org.apache.apippi.schema.KeyspaceParams.Option;
import org.apache.apippi.schema.Keyspaces;
import org.apache.apippi.schema.Keyspaces.KeyspacesDiff;
import org.apache.apippi.schema.Schema;
import org.apache.apippi.service.ClientState;
import org.apache.apippi.transport.Event.SchemaChange;
import org.apache.apippi.transport.Event.SchemaChange.Change;

public final class CreateKeyspaceStatement extends AlterSchemaStatement
{
    private static final Logger logger = LoggerFactory.getLogger(CreateKeyspaceStatement.class);

    private final KeyspaceAttributes attrs;
    private final boolean ifNotExists;
    private final HashSet<String> clientWarnings = new HashSet<>();

    public CreateKeyspaceStatement(String keyspaceName, KeyspaceAttributes attrs, boolean ifNotExists)
    {
        super(keyspaceName);
        this.attrs = attrs;
        this.ifNotExists = ifNotExists;
    }

    public Keyspaces apply(Keyspaces schema)
    {
        attrs.validate();

        if (!attrs.hasOption(Option.REPLICATION))
            throw ire("Missing mandatory option '%s'", Option.REPLICATION);

        if (attrs.getReplicationStrategyClass() != null && attrs.getReplicationStrategyClass().equals(SimpleStrategy.class.getSimpleName()))
            Guardrails.simpleStrategyEnabled.ensureEnabled("SimpleStrategy", state);

        if (schema.containsKeyspace(keyspaceName))
        {
            if (ifNotExists)
                return schema;

            throw new AlreadyExistsException(keyspaceName);
        }

        KeyspaceMetadata keyspace = KeyspaceMetadata.create(keyspaceName, attrs.asNewKeyspaceParams());

        if (keyspace.params.replication.klass.equals(LocalStrategy.class))
            throw ire("Unable to use given strategy class: LocalStrategy is reserved for internal use.");

        keyspace.params.validate(keyspaceName, state);
        return schema.withAddedOrUpdated(keyspace);
    }

    SchemaChange schemaChangeEvent(KeyspacesDiff diff)
    {
        return new SchemaChange(Change.CREATED, keyspaceName);
    }

    public void authorize(ClientState client)
    {
        client.ensureAllKeyspacesPermission(Permission.CREATE);
    }

    @Override
    Set<IResource> createdResources(KeyspacesDiff diff)
    {
        return ImmutableSet.of(DataResource.keyspace(keyspaceName), FunctionResource.keyspace(keyspaceName));
    }

    @Override
    public AuditLogContext getAuditLogContext()
    {
        return new AuditLogContext(AuditLogEntryType.CREATE_KEYSPACE, keyspaceName);
    }

    public String toString()
    {
        return String.format("%s (%s)", getClass().getSimpleName(), keyspaceName);
    }

    @Override
    public void validate(ClientState state)
    {
        super.validate(state);

        // Guardrail on number of keyspaces
        Guardrails.keyspaces.guard(Schema.instance.getUserKeyspaces().size() + 1, keyspaceName, false, state);
    }

    @Override
    Set<String> clientWarnings(KeyspacesDiff diff)
    {
        // this threshold is deprecated, it will be replaced by the guardrail used in #validate(ClientState)
        int keyspaceCount = Schema.instance.getKeyspaces().size();
        if (keyspaceCount > DatabaseDescriptor.keyspaceCountWarnThreshold())
        {
            String msg = String.format("Cluster already contains %d keyspaces. Having a large number of keyspaces will significantly slow down schema dependent cluster operations.",
                                       keyspaceCount);
            logger.warn(msg);
            clientWarnings.add(msg);
        }

        return clientWarnings;
    }

    public static final class Raw extends CQLStatement.Raw
    {
        public final String keyspaceName;
        private final KeyspaceAttributes attrs;
        private final boolean ifNotExists;

        public Raw(String keyspaceName, KeyspaceAttributes attrs, boolean ifNotExists)
        {
            this.keyspaceName = keyspaceName;
            this.attrs = attrs;
            this.ifNotExists = ifNotExists;
        }

        public CreateKeyspaceStatement prepare(ClientState state)
        {
            return new CreateKeyspaceStatement(keyspaceName, attrs, ifNotExists);
        }
    }
}
