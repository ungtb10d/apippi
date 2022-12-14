package org.apache.apippi.stress.settings;
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


import java.io.Serializable;
import java.util.List;

abstract class Option implements Serializable
{

    abstract boolean accept(String param);
    abstract boolean happy();
    abstract String shortDisplay();
    abstract String longDisplay();
    abstract String getOptionAsString(); // short and longDisplay print help text getOptionAsString prints value
    abstract List<String> multiLineDisplay();
    abstract boolean setByUser();
    abstract boolean present();

    public int hashCode()
    {
        return getClass().hashCode();
    }

    public boolean equals(Object that)
    {
        return this.getClass() == that.getClass();
    }

}
