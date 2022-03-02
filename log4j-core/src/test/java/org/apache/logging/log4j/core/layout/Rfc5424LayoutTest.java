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
package org.apache.logging.log4j.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.BasicConfigurationFactory;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.layout.Rfc5424Layout.TimestampPrecision;
import org.apache.logging.log4j.core.net.Facility;
import org.apache.logging.log4j.core.util.Integers;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.junit.UsingAnyThreadContext;
import org.apache.logging.log4j.message.StructuredDataCollectionMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.test.appender.ListAppender;
import org.apache.logging.log4j.util.ProcessIdUtil;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@UsingAnyThreadContext
public class Rfc5424LayoutTest {
    LoggerContext ctx = LoggerContext.getContext();
    Logger root = ctx.getRootLogger();

    private static final String PROCESSID = ProcessIdUtil.getProcessId();
    private static final String line1 = String.format("ATM %s - [RequestContext@3692 loginId=\"JohnDoe\"] starting mdc pattern test", PROCESSID);
    private static final String line2 = String.format("ATM %s - [RequestContext@3692 loginId=\"JohnDoe\"] empty mdc", PROCESSID);
    private static final String line3 = String.format("ATM %s - [RequestContext@3692 loginId=\"JohnDoe\"] filled mdc", PROCESSID);
    private static final String line4 =
        String.format("ATM %s Audit [Transfer@18060 Amount=\"200.00\" FromAccount=\"123457\" ToAccount=\"123456\"]" +
        "[RequestContext@3692 ipAddress=\"192.168.0.120\" loginId=\"JohnDoe\"] Transfer Complete", PROCESSID);
    private static final String lineEscaped3 =
            String.format("ATM %s - [RequestContext@3692 escaped=\"Testing escaping #012 \\\" \\] \\\"\" loginId=\"JohnDoe\"] filled mdc", PROCESSID);
    private static final String lineEscaped4 =
        String.format("ATM %s Audit [Transfer@18060 Amount=\"200.00\" FromAccount=\"123457\" ToAccount=\"123456\"]" +
        "[RequestContext@3692 escaped=\"Testing escaping #012 \\\" \\] \\\"\" ipAddress=\"192.168.0.120\" loginId=\"JohnDoe\"] Transfer Complete",
            PROCESSID);
    private static final String collectionLine1 = "[Transfer@18060 Amount=\"200.00\" FromAccount=\"123457\" " +
            "ToAccount=\"123456\"]";
    private static final String collectionLine2 = "[Extra@18060 Item1=\"Hello\" Item2=\"World\"]";
    private static final String collectionLine3 = "[RequestContext@3692 ipAddress=\"192.168.0.120\" loginId=\"JohnDoe\"]";
    private static final String collectionEndOfLine = "Transfer Complete";

    private static final Pattern milliTimestampPattern = Pattern
            .compile("<\\d+>1 (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}[+-]\\d{2}:\\d{2}) .*");
    private static final Pattern microTimestampPattern = Pattern
            .compile("<\\d+>1 (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{6}[+-]\\d{2}:\\d{2}) .*");


    static ConfigurationFactory cf = new BasicConfigurationFactory();

    @BeforeAll
    public static void setupClass() {
        StatusLogger.getLogger().setLevel(Level.OFF);
        ConfigurationFactory.setConfigurationFactory(cf);
        final LoggerContext ctx = LoggerContext.getContext();
        ctx.reconfigure();
    }

    @AfterAll
    public static void cleanupClass() {
        ConfigurationFactory.removeConfigurationFactory(cf);
    }

