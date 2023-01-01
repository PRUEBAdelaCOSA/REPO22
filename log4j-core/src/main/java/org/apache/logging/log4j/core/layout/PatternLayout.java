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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.Log4jProperties;
import org.apache.logging.log4j.core.pattern.FormattingInfo;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.apache.logging.log4j.core.pattern.RegexReplacement;
import org.apache.logging.log4j.core.util.GarbageFreeConfiguration;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginFactory;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.Recycler;
import org.apache.logging.log4j.util.RecyclerFactories;
import org.apache.logging.log4j.util.RecyclerFactory;
import org.apache.logging.log4j.util.StringBuilders;
import org.apache.logging.log4j.util.Strings;

/**
 * A flexible layout configurable with pattern string.
 * <p>
 * The goal of this class is to {@link Layout#toByteArray format} a {@link LogEvent} and
 * return the results. The format of the result depends on the <em>conversion pattern</em>.
 * </p>
 * <p>
 * The conversion pattern is closely related to the conversion pattern of the printf function in C. A conversion pattern
 * is composed of literal text and format control expressions called <em>conversion specifiers</em>.
 * </p>
 * <p>
 * See the Log4j Manual for details on the supported pattern converters.
 * </p>
 */
@Configurable(elementType = Layout.ELEMENT_TYPE, printObject = true)
@Plugin
public final class PatternLayout extends AbstractStringLayout {

    /**
     * Default pattern string for log output. Currently set to the string <b>"%m%n"</b> which just prints the
     * application supplied message.
     */
    public static final String DEFAULT_CONVERSION_PATTERN = "%m%n";

    /**
     * A conversion pattern equivalent to the TTCCLayout. Current value is <b>%r [%t] %p %c %notEmpty{%x }- %m%n</b>.
     */
    public static final String TTCC_CONVERSION_PATTERN = "%r [%t] %p %c %notEmpty{%x }- %m%n";

    /**
     * A simple pattern. Current value is <b>%d [%t] %p %c - %m%n</b>.
     */
    public static final String SIMPLE_CONVERSION_PATTERN = "%d [%t] %p %c - %m%n";

    /** Key to identify pattern converters. */
    public static final String KEY = "Converter";

    /**
     * Conversion pattern.
     */
    private final String conversionPattern;
    private final PatternSelector patternSelector;
    private final Serializer eventSerializer;

    /**
     * Constructs a PatternLayout using the supplied conversion pattern.
     *
     * @param config                The Configuration.
     * @param recyclerFactory       the recycler factory to use for StringBuilder instances
     * @param replace               The regular expression to match.
     * @param eventPattern          conversion pattern.
     * @param patternSelector       The PatternSelector.
     * @param charset               The character set.
     * @param alwaysWriteExceptions Whether or not exceptions should always be handled in this pattern (if {@code true},
     *                              exceptions will be written even if the pattern does not specify so).
     * @param disableAnsi           If {@code "true"}, do not output ANSI escape codes
     * @param noConsoleNoAnsi       If {@code "true"} (default) and {@link System#console()} is null, do not output ANSI escape codes
     * @param headerPattern         header conversion pattern.
     * @param footerPattern         footer conversion pattern.
     */
    private PatternLayout(final Configuration config, final RecyclerFactory recyclerFactory,
                          final RegexReplacement replace, final String eventPattern,
                          final PatternSelector patternSelector, final Charset charset,
                          final boolean alwaysWriteExceptions, final boolean disableAnsi,
                          final boolean noConsoleNoAnsi, final String headerPattern,
                          final String footerPattern) {
        super(config, charset,
                newSerializerBuilder()
                        .setConfiguration(config)
                        .setRecyclerFactory(recyclerFactory)
                        .setReplace(replace)
                        .setPatternSelector(patternSelector)
                        .setAlwaysWriteExceptions(alwaysWriteExceptions)
                        .setDisableAnsi(disableAnsi)
                        .setNoConsoleNoAnsi(noConsoleNoAnsi)
                        .setPattern(headerPattern)
                        .build(),
                newSerializerBuilder()
                        .setConfiguration(config)
                        .setRecyclerFactory(recyclerFactory)
                        .setReplace(replace)
                        .setPatternSelector(patternSelector)
                        .setAlwaysWriteExceptions(alwaysWriteExceptions)
                        .setDisableAnsi(disableAnsi)
                        .setNoConsoleNoAnsi(noConsoleNoAnsi)
                        .setPattern(footerPattern)
                        .build());
        this.conversionPattern = eventPattern;
        this.patternSelector = patternSelector;
        this.eventSerializer = newSerializerBuilder()
                .setConfiguration(config)
                .setRecyclerFactory(recyclerFactory)
                .setReplace(replace)
                .setPatternSelector(patternSelector)
                .setAlwaysWriteExceptions(alwaysWriteExceptions)
                .setDisableAnsi(disableAnsi)
                .setNoConsoleNoAnsi(noConsoleNoAnsi)
                .setPattern(eventPattern)
                .setDefaultPattern(DEFAULT_CONVERSION_PATTERN)
                .build();
    }

