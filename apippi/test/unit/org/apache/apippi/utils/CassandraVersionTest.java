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
package org.apache.apippi.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Splitter;
import org.apache.commons.lang3.ArrayUtils;

import org.junit.Assert;
import org.junit.Test;

import com.datastax.driver.core.VersionNumber;
import org.assertj.core.api.Assertions;
import org.quicktheories.core.Gen;
import org.quicktheories.generators.Generate;
import org.quicktheories.generators.SourceDSL;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.quicktheories.QuickTheory.qt;

public class apippiVersionTest
{
    @Test
    public void toStringParses()
    {
        qt().forAll(versionGen()).checkAssert(version -> {
            Assertions.assertThat(new apippiVersion(version.toString()))
                      .isEqualTo(version)
                      .hasSameHashCodeAs(version)
                      .isEqualByComparingTo(version);

        });
    }

    @Test
    public void clientCanParse()
    {
        qt().forAll(versionGen()).checkAssert(version -> {
            Assertions.assertThat(VersionNumber.parse(version.toString())).isNotNull();
        });
    }

    private static Gen<apippiVersion> versionGen()
    {
        Gen<Integer> positive = SourceDSL.integers().allPositive();
        Gen<Integer> hotfixGen = positive.mix(Generate.constant(apippiVersion.NO_HOTFIX));
        Gen<Integer> smallSizes = SourceDSL.integers().between(0, 5);
        Gen<String> word = Generators.regexWord(SourceDSL.integers().between(1, 100)); // empty isn't allowed while parsing since \w+ is used, so must be at least 1
        return td -> {
            int major = positive.generate(td);
            int minor = positive.generate(td);
            int patch = positive.generate(td);

            int hotfix = hotfixGen.generate(td);

            int numPreRelease = smallSizes.generate(td);
            String[] preRelease = numPreRelease == 0 ? null : new String[numPreRelease];
            for (int i = 0; i < numPreRelease; i++)
                preRelease[i] = word.generate(td);

            int numBuild = smallSizes.generate(td);
            String[] build = numBuild == 0 ? null : new String[numBuild];
            for (int i = 0; i < numBuild; i++)
                build[i] = word.generate(td);
            return new apippiVersion(major, minor, patch, hotfix, preRelease, build);
        };
    }

    @Test
    public void multiplePreRelease()
    {
        for (String version : Arrays.asList("4.0-alpha1-SNAPSHOT",
                                            "4.0.1-alpha1-SNAPSHOT",
                                            "4.0.1.1-alpha1-SNAPSHOT",
                                            "4.0.0.0-a-b-c-d-e-f-g"))
        {
            apippiVersion apippi = new apippiVersion(version);
            VersionNumber client = VersionNumber.parse(version);
            Assert.assertEquals(apippi.major, client.getMajor());
            Assert.assertEquals(apippi.minor, client.getMinor());
            Assert.assertEquals(apippi.patch, client.getPatch());
            Assert.assertEquals(apippi.hotfix, client.getDSEPatch());
            Assert.assertEquals(apippi.getPreRelease(), client.getPreReleaseLabels());
        }
    }

    @Test
    public void multipleBuild()
    {
        for (String version : Arrays.asList("4.0+alpha1.SNAPSHOT",
                                            "4.0.1+alpha1.SNAPSHOT",
                                            "4.0.1.1+alpha1.SNAPSHOT",
                                            "4.0.0.0+a.b.c.d.e.f.g"))
        {
            apippiVersion apippi = new apippiVersion(version);
            VersionNumber client = VersionNumber.parse(version);
            Assert.assertEquals(apippi.major, client.getMajor());
            Assert.assertEquals(apippi.minor, client.getMinor());
            Assert.assertEquals(apippi.patch, client.getPatch());
            Assert.assertEquals(apippi.hotfix, client.getDSEPatch());
            Assert.assertEquals(apippi.getBuild(), Splitter.on(".").splitToList(client.getBuildLabel()));
        }
    }

    @Test
    public void testParsing()
    {
        apippiVersion version;

        version = new apippiVersion("1.2.3");
        assertTrue(version.major == 1 && version.minor == 2 && version.patch == 3);

        version = new apippiVersion("1.2.3-foo.2+Bar");
        assertTrue(version.major == 1 && version.minor == 2 && version.patch == 3);

        // apippiVersion can parse 4th '.' as build number
        version = new apippiVersion("1.2.3.456");
        assertTrue(version.major == 1 && version.minor == 2 && version.patch == 3);

        // support for tick-tock release
        version = new apippiVersion("3.2");
        assertTrue(version.major == 3 && version.minor == 2 && version.patch == 0);
    }

