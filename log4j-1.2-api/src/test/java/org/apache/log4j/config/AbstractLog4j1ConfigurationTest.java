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

package org.apache.log4j.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.layout.Log4j1XmlLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LifeCycle.State;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender.Target;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.NullAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.HtmlLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;

public abstract class AbstractLog4j1ConfigurationTest {

    abstract Configuration getConfiguration(String configResourcePrefix) throws URISyntaxException, IOException;

    private Layout<?> testConsole(final String configResourcePrefix) throws Exception {
        final Configuration configuration = getConfiguration(configResourcePrefix);
        final String name = "Console";
        final ConsoleAppender appender = configuration.getAppender(name);
        assertNotNull(
                "Missing appender '" + name + "' in configuration " + configResourcePrefix + " → " + configuration,
                appender);
        assertEquals(Target.SYSTEM_ERR, appender.getTarget());
        //
        final LoggerConfig loggerConfig = configuration.getLoggerConfig("com.example.foo");
        assertNotNull(loggerConfig);
        assertEquals(Level.DEBUG, loggerConfig.getLevel());
        configuration.start();
        configuration.stop();
        return appender.getLayout();
    }

    private Layout<?> testFile(final String configResourcePrefix) throws Exception {
        final Configuration configuration = getConfiguration(configResourcePrefix);
        final FileAppender appender = configuration.getAppender("File");
        assertNotNull(appender);
        assertEquals("target/mylog.txt", appender.getFileName());
        //
        final LoggerConfig loggerConfig = configuration.getLoggerConfig("com.example.foo");
        assertNotNull(loggerConfig);
        assertEquals(Level.DEBUG, loggerConfig.getLevel());
        configuration.start();
        configuration.stop();
        return appender.getLayout();
    }

    public void testConsoleEnhancedPatternLayout() throws Exception {
        final PatternLayout layout = (PatternLayout) testConsole("config-1.2/log4j-console-EnhancedPatternLayout");
        assertEquals("%d{ISO8601} [%t][%c] %-5p %properties %ndc: %m%n", layout.getConversionPattern());
    }

    public void testConsoleHtmlLayout() throws Exception {
        final HtmlLayout layout = (HtmlLayout) testConsole("config-1.2/log4j-console-HtmlLayout");
        assertEquals("Headline", layout.getTitle());
        assertTrue(layout.isLocationInfo());
    }

    public void testConsolePatternLayout() throws Exception {
        final PatternLayout layout = (PatternLayout) testConsole("config-1.2/log4j-console-PatternLayout");
        assertEquals("%d{ISO8601} [%t][%c] %-5p: %m%n", layout.getConversionPattern());
    }

    public void testConsoleSimpleLayout() throws Exception {
        final PatternLayout layout = (PatternLayout) testConsole("config-1.2/log4j-console-SimpleLayout");
        assertEquals("%level - %m%n", layout.getConversionPattern());
    }

    public void testConsoleTtccLayout() throws Exception {
        final PatternLayout layout = (PatternLayout) testConsole("config-1.2/log4j-console-TTCCLayout");
        assertEquals("%r [%t] %p %notEmpty{%ndc }- %m%n", layout.getConversionPattern());
    }

    public void testConsoleXmlLayout() throws Exception {
        final Log4j1XmlLayout layout = (Log4j1XmlLayout) testConsole("config-1.2/log4j-console-XmlLayout");
        assertTrue(layout.isLocationInfo());
        assertFalse(layout.isProperties());
    }

    public void testFileSimpleLayout() throws Exception {
        final PatternLayout layout = (PatternLayout) testFile("config-1.2/log4j-file-SimpleLayout");
        assertEquals("%level - %m%n", layout.getConversionPattern());
    }

    public void testNullAppender() throws Exception {
        final Configuration configuration = getConfiguration("config-1.2/log4j-NullAppender");
        final Appender appender = configuration.getAppender("NullAppender");
        assertNotNull(appender);
        assertEquals("NullAppender", appender.getName());
        assertTrue(appender.getClass().getName(), appender instanceof NullAppender);
    }

    public void testRollingFileAppender() throws Exception {
        testRollingFileAppender("config-1.2/log4j-RollingFileAppender", "RFA", "target/hadoop.log.%i");
    }

    public void testDailyRollingFileAppender() throws Exception {
        testDailyRollingFileAppender("config-1.2/log4j-DailyRollingFileAppender", "DRFA",
                "target/hadoop.log%d{.yyyy-MM-dd}");
    }

    public void testRollingFileAppenderWithProperties() throws Exception {
        testRollingFileAppender("config-1.2/log4j-RollingFileAppender-with-props", "RFA", "target/hadoop.log.%i");
    }

    public void testSystemProperties1() throws Exception {
        final String tempFileName = System.getProperty("java.io.tmpdir") + "/hadoop.log";
        final Path tempFilePath = new File(tempFileName).toPath();
        Files.deleteIfExists(tempFilePath);
        try {
            final Configuration configuration = getConfiguration("config-1.2/log4j-system-properties-1");
            final RollingFileAppender appender = configuration.getAppender("RFA");
            appender.stop(10, TimeUnit.SECONDS);
            // System.out.println("expected: " + tempFileName + " Actual: " +
            // appender.getFileName());
            assertEquals(tempFileName, appender.getFileName());
        } finally {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (final FileSystemException e) {
                e.printStackTrace();
            }
        }
    }

