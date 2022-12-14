package org.apache.apippi.stress.generate;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


public class RatioDistribution
{

    final Distribution distribution;
    final double divisor;

    public RatioDistribution(Distribution distribution, double divisor)
    {
        this.distribution = distribution;
        this.divisor = divisor;
    }

    // yields a value between 0 and 1
    public double next()
    {
        return Math.max(0f, Math.min(1f, distribution.nextDouble() / divisor));
    }

    public double min()
    {
        return Math.min(1d, distribution.minValue() / divisor);
    }

    public double max()
    {
        return Math.min(1d, distribution.maxValue() / divisor);
    }
}
