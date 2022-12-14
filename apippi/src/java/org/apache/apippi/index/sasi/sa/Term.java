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
package org.apache.apippi.index.sasi.sa;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.apache.apippi.index.sasi.disk.TokenTreeBuilder;
import org.apache.apippi.db.marshal.AbstractType;

public abstract class Term<T extends Buffer>
{
    protected final int position;
    protected final T value;
    protected TokenTreeBuilder tokens;


    public Term(int position, T value, TokenTreeBuilder tokens)
    {
        this.position = position;
        this.value = value;
        this.tokens = tokens;
    }

    public int getPosition()
    {
        return position;
    }

    public abstract ByteBuffer getTerm();
    public abstract ByteBuffer getSuffix(int start);

    public TokenTreeBuilder getTokens()
    {
        return tokens;
    }

    public abstract int compareTo(AbstractType<?> comparator, Term other);

    public abstract int length();

}