    public void testSystemProperties2() throws Exception {
        final Configuration configuration = getConfiguration("config-1.2/log4j-system-properties-2");
        final RollingFileAppender appender = configuration.getAppender("RFA");
        final String tmpDir = System.getProperty("java.io.tmpdir");
        assertEquals(tmpDir + "/hadoop.log", appender.getFileName());
        // The appender should be in the INITIALIZED state, so no cleanup
        // necessary
        if (appender.getState() == State.STARTED) {
            appender.stop(10, TimeUnit.SECONDS);
            try {
                Path path = new File(appender.getFileName()).toPath();
                Files.deleteIfExists(path);
                path = new File("${java.io.tmpdir}").toPath();
                Files.deleteIfExists(path);
            } catch (FileSystemException e) {
                e.printStackTrace();
            }
        }
    }

    private void testRollingFileAppender(final String configResourcePrefix, final String name, final String filePattern)
            throws Exception {
        final Configuration configuration = getConfiguration(configResourcePrefix);
        final Appender appender = configuration.getAppender(name);
        assertNotNull(appender);
        assertEquals(name, appender.getName());
        assertTrue(appender.getClass().getName(), appender instanceof RollingFileAppender);
        final RollingFileAppender rfa = (RollingFileAppender) appender;
        assertEquals("target/hadoop.log", rfa.getFileName());
        assertEquals(filePattern, rfa.getFilePattern());
        final TriggeringPolicy triggeringPolicy = rfa.getTriggeringPolicy();
        assertNotNull(triggeringPolicy);
        assertTrue(triggeringPolicy.getClass().getName(), triggeringPolicy instanceof CompositeTriggeringPolicy);
        final CompositeTriggeringPolicy ctp = (CompositeTriggeringPolicy) triggeringPolicy;
        final TriggeringPolicy[] triggeringPolicies = ctp.getTriggeringPolicies();
        assertEquals(1, triggeringPolicies.length);
        final TriggeringPolicy tp = triggeringPolicies[0];
        assertTrue(tp.getClass().getName(), tp instanceof SizeBasedTriggeringPolicy);
        final SizeBasedTriggeringPolicy sbtp = (SizeBasedTriggeringPolicy) tp;
        assertEquals(256 * 1024 * 1024, sbtp.getMaxFileSize());
        final RolloverStrategy rolloverStrategy = rfa.getManager().getRolloverStrategy();
        assertTrue(rolloverStrategy.getClass().getName(), rolloverStrategy instanceof DefaultRolloverStrategy);
        final DefaultRolloverStrategy drs = (DefaultRolloverStrategy) rolloverStrategy;
        assertEquals(20, drs.getMaxIndex());
        configuration.start();
        configuration.stop();
    }

    private void testDailyRollingFileAppender(final String configResourcePrefix, final String name,
            final String filePattern) throws Exception {
        final Configuration configuration = getConfiguration(configResourcePrefix);
        final Appender appender = configuration.getAppender(name);
        assertNotNull(appender);
        assertEquals(name, appender.getName());
        assertTrue(appender.getClass().getName(), appender instanceof RollingFileAppender);
        final RollingFileAppender rfa = (RollingFileAppender) appender;
        assertEquals("target/hadoop.log", rfa.getFileName());
        assertEquals(filePattern, rfa.getFilePattern());
        final TriggeringPolicy triggeringPolicy = rfa.getTriggeringPolicy();
        assertNotNull(triggeringPolicy);
        assertTrue(triggeringPolicy.getClass().getName(), triggeringPolicy instanceof CompositeTriggeringPolicy);
        final CompositeTriggeringPolicy ctp = (CompositeTriggeringPolicy) triggeringPolicy;
        final TriggeringPolicy[] triggeringPolicies = ctp.getTriggeringPolicies();
        assertEquals(1, triggeringPolicies.length);
        final TriggeringPolicy tp = triggeringPolicies[0];
        assertTrue(tp.getClass().getName(), tp instanceof TimeBasedTriggeringPolicy);
        final TimeBasedTriggeringPolicy tbtp = (TimeBasedTriggeringPolicy) tp;
        assertEquals(1, tbtp.getInterval());
        final RolloverStrategy rolloverStrategy = rfa.getManager().getRolloverStrategy();
        assertTrue(rolloverStrategy.getClass().getName(), rolloverStrategy instanceof DefaultRolloverStrategy);
        final DefaultRolloverStrategy drs = (DefaultRolloverStrategy) rolloverStrategy;
        assertEquals(Integer.MAX_VALUE, drs.getMaxIndex());
        configuration.start();
        configuration.stop();
    }

    public void testRecursiveProperties() throws Exception {
        final Configuration configuration = getConfiguration("config-1.2/log4j-file-recursive");
        final FileAppender appender = configuration.getAppender("File");
        assertNotNull(appender);
        assertEquals("target/path/to/the/logFile.txt", appender.getFileName());
        //
        final LoggerConfig loggerConfig = configuration.getLoggerConfig("com.example.foo");
        assertNotNull(loggerConfig);
        assertEquals(Level.DEBUG, loggerConfig.getLevel());
        final LoggerConfig rootLogger = configuration.getRootLogger();
        assertEquals(Level.TRACE, rootLogger.getLevel());
        appender.stop();
        final List<Path> paths = Arrays.asList(Paths.get("target/path/to/the/logFile.txt"),
                Paths.get("target/path/to/the"), Paths.get("target/path/to"), Paths.get("target/path"));
        paths.forEach(t -> {
            try {
                Files.deleteIfExists(t);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
