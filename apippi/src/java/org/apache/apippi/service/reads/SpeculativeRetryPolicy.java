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
package org.apache.apippi.service.reads;

import org.apache.apippi.exceptions.ConfigurationException;
import org.apache.apippi.metrics.SnapshottingTimer;
import org.apache.apippi.schema.TableParams;

public interface SpeculativeRetryPolicy
{
    public enum Kind
    {
        NEVER, FIXED, PERCENTILE, HYBRID, ALWAYS
    }

    /**
     * Calculate the delay in microseconds after which speculation takes place
     *
     * @param latency       snapshot of coordinator latencies (in microseconds)
     * @param existingValue existing speculation threshold (in microseconds)
     * @return speculation delay (in microseconds).
     */
    long calculateThreshold(SnapshottingTimer latency, long existingValue);

    Kind kind();

    public static SpeculativeRetryPolicy fromString(String str)
    {
        if (AlwaysSpeculativeRetryPolicy.stringMatches(str))
            return AlwaysSpeculativeRetryPolicy.INSTANCE;

        if (NeverSpeculativeRetryPolicy.stringMatches(str))
            return NeverSpeculativeRetryPolicy.INSTANCE;

        if (PercentileSpeculativeRetryPolicy.stringMatches(str))
            return PercentileSpeculativeRetryPolicy.fromString(str);

        if (FixedSpeculativeRetryPolicy.stringMatches(str))
            return FixedSpeculativeRetryPolicy.fromString(str);

        if (HybridSpeculativeRetryPolicy.stringMatches(str))
            return HybridSpeculativeRetryPolicy.fromString(str);

        throw new ConfigurationException(String.format("Invalid value %s for option '%s'", str, TableParams.Option.SPECULATIVE_RETRY));
    }
}
