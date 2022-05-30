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
package org.apache.logging.log4j.core.async;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.test.CoreLoggerContexts;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

@Tag("async")
@SetSystemProperty(key = ConfigurationFactory.CONFIGURATION_FILE_PROPERTY, value = "AsyncLoggerConfigTest.xml")
public class AsyncLoggerConfigUseAfterShutdownTest {

    @Test
    public void testNoErrorIfLogAfterShutdown() throws Exception {
        final Logger log = LogManager.getLogger("com.foo.Bar");
        log.info("some message");
        CoreLoggerContexts.stopLoggerContext(); // stop async thread

        // call the #logMessage() method to bypass the isEnabled check:
        // before the LOG4J2-639 fix this would throw a NPE
        ((AbstractLogger) log).logMessage("com.foo.Bar", Level.INFO, null, new SimpleMessage("msg"), null);
   }
}