    @Test
    public void testCompareTo()
    {
        apippiVersion v1, v2;

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("1.2.4");
        assertTrue(v1.compareTo(v2) < 0);

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("1.2.3");
        assertTrue(v1.compareTo(v2) == 0);

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("2.0.0");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("1.2.3-alpha");
        assertTrue(v1.compareTo(v2) > 0);

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("1.2.3+foo");
        assertTrue(v1.compareTo(v2) < 0);

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("1.2.3-alpha+foo");
        assertTrue(v1.compareTo(v2) > 0);

        v1 = new apippiVersion("1.2.3-alpha+1");
        v2 = new apippiVersion("1.2.3-alpha+2");
        assertTrue(v1.compareTo(v2) < 0);

        v1 = new apippiVersion("4.0-rc2");
        v2 = new apippiVersion("4.0-rc1");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = new apippiVersion("4.0-rc2");
        v2 = new apippiVersion("4.0-rc1");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = new apippiVersion("4.0.0");
        v2 = new apippiVersion("4.0-rc2");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = new apippiVersion("1.2.3-SNAPSHOT");
        v2 = new apippiVersion("1.2.3-alpha");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = new apippiVersion("1.2.3-SNAPSHOT");
        v2 = new apippiVersion("1.2.3-alpha-SNAPSHOT");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = new apippiVersion("1.2.3-SNAPSHOT");
        v2 = new apippiVersion("1.2.3");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        v1 = new apippiVersion("1.2-SNAPSHOT");
        v2 = new apippiVersion("1.2.3");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        v1 = new apippiVersion("1.2.3-SNAPSHOT");
        v2 = new apippiVersion("1.2");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = new apippiVersion("1.2-rc2");
        v2 = new apippiVersion("1.2.3-SNAPSHOT");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        v1 = new apippiVersion("1.2.3-rc2");
        v2 = new apippiVersion("1.2-SNAPSHOT");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);

        v1 = apippiVersion.apippi_4_0;
        v2 = new apippiVersion("4.0.0-SNAPSHOT");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        v1 = new apippiVersion("4.0");
        v2 = new apippiVersion("4.0.0");
        assertTrue(v1.compareTo(v2) == 0);
        assertTrue(v2.compareTo(v1) == 0);

