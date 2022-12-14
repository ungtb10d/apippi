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

package org.apache.apippi.locator;

import java.io.Serializable;
import java.util.HashMap;

import org.apache.apippi.diag.DiagnosticEvent;

/**
 * Events related to {@link TokenMetadata} changes.
 */
public final class TokenMetadataEvent extends DiagnosticEvent
{

    public enum TokenMetadataEventType
    {
        PENDING_RANGE_CALCULATION_STARTED,
        PENDING_RANGE_CALCULATION_COMPLETED,
    }

    private final TokenMetadataEventType type;
    private final TokenMetadata tokenMetadata;
    private final String keyspace;

    TokenMetadataEvent(TokenMetadataEventType type, TokenMetadata tokenMetadata, String keyspace)
    {
        this.type = type;
        this.tokenMetadata = tokenMetadata;
        this.keyspace = keyspace;
    }

    public TokenMetadataEventType getType()
    {
        return type;
    }

    public HashMap<String, Serializable> toMap()
    {
        // be extra defensive against nulls and bugs
        HashMap<String, Serializable> ret = new HashMap<>();
        ret.put("keyspace", keyspace);
        ret.put("tokenMetadata", tokenMetadata.toString());
        return ret;
    }
}