    public static SerializerBuilder newSerializerBuilder() {
        return new SerializerBuilder();
    }

    @Override
    public boolean requiresLocation() {
        return eventSerializer.requiresLocation();
    }

    /**
     * Gets the conversion pattern.
     *
     * @return the conversion pattern.
     */
    public String getConversionPattern() {
        return conversionPattern;
    }

    /**
     * Gets this PatternLayout's content format. Specified by:
     * <ul>
     * <li>Key: "structured" Value: "false"</li>
     * <li>Key: "formatType" Value: "conversion" (format uses the keywords supported by OptionConverter)</li>
     * <li>Key: "format" Value: provided "conversionPattern" param</li>
     * </ul>
     *
     * @return Map of content format keys supporting PatternLayout
     */
    @Override
    public Map<String, String> getContentFormat() {
        return Map.of(
                "structured", "false",
                "formatType", "conversion",
                "format", conversionPattern);
    }

    /**
     * Formats a logging event to a writer.
     *
     * @param event logging event to be formatted.
     * @return The event formatted as a String.
     */
    @Override
    public String toSerializable(final LogEvent event) {
        return eventSerializer.toSerializable(event);
    }

    public void serialize(final LogEvent event, final StringBuilder stringBuilder) {
        eventSerializer.toSerializable(event, stringBuilder);
    }

    @Override
    public void encode(final LogEvent event, final ByteBufferDestination destination) {
        final StringBuilder buf = getStringBuilder();
        try {
            final StringBuilder text = toText(eventSerializer, event, buf);
            final Encoder<StringBuilder> encoder = getStringBuilderEncoder();
            encoder.encode(text, destination);
        } finally {
            recycleStringBuilder(buf);
        }
    }

    /**
     * Creates a text representation of the specified log event
     * and writes it into the specified StringBuilder.
     * <p>
     * Implementations are free to return a new StringBuilder if they can
     * detect in advance that the specified StringBuilder is too small.
     */
    private StringBuilder toText(final Serializer2 serializer, final LogEvent event,
            final StringBuilder destination) {
        return serializer.toSerializable(event, destination);
    }

    /**
     * Creates a PatternParser.
     * @param config The Configuration.
     * @return The PatternParser.
     */
    public static PatternParser createPatternParser(final Configuration config) {
        if (config == null) {
            return new PatternParser(config, KEY, LogEventPatternConverter.class);
        }
        PatternParser parser = config.getComponent(KEY);
        if (parser == null) {
            parser = new PatternParser(config, KEY, LogEventPatternConverter.class);
            config.addComponent(KEY, parser);
            parser = config.getComponent(KEY);
        }
        return parser;
    }

    @Override
    public String toString() {
        return patternSelector == null ? conversionPattern : patternSelector.toString();
    }

    private interface PatternSerializer extends Serializer, Serializer2 {}

    private static final class NoFormatPatternSerializer implements PatternSerializer {

        private final Recycler<StringBuilder> recycler;
        private final LogEventPatternConverter[] converters;

        private NoFormatPatternSerializer(final Recycler<StringBuilder> recycler, final PatternFormatter[] formatters) {
            this.recycler = recycler;
            this.converters = new LogEventPatternConverter[formatters.length];
            for (int i = 0; i < formatters.length; i++) {
                converters[i] = formatters[i].getConverter();
            }
        }

        @Override
        public String toSerializable(final LogEvent event) {
            final StringBuilder sb = recycler.acquire();
            try {
                return toSerializable(event, sb).toString();
            } finally {
                recycler.release(sb);
            }
        }