        v1 = new apippiVersion("4.0").familyLowerBound.get();
        v2 = new apippiVersion("4.0.0");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        v1 = new apippiVersion("4.0").familyLowerBound.get();
        v2 = new apippiVersion("4.0");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);

        assertEquals(-1, v1.compareTo(v2));

        v1 = new apippiVersion("1.2.3");
        v2 = new apippiVersion("1.2.3.1");
        assertEquals(-1, v1.compareTo(v2));

        v1 = new apippiVersion("1.2.3.1");
        v2 = new apippiVersion("1.2.3.2");
        assertEquals(-1, v1.compareTo(v2));
    }

    @Test
    public void testInvalid()
    {
        assertThrows("1.0.0a");
        assertThrows("1.a.4");
        assertThrows("1.0.0-foo&");
    }

    @Test
    public void testEquals()
    {
        assertEquals(new apippiVersion("3.0"), new apippiVersion("3.0.0"));
        assertNotEquals(new apippiVersion("3.0"), new apippiVersion("3.0").familyLowerBound.get());
        assertNotEquals(new apippiVersion("3.0.0"), new apippiVersion("3.0.0").familyLowerBound.get());
        assertNotEquals(new apippiVersion("3.0.0"), new apippiVersion("3.0").familyLowerBound.get());
    }

    @Test
    public void testFamilyLowerBound()
    {
        apippiVersion expected = new apippiVersion(3, 0, 0, apippiVersion.NO_HOTFIX, ArrayUtils.EMPTY_STRING_ARRAY, null);
        assertEquals(expected, new apippiVersion("3.0.0-alpha1-SNAPSHOT").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.0-alpha1").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.0-rc1-SNAPSHOT").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.0-rc1").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.0-SNAPSHOT").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.0").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.1-SNAPSHOT").familyLowerBound.get());
        assertEquals(expected, new apippiVersion("3.0.1").familyLowerBound.get());
    }

    @Test
    public void testOrderWithSnapshotsAndFamilyLowerBound()
    {
        List<apippiVersion> expected = Arrays.asList(new apippiVersion("2.0").familyLowerBound.get(),
                                                        new apippiVersion("2.1.5"),
                                                        new apippiVersion("2.1.5.123"),
                                                        new apippiVersion("2.2").familyLowerBound.get(),
                                                        new apippiVersion("2.2.0-beta1-snapshot"),
                                                        new apippiVersion("2.2.0-beta1"),
                                                        new apippiVersion("2.2.0-beta2-SNAPSHOT"),
                                                        new apippiVersion("2.2.0-beta2"),
                                                        new apippiVersion("2.2.0-rc1-snapshot"),
                                                        new apippiVersion("2.2.0-rc1"),
                                                        new apippiVersion("2.2.0-SNAPSHOT"),
                                                        new apippiVersion("2.2.0"),
                                                        new apippiVersion("3.0").familyLowerBound.get(),
                                                        new apippiVersion("3.0-alpha1"),
                                                        new apippiVersion("3.0-alpha2-SNAPSHOT"),
                                                        new apippiVersion("3.0-alpha2"),
                                                        new apippiVersion("3.0-beta1"),
                                                        new apippiVersion("3.0-beta2-SNAPSHOT"),
                                                        new apippiVersion("3.0-beta2"),
                                                        new apippiVersion("3.0-RC1-SNAPSHOT"),
                                                        new apippiVersion("3.0-RC1"),
                                                        new apippiVersion("3.0-RC2-SNAPSHOT"),
                                                        new apippiVersion("3.0-RC2"),
                                                        new apippiVersion("3.0-SNAPSHOT"),
                                                        new apippiVersion("3.0.0"),
                                                        new apippiVersion("3.0.1-SNAPSHOT"),
                                                        new apippiVersion("3.0.1"),
                                                        new apippiVersion("3.2").familyLowerBound.get(),
                                                        new apippiVersion("3.2-SNAPSHOT"),
                                                        new apippiVersion("3.2"));

        for (int i = 0; i < 100; i++)
        {
            List<apippiVersion> shuffled = new ArrayList<>(expected);
            Collections.shuffle(shuffled);

            List<apippiVersion> sorted = new ArrayList<>(shuffled);
            Collections.sort(sorted);
            if (!expected.equals(sorted))
            {
                fail("Expecting " + shuffled + " to be sorted into " + expected + " but was sorted into " + sorted);
            }
        }
    }

    private static void assertThrows(String str)
    {
        try
        {
            new apippiVersion(str);
            fail();
        }
        catch (IllegalArgumentException e) {}
    }

    @Test
    public void testParseIdentifiersPositive() throws Throwable
    {
        String[] result = parseIdentifiers("DUMMY", "a.b.cde.f_g.");
        String[] expected = {"a", "b", "cde", "f_g"};
        assertArrayEquals(expected, result);
    }

    @Test
    public void testParseIdentifiersNegative() throws Throwable
    {
        String version = "DUMMY";
        try
        {
            parseIdentifiers(version, "+a. .b");

        }
        catch (IllegalArgumentException e)
        {
            assertThat(e.getMessage(), containsString(version));
        }
    }

    @Test
    public void testExtraOrdering()
    {
        List<apippiVersion> versions = Arrays.asList(version("4.0.0"),
                                                        version("4.0.0-SNAPSHOT"),
                                                        version("4.0.0.0"),
                                                        version("4.0.0.0-SNAPSHOT"));
        List<apippiVersion> expected = Arrays.asList(version("4.0.0-SNAPSHOT"),
                                                        version("4.0.0"),
                                                        version("4.0.0.0-SNAPSHOT"),
                                                        version("4.0.0.0"));
        Collections.sort(versions);
        Assertions.assertThat(versions).isEqualTo(expected);
    }

    private static apippiVersion version(String str)
    {
        return new apippiVersion(str);
    }

    private static String[] parseIdentifiers(String version, String str) throws Throwable
    {
        String name = "parseIdentifiers";
        Class[] args = {String.class, String.class};
        for (Method m: apippiVersion.class.getDeclaredMethods())
        {
            if (name.equals(m.getName()) &&
                    Arrays.equals(args, m.getParameterTypes()))
            {
                m.setAccessible(true);
                try
                {
                return (String[]) m.invoke(null, version, str);
                } catch (InvocationTargetException e){
                    throw e.getTargetException();
                }
            }
        }
        throw new NoSuchMethodException(apippiVersion.class + "." + name + Arrays.toString(args));
    }
}