    /**
     * Test case for MDC conversion pattern.
     */
    @Test
    public void testLayout() throws Exception {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up appender
        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
            null, null, true, null, "ATM", null, "key1, key2, locale", null, "loginId", null, true, null, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        ThreadContext.put("loginId", "JohnDoe");

        // output starting message
        root.debug("starting mdc pattern test");

        root.debug("empty mdc");

        ThreadContext.put("key1", "value1");
        ThreadContext.put("key2", "value2");

        root.debug("filled mdc");

        ThreadContext.put("ipAddress", "192.168.0.120");
        ThreadContext.put("locale", Locale.US.getDisplayName());
        try {
            final StructuredDataMessage msg = new StructuredDataMessage("Transfer@18060", "Transfer Complete", "Audit");
            msg.put("ToAccount", "123456");
            msg.put("FromAccount", "123457");
            msg.put("Amount", "200.00");
            root.info(MarkerManager.getMarker("EVENT"), msg);

            List<String> list = appender.getMessages();

            assertTrue(list.get(0).endsWith(line1), "Expected line 1 to end with: " + line1 + " Actual " + list.get(0));
            assertTrue(list.get(1).endsWith(line2), "Expected line 2 to end with: " + line2 + " Actual " + list.get(1));
            assertTrue(list.get(2).endsWith(line3), "Expected line 3 to end with: " + line3 + " Actual " + list.get(2));
            assertTrue(list.get(3).endsWith(line4), "Expected line 4 to end with: " + line4 + " Actual " + list.get(3));

            for (final String frame : list) {
                int length = -1;
                final int frameLength = frame.length();
                final int firstSpacePosition = frame.indexOf(' ');
                final String messageLength = frame.substring(0, firstSpacePosition);
                try {
                    length = Integers.parseInt(messageLength);
                    // the ListAppender removes the ending newline, so we expect one less size
                    assertEquals(frameLength, messageLength.length() + length);
                }
                catch (final NumberFormatException e) {
                    fail("Not a valid RFC 5425 frame");
                }
            }

            appender.clear();

            ThreadContext.remove("loginId");

            root.debug("This is a test");

            list = appender.getMessages();
            assertTrue(list.isEmpty(), "No messages expected, found " + list.size());
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    /**
     * Test case for MDC conversion pattern.
     */
    @Test
    public void testCollection() throws Exception {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up appender
        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
                null, null, true, null, "ATM", null, "key1, key2, locale", null, "loginId", null, true, null, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        ThreadContext.put("loginId", "JohnDoe");
        ThreadContext.put("ipAddress", "192.168.0.120");
        ThreadContext.put("locale", Locale.US.getDisplayName());
        try {
            final StructuredDataMessage msg = new StructuredDataMessage("Transfer@18060", "Transfer Complete", "Audit");
            msg.put("ToAccount", "123456");
            msg.put("FromAccount", "123457");
            msg.put("Amount", "200.00");
            final StructuredDataMessage msg2 = new StructuredDataMessage("Extra@18060", null, "Audit");
            msg2.put("Item1", "Hello");
            msg2.put("Item2", "World");
            final List<StructuredDataMessage> messages = new ArrayList<>();
            messages.add(msg);
            messages.add(msg2);
            final StructuredDataCollectionMessage collectionMessage = new StructuredDataCollectionMessage(messages);

            root.info(MarkerManager.getMarker("EVENT"), collectionMessage);

            List<String> list = appender.getMessages();
            String result = list.get(0);
            assertTrue(
                    result.contains(collectionLine1), "Expected line to contain " + collectionLine1 + ", Actual " + result);
            assertTrue(
                    result.contains(collectionLine2), "Expected line to contain " + collectionLine2 + ", Actual " + result);
            assertTrue(
                    result.contains(collectionLine3), "Expected line to contain " + collectionLine3 + ", Actual " + result);
            assertTrue(
                    result.endsWith(collectionEndOfLine),
                    "Expected line to end with: " + collectionEndOfLine + " Actual " + result);

            for (final String frame : list) {
                int length = -1;
                final int frameLength = frame.length();
                final int firstSpacePosition = frame.indexOf(' ');
                final String messageLength = frame.substring(0, firstSpacePosition);
                try {
                    length = Integers.parseInt(messageLength);
                    // the ListAppender removes the ending newline, so we expect one less size
                    assertEquals(frameLength, messageLength.length() + length);
                }
                catch (final NumberFormatException e) {
                    fail("Not a valid RFC 5425 frame");
                }
            }

            appender.clear();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    /**
     * Test case for escaping newlines and other SD PARAM-NAME special characters.
     */
    @Test
    public void testEscape() throws Exception {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up layout/appender
        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
            null, null, true, "#012", "ATM", null, "key1, key2, locale", null, "loginId", null, true, null, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        ThreadContext.put("loginId", "JohnDoe");

        // output starting message
        root.debug("starting mdc pattern test");

        root.debug("empty mdc");

        ThreadContext.put("escaped", "Testing escaping \n \" ] \"");

        root.debug("filled mdc");

        ThreadContext.put("ipAddress", "192.168.0.120");
        ThreadContext.put("locale", Locale.US.getDisplayName());
        try {
            final StructuredDataMessage msg = new StructuredDataMessage("Transfer@18060", "Transfer Complete", "Audit");
            msg.put("ToAccount", "123456");
            msg.put("FromAccount", "123457");
            msg.put("Amount", "200.00");
            root.info(MarkerManager.getMarker("EVENT"), msg);

            List<String> list = appender.getMessages();

            assertTrue(list.get(0).endsWith(line1), "Expected line 1 to end with: " + line1 + " Actual " + list.get(0));
            assertTrue(list.get(1).endsWith(line2), "Expected line 2 to end with: " + line2 + " Actual " + list.get(1));
            assertTrue(list.get(2).endsWith(lineEscaped3),
                    "Expected line 3 to end with: " + lineEscaped3 + " Actual " + list.get(2));
            assertTrue(list.get(3).endsWith(lineEscaped4),
                    "Expected line 4 to end with: " + lineEscaped4 + " Actual " + list.get(3));

            appender.clear();

            ThreadContext.remove("loginId");

            root.debug("This is a test");

            list = appender.getMessages();
            assertTrue(list.isEmpty(), "No messages expected, found " + list.size());
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    /**
     * Test case for MDC exception conversion pattern.
     */
    @Test
    public void testException() throws Exception {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up layout/appender
        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
            null, null, true, null, "ATM", null, "key1, key2, locale", null, "loginId", "%xEx", true, null, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);
        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        ThreadContext.put("loginId", "JohnDoe");

        // output starting message
        root.debug("starting mdc pattern test", new IllegalArgumentException("Test"));

        try {

            final List<String> list = appender.getMessages();

            assertTrue(list.size() > 1, "Not enough list entries");
            final String string = list.get(1);
            assertTrue(string.contains("IllegalArgumentException"), "No Exception in " + string);

            appender.clear();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    /**
     * Test case for MDC logger field inclusion.
     */
    @Test
    public void testMDCLoggerFields() throws Exception {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }

        final LoggerFields[] loggerFields = new LoggerFields[] {
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("source", "%C.%M")}, null, null, false),
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("source2", "%C.%M")}, null, null, false)
        };

        // set up layout/appender
        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
            null, null, true, null, "ATM", null, "key1, key2, locale", null, null, null, true, loggerFields, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);
        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        // output starting message
        root.info("starting logger fields test");

        try {

            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            assertTrue(list.get(0).contains("Rfc5424LayoutTest.testMDCLoggerFields"), "No class/method");

            appender.clear();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testLoggerFields() {
        final String[] fields = new String[] {
                "[BAZ@32473 baz=\"org.apache.logging.log4j.core.layout.Rfc5424LayoutTest.testLoggerFields\"]",
                "[RequestContext@3692 bar=\"org.apache.logging.log4j.core.layout.Rfc5424LayoutTest.testLoggerFields\"]",
                "[SD-ID@32473 source=\"org.apache.logging.log4j.core.layout.Rfc5424LayoutTest.testLoggerFields\"]"
        };
        final List<String> expectedToContain = Arrays.asList(fields);

        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }

        final LoggerFields[] loggerFields = new LoggerFields[] {
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("source", "%C.%M")}, "SD-ID",
                        "32473", false),
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("baz", "%C.%M"),
                        new KeyValuePair("baz", "%C.%M") }, "BAZ", "32473", false),
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("bar", "%C.%M")}, null, null, false)
        };

        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
                null, null, true, null, "ATM", null, "key1, key2, locale", null, null, null, false, loggerFields, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);
        appender.start();

        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        root.info("starting logger fields test");

        try {

            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message =  list.get(0);
            assertTrue(message.contains("Rfc5424LayoutTest.testLoggerFields"), "No class/method");
            for (final String value : expectedToContain) {
                assertTrue(message.contains(value), "Message expected to contain " + value + " but did not");
            }
            appender.clear();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testDiscardEmptyLoggerFields() {
        final String mdcId = "RequestContext";

        Arrays.asList(
                "[BAZ@32473 baz=\"org.apache.logging.log4j.core.layout.Rfc5424LayoutTest.testLoggerFields\"]"  +
                        "[RequestContext@3692 bar=\"org.apache.logging.log4j.core.layout.Rfc5424LayoutTest.testLoggerFields\"]"
        );

        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }

        final LoggerFields[] loggerFields = new LoggerFields[] {
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("dummy", Strings.EMPTY),
                        new KeyValuePair("empty", Strings.EMPTY)}, "SD-ID", "32473", true),
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("baz", "%C.%M"),
                        new KeyValuePair("baz", "%C.%M") }, "BAZ", "32473", false),
                LoggerFields.createLoggerFields(new KeyValuePair[] { new KeyValuePair("bar", "%C.%M")}, null, null, false)
        };

        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, mdcId,
                null, null, true, null, "ATM", null, "key1, key2, locale", null, null, null, false, loggerFields, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);
        appender.start();

        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        root.info("starting logger fields test");

        try {

            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message =  list.get(0);
            assertFalse(message.contains("SD-ID"), "SD-ID should have been discarded");
            assertTrue(message.contains("BAZ"), "BAZ should have been included");
            assertTrue(message.contains(mdcId), mdcId + "should have been included");
            appender.clear();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testSubstituteStructuredData() {
        final String mdcId = "RequestContext";

        final String expectedToContain = String.format("ATM %s MSG-ID - Message", PROCESSID);

        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }

        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, false, mdcId,
                null, null, true, null, "ATM", "MSG-ID", "key1, key2, locale", null, null, null, false, null, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);
        appender.start();

        root.addAppender(appender);
        root.setLevel(Level.DEBUG);

        root.info("Message");

        try {
            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message =  list.get(0);
            assertTrue(message.contains(expectedToContain), "Not the expected message received");
            appender.clear();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testParameterizedMessage() {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up appender
        final AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
            null, null, true, null, "ATM", null, "key1, key2, locale", null, null, null, true, null, null, null);
        final ListAppender appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        root.info("Hello {}", "World");
        try {
            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message =  list.get(0);
            assertTrue(message.contains("Hello World"),
                    "Incorrect message. Expected - Hello World, Actual - " + message);
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testTimestampMilliPrecision() {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up appender layout and appender with millisecond precision
        AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
                null, null, true, null, "ATM", null, "key1, key2, locale", null, null, null, false, null, null,
                TimestampPrecision.MILLISECOND);
        ListAppender appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        root.info("Hello");

        try {
            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message = list.get(0);
            Matcher matcher = milliTimestampPattern.matcher(message);
            assertTrue(matcher.matches(),
                    "Incorrect timstamp format. Expected format - yyyy-MM-ddTHH:mm:ss.SSSXXX, Actual - " + message);
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }

        // set up appender layout and appender with null
        // timestampPrecision. It should default to millisecond
        layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext", null, null, true,
                null, "ATM", null, "key1, key2, locale", null, null, null, false, null, null, null);
        appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        root.info("Hello");
        try {
            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message = list.get(0);
            Matcher matcher = milliTimestampPattern.matcher(message);
            assertTrue(matcher.matches(),
                    "Incorrect timstamp format. Expected format - yyyy-MM-ddTHH:mm:ss.SSSXXX, Actual - " + message);
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testTimestampMicroPrecision() {
        for (final Appender appender : root.getAppenders().values()) {
            root.removeAppender(appender);
        }
        // set up appender layout and appender with microsecond precision
        AbstractStringLayout layout = Rfc5424Layout.createLayout(Facility.LOCAL0, "Event", 3692, true, "RequestContext",
                null, null, true, null, "ATM", null, "key1, key2, locale", null, null, null, false, null, null,
                TimestampPrecision.MICROSECOND);
        ListAppender appender = new ListAppender("List", null, layout, true, false);

        appender.start();

        // set appender on root and set level to debug
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        root.info("Hello");

        try {
            final List<String> list = appender.getMessages();
            assertTrue(list.size() > 0, "Not enough list entries");
            final String message = list.get(0);
            Matcher matcher = microTimestampPattern.matcher(message);
            assertTrue(matcher.matches(),
                    "Incorrect timstamp format. Expected format - yyyy-MM-ddTHH:mm:ss.nnnnnnXXX, Actual - " + message);
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }
}
