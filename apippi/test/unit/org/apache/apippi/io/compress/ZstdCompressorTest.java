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

package org.apache.apippi.io.compress;

import java.util.Collections;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import com.github.luben.zstd.Zstd;

import static org.junit.Assert.assertEquals;

/**
 * Zstd Compressor specific tests. General compressor tests are in {@link CompressorTest}
 */
public class ZstdCompressorTest
{
    @Test
    public void emptyConfigurationUsesDefaultCompressionLevel()
    {
        ZstdCompressor compressor = ZstdCompressor.create(Collections.emptyMap());
        assertEquals(ZstdCompressor.DEFAULT_COMPRESSION_LEVEL, compressor.getCompressionLevel());
    }

    @Test(expected = IllegalArgumentException.class)
    public void badCompressionLevelParamThrowsExceptionMin()
    {
        ZstdCompressor.create(ImmutableMap.of(ZstdCompressor.COMPRESSION_LEVEL_OPTION_NAME, Integer.toString(Zstd.minCompressionLevel() - 1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void badCompressionLevelParamThrowsExceptionMax()
    {
        ZstdCompressor.create(ImmutableMap.of(ZstdCompressor.COMPRESSION_LEVEL_OPTION_NAME, Integer.toString(Zstd.maxCompressionLevel() + 1)));
    }
}