        @Override
        public StringBuilder toSerializable(final LogEvent event, final StringBuilder buffer) {
            for (LogEventPatternConverter converter : converters) {
                converter.format(event, buffer);
            }
            return buffer;
        }

        @Override
        public boolean requiresLocation() {
            for (LogEventPatternConverter converter : converters) {
                if (converter.requiresLocation()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return super.toString() + "[converters=" + Arrays.toString(converters) + "]";
        }
    }

    private static final class PatternFormatterPatternSerializer implements PatternSerializer {

        private final Recycler<StringBuilder> recycler;
        private final PatternFormatter[] formatters;

        private PatternFormatterPatternSerializer(final Recycler<StringBuilder> recycler, final PatternFormatter[] formatters) {
            this.recycler = recycler;
            this.formatters = formatters;
        }

        @Override
        public String toSerializable(final LogEvent event) {
            final StringBuilder sb = recycler.acquire();
            try {
                return toSerializable(event, sb).toString();
            } finally {
                recycler.release(sb);
            }
        }

        @Override
        public StringBuilder toSerializable(final LogEvent event, final StringBuilder buffer) {
            for (PatternFormatter formatter : formatters) {
                formatter.format(event, buffer);
            }
            return buffer;
        }

        @Override
        public String toString() {
            return super.toString() +
                    "[formatters=" +
                    Arrays.toString(formatters) +
                    "]";
        }
    }

    private static final class PatternSerializerWithReplacement implements Serializer, Serializer2 {

        private final Recycler<StringBuilder> recycler;
        private final PatternSerializer delegate;
        private final RegexReplacement replace;

        private PatternSerializerWithReplacement(final Recycler<StringBuilder> recycler,
                                                 final PatternSerializer delegate,
                                                 final RegexReplacement replace) {
            this.recycler = recycler;
            this.delegate = delegate;
            this.replace = replace;
        }

        @Override
        public String toSerializable(final LogEvent event) {
            final StringBuilder sb = recycler.acquire();
            try {
                return toSerializable(event, sb).toString();
            } finally {
                recycler.release(sb);
            }
        }

        @Override
        public StringBuilder toSerializable(final LogEvent event, final StringBuilder buf) {
            StringBuilder buffer = delegate.toSerializable(event, buf);
            String str = buffer.toString();
            str = replace.format(str);
            buffer.setLength(0);
            buffer.append(str);
            return buffer;
        }



        @Override
        public String toString() {
            return super.toString() +
                    "[delegate=" +
                    delegate +
                    ", replace=" +
                    replace +
                    "]";
        }

        @Override
        public boolean requiresLocation() {
            return delegate.requiresLocation();
        }
    }

    public static class SerializerBuilder implements org.apache.logging.log4j.plugins.util.Builder<Serializer> {

        private Configuration configuration;
        private RecyclerFactory recyclerFactory;
        private RegexReplacement replace;
        private String pattern;
        private String defaultPattern;
        private PatternSelector patternSelector;
        private boolean alwaysWriteExceptions;
        private boolean disableAnsi;
        private boolean noConsoleNoAnsi;

        @Override
        public Serializer build() {
            if (Strings.isEmpty(pattern) && Strings.isEmpty(defaultPattern)) {
                return null;
            }
            if (recyclerFactory == null) {
                recyclerFactory = configuration != null
                        ? configuration.getInstance(RecyclerFactory.class)
                        : RecyclerFactories.ofSpec(null);
            }
            final GarbageFreeConfiguration gfConfig = configuration != null
                    ? configuration.getInstance(GarbageFreeConfiguration.class)
                    : GarbageFreeConfiguration.getDefaultConfiguration();
            final Recycler<StringBuilder> recycler = recyclerFactory.create(
                    () -> new StringBuilder(DEFAULT_STRING_BUILDER_SIZE),
                    buf -> {
                        StringBuilders.trimToMaxSize(buf, gfConfig.getLayoutStringBuilderMaxSize());
                        buf.setLength(0);
                    }
            );
            if (patternSelector == null) {
                try {
                    final PatternParser parser = createPatternParser(configuration);
                    final List<PatternFormatter> list = parser.parse(pattern == null ? defaultPattern : pattern,
                            alwaysWriteExceptions, disableAnsi, noConsoleNoAnsi);
                    final PatternFormatter[] formatters = list.toArray(new PatternFormatter[0]);
                    boolean hasFormattingInfo = false;
                    for (PatternFormatter formatter : formatters) {
                        FormattingInfo info = formatter.getFormattingInfo();
                        if (info != null && info != FormattingInfo.getDefault()) {
                            hasFormattingInfo = true;
                            break;
                        }
                    }
                    PatternSerializer serializer = hasFormattingInfo
                            ? new PatternFormatterPatternSerializer(recycler, formatters)
                            : new NoFormatPatternSerializer(recycler, formatters);
                    return replace == null ? serializer : new PatternSerializerWithReplacement(recycler, serializer, replace);
                } catch (final RuntimeException ex) {
                    throw new IllegalArgumentException("Cannot parse pattern '" + pattern + "'", ex);
                }
            }
            return new PatternSelectorSerializer(recycler, patternSelector, replace);
        }

        public SerializerBuilder setConfiguration(final Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        public SerializerBuilder setRecyclerFactory(final RecyclerFactory recyclerFactory) {
            this.recyclerFactory = recyclerFactory;
            return this;
        }

        public SerializerBuilder setReplace(final RegexReplacement replace) {
            this.replace = replace;
            return this;
        }

        public SerializerBuilder setPattern(final String pattern) {
            this.pattern = pattern;
            return this;
        }

        public SerializerBuilder setDefaultPattern(final String defaultPattern) {
            this.defaultPattern = defaultPattern;
            return this;
        }

        public SerializerBuilder setPatternSelector(final PatternSelector patternSelector) {
            this.patternSelector = patternSelector;
            return this;
        }

        public SerializerBuilder setAlwaysWriteExceptions(final boolean alwaysWriteExceptions) {
            this.alwaysWriteExceptions = alwaysWriteExceptions;
            return this;
        }

        public SerializerBuilder setDisableAnsi(final boolean disableAnsi) {
            this.disableAnsi = disableAnsi;
            return this;
        }

        public SerializerBuilder setNoConsoleNoAnsi(final boolean noConsoleNoAnsi) {
            this.noConsoleNoAnsi = noConsoleNoAnsi;
            return this;
        }

    }

    private static final class PatternSelectorSerializer implements Serializer, Serializer2 {

        private final Recycler<StringBuilder> recycler;
        private final PatternSelector patternSelector;
        private final RegexReplacement replace;

        private PatternSelectorSerializer(final Recycler<StringBuilder> recycler,
                                          final PatternSelector patternSelector,
                                          final RegexReplacement replace) {
            super();
            this.recycler = recycler;
            this.patternSelector = patternSelector;
            this.replace = replace;
        }

        @Override
        public String toSerializable(final LogEvent event) {
            final StringBuilder sb = recycler.acquire();
            try {
                return toSerializable(event, sb).toString();
            } finally {
                recycler.release(sb);
            }
        }

        @Override
        public StringBuilder toSerializable(final LogEvent event, final StringBuilder buffer) {
            for (PatternFormatter formatter : patternSelector.getFormatters(event)) {
                formatter.format(event, buffer);
            }
            if (replace != null) { // creates temporary objects
                String str = buffer.toString();
                str = replace.format(str);
                buffer.setLength(0);
                buffer.append(str);
            }
            return buffer;
        }

        @Override
        public boolean requiresLocation() {
            return patternSelector.requiresLocation();
        }

        @Override
        public String toString() {
            return super.toString() + "[patternSelector=" + patternSelector + ", replace=" + replace + "]";
        }
    }

    /**
     * Creates a PatternLayout using the default options and the given configuration. These options include using UTF-8,
     * the default conversion pattern, exceptions being written, and with ANSI escape codes.
     *
     * @param configuration The Configuration.
     *
     * @return the PatternLayout.
     * @see #DEFAULT_CONVERSION_PATTERN Default conversion pattern
     */
    public static PatternLayout createDefaultLayout(final Configuration configuration) {
        return newBuilder().setConfiguration(configuration).build();
    }

    /**
     * Creates a builder for a custom PatternLayout.
     *
     * @return a PatternLayout builder.
     */
    @PluginFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Custom PatternLayout builder. Use the {@link PatternLayout#newBuilder() builder factory method} to create this.
     */
    public static class Builder implements org.apache.logging.log4j.plugins.util.Builder<PatternLayout> {

        @PluginBuilderAttribute
        private String pattern = PatternLayout.DEFAULT_CONVERSION_PATTERN;

        @PluginElement("PatternSelector")
        private PatternSelector patternSelector;

        private Configuration configuration;

        private RecyclerFactory recyclerFactory;

        @PluginElement("Replace")
        private RegexReplacement regexReplacement;

        // LOG4J2-783 use platform default by default
        @PluginBuilderAttribute
        private Charset charset = Charset.defaultCharset();

        @PluginBuilderAttribute
        private boolean alwaysWriteExceptions = true;

        @PluginBuilderAttribute
        private Boolean disableAnsi;

        @PluginBuilderAttribute
        private boolean noConsoleNoAnsi;

        @PluginBuilderAttribute
        private String header;

        @PluginBuilderAttribute
        private String footer;

        private Builder() {
        }

        private Configuration getConfiguration() {
            if (configuration == null) {
                configuration = new DefaultConfiguration();
            }
            return configuration;
        }

        private boolean isDisableAnsi() {
            if (disableAnsi != null) {
                return disableAnsi;
            }
            final boolean enableJansi = getConfiguration().getPropertyResolver().getBoolean(Log4jProperties.JANSI_ENABLED);
            return !enableJansi && Constants.isWindows();
        }

        /**
         * @param pattern
         *        The pattern. If not specified, defaults to DEFAULT_CONVERSION_PATTERN.
         */
        public Builder setPattern(final String pattern) {
            this.pattern = pattern;
            return this;
        }

        /**
         * @param patternSelector
         *        Allows different patterns to be used based on some selection criteria.
         */
        public Builder setPatternSelector(final PatternSelector patternSelector) {
            this.patternSelector = patternSelector;
            return this;
        }

        /**
         * @param configuration
         *        The Configuration. Some Converters require access to the Interpolator.
         */
        @Inject
        public Builder setConfiguration(final Configuration configuration) {
            this.configuration = configuration;
            return this;
        }


        @Inject
        public Builder setRecyclerFactory(final RecyclerFactory recyclerFactory) {
            this.recyclerFactory = recyclerFactory;
            return this;
        }

        /**
         * @param regexReplacement
         *        A Regex replacement
         */
        public Builder setRegexReplacement(final RegexReplacement regexReplacement) {
            this.regexReplacement = regexReplacement;
            return this;
        }

        /**
         * @param charset
         *        The character set. The platform default is used if not specified.
         */
        public Builder setCharset(final Charset charset) {
            // LOG4J2-783 if null, use platform default by default
            if (charset != null) {
                this.charset = charset;
            }
            return this;
        }

        /**
         * @param alwaysWriteExceptions
         *        If {@code "true"} (default) exceptions are always written even if the pattern contains no exception tokens.
         */
        public Builder setAlwaysWriteExceptions(final boolean alwaysWriteExceptions) {
            this.alwaysWriteExceptions = alwaysWriteExceptions;
            return this;
        }

        /**
         * @param disableAnsi
         *        If {@code "true"} (default is the opposite value of system property {@value Log4jProperties#JANSI_ENABLED}, or `true` if undefined),
         *        do not output ANSI escape codes
         */
        public Builder setDisableAnsi(final boolean disableAnsi) {
            this.disableAnsi = disableAnsi;
            return this;
        }

        /**
         * @param noConsoleNoAnsi
         *        If {@code "true"} (default is false) and {@link System#console()} is null, do not output ANSI escape codes
         */
        public Builder setNoConsoleNoAnsi(final boolean noConsoleNoAnsi) {
            this.noConsoleNoAnsi = noConsoleNoAnsi;
            return this;
        }

        /**
         * @param header
         *        The footer to place at the top of the document, once.
         */
        public Builder setHeader(final String header) {
            this.header = header;
            return this;
        }

        /**
         * @param footer
         *        The footer to place at the bottom of the document, once.
         */
        public Builder setFooter(final String footer) {
            this.footer = footer;
            return this;
        }

        @Override
        public PatternLayout build() {
            final Configuration config = getConfiguration();
            if (recyclerFactory == null) {
                recyclerFactory = config.getInstance(RecyclerFactory.class);
            }
            return new PatternLayout(config, recyclerFactory, regexReplacement, pattern, patternSelector,
                    charset, alwaysWriteExceptions, isDisableAnsi(), noConsoleNoAnsi, header, footer);
        }
    }

    public Serializer getEventSerializer() {
        return eventSerializer;
    }
}
