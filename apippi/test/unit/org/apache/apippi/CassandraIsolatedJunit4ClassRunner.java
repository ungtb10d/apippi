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

package org.apache.apippi;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.function.Predicate;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import org.apache.apippi.distributed.impl.AbstractCluster;

/**
 *
 * This class is usually used to test singletons. It ensure singletons can be unique in each test case.
 *
 */
public class apippiIsolatedJunit4ClassRunner extends BlockJUnit4ClassRunner
{

    private static final Predicate<String> isolatedPackage = name ->
                                                             name.startsWith("org.apache.apippi.") ||
                                                             // YAML could not be shared because
                                                             // org.apache.apippi.config.Config is loaded by org.yaml.snakeyaml.YAML
                                                             name.startsWith("org.yaml.snakeyaml.");


    /**
     * Creates a apippiIsolatedJunit4ClassRunner to run {@code klass}
     *
     * @param clazz
     * @throws InitializationError if the test class is malformed.
     */
    public apippiIsolatedJunit4ClassRunner(Class<?> clazz) throws InitializationError
    {
        super(createClassLoader(clazz));
    }

    private static Class<?> createClassLoader(Class<?> clazz) throws InitializationError {
        try {
            ClassLoader testClassLoader = new apippiIsolatedClassLoader();
            return Class.forName(clazz.getName(), true, testClassLoader);
        } catch (ClassNotFoundException e) {
            throw new InitializationError(e);
        }
    }

    public static class apippiIsolatedClassLoader extends URLClassLoader
    {
        public apippiIsolatedClassLoader()
        {
            super(AbstractCluster.CURRENT_VERSION.classpath);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException
        {

            if (isolatedPackage.test(name))
            {
                synchronized (getClassLoadingLock(name))
                {
                    // First, check if the class has already been loaded
                    Class<?> c = findLoadedClass(name);

                    if (c == null)
                        c = findClass(name);

                    return c;
                }
            }
            else
            {
                return super.loadClass(name);
            }
        }

        protected void finalize()
        {
            try
            {
                close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
