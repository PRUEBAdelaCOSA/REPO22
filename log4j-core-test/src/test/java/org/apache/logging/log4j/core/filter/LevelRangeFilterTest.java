/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.filter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LevelRangeFilterTest {

    @Test
    public void testLevels() {
        final LevelRangeFilter filter = LevelRangeFilter.createFilter(Level.ERROR, Level.INFO, null, null);
        filter.start();
        assertTrue(filter.isStarted());
        assertSame(Filter.Result.DENY, filter.filter(null, Level.DEBUG, null, (Object) null, (Throwable) null));
        assertSame(Filter.Result.NEUTRAL, filter.filter(null, Level.ERROR, null, (Object) null, (Throwable) null));
        LogEvent event = LogEvent.builder() //
                .setLevel(Level.DEBUG) //
                .setMessage(new SimpleMessage("Test")) //
                .get();
        assertSame(Filter.Result.DENY, filter.filter(event));
        event = LogEvent.builder() //
                .setLevel(Level.ERROR) //
                .setMessage(new SimpleMessage("Test")) //
                .get();
        assertSame(Filter.Result.NEUTRAL, filter.filter(event));
    }

    @Test
    public void testMinimumOnlyLevel() {
        final LevelRangeFilter filter = LevelRangeFilter.createFilter(Level.ERROR, null, null, null);
        filter.start();
        assertTrue(filter.isStarted());
        assertSame(Filter.Result.NEUTRAL, filter.filter(null, Level.ERROR, null, (Object) null, (Throwable) null));
    }

}
