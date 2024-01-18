/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.core.async;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.impl.Log4jPropertyKey;
import org.apache.logging.log4j.core.test.CoreLoggerContexts;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

/**
 * Test for <a href="https://issues.apache.org/jira/browse/LOG4J2-639">LOG4J2-639</a>
 */
@Tag("async")
@Tag("functional")
public class AsyncLoggerUseAfterShutdownTest {
    @Test
    @SetSystemProperty(
            key = Log4jPropertyKey.Constant.CONTEXT_SELECTOR_CLASS_NAME,
            value = "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
    @SetSystemProperty(key = Log4jPropertyKey.Constant.CONFIG_LOCATION, value = "AsyncLoggerTest.xml")
    public void testNoErrorIfLogAfterShutdown() throws Exception {
        final Logger log = LogManager.getLogger("com.foo.Bar");
        final String msg = "Async logger msg";
        log.info(msg, new InternalError("this is not a real error"));
        CoreLoggerContexts.stopLoggerContext(); // stop async thread

        log.log(Level.INFO, (Marker) null, (Message) new SimpleMessage("msg"), null);
    }
}
