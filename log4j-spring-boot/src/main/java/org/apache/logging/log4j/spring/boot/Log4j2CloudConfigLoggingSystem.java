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
package org.apache.logging.log4j.spring.boot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;
import org.apache.logging.log4j.core.net.ssl.LaxHostnameVerifier;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;
import org.apache.logging.log4j.core.util.AuthorizationProvider;
import org.apache.logging.log4j.core.util.BasicAuthorizationProvider;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.log4j2.Log4J2LoggingSystem;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;

/**
 * Override Spring's implementation of the Log4j 2 Logging System to properly support Spring Cloud Config.
 */
public class Log4j2CloudConfigLoggingSystem extends Log4J2LoggingSystem {
    private static final String HTTPS = "https";
    public static final String ENVIRONMENT_KEY = "SpringEnvironment";
    private static final String OVERRIDE_PARAM = "override";
    private static Logger LOGGER = StatusLogger.getLogger();

    public Log4j2CloudConfigLoggingSystem(final ClassLoader loader) {
        super(loader);
    }

    /**
     * Set the environment into the ExternalContext field so that it can be obtained by SpringLookup when it
     * is constructed. Spring will replace the ExternalContext field with a String once initialization is
     * complete.
     * @param initializationContext The initialization context.
     * @param configLocation The configuration location.
     * @param logFile the log file.
     */
    @Override
    public void initialize(final LoggingInitializationContext initializationContext, final String configLocation, final LogFile logFile) {
        getLoggerContext().putObjectIfAbsent(ENVIRONMENT_KEY, initializationContext.getEnvironment());
        super.initialize(initializationContext, configLocation, logFile);
    }

    @Override
    protected String[] getStandardConfigLocations() {
        String[] locations = super.getStandardConfigLocations();
        final PropertiesUtil props = new PropertiesUtil(new Properties());
        final String location = props.getStringProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY);
        if (location != null) {
            final List<String> list = new ArrayList<>(Arrays.asList(super.getStandardConfigLocations()));
            list.add(location);
            locations = list.toArray(new String[0]);
        }
        return locations;
    }

    @Override
    protected void loadDefaults(final LoggingInitializationContext initializationContext, final LogFile logFile) {
        if (logFile != null) {
            this.loadConfiguration(this.getBootPackagedConfigFile("log4j2-file.xml"), logFile);
        } else {
            this.loadConfiguration(this.getBootPackagedConfigFile("log4j2.xml"), logFile);
        }
    }

    private String getBootPackagedConfigFile(final String fileName) {
        String defaultPath = ClassUtils.getPackageName(Log4J2LoggingSystem.class);
        defaultPath = defaultPath.replace('.', '/');
        defaultPath = defaultPath + "/" + fileName;
        defaultPath = "classpath:" + defaultPath;
        return defaultPath;
    }

    @Override
    protected void loadConfiguration(final String location, final LogFile logFile) {
        Assert.notNull(location, "Location must not be null");
        try {
            final LoggerContext ctx = getLoggerContext();
            final String[] locations = parseConfigLocations(location);
            if (locations.length == 1) {
                final URL url = ResourceUtils.getURL(location);
                final ConfigurationSource source = getConfigurationSource(url);
                if (source != null) {
                    ctx.start(ConfigurationFactory.getInstance().getConfiguration(ctx, source));
                }
            } else {
                final List<AbstractConfiguration> configs = new ArrayList<>();
                for (final String sourceLocation : locations) {
                    final ConfigurationSource source = getConfigurationSource(ResourceUtils.getURL(sourceLocation));
                    if (source != null) {
                        final Configuration config = ConfigurationFactory.getInstance().getConfiguration(ctx, source);
                        if (config instanceof AbstractConfiguration) {
                            configs.add((AbstractConfiguration) config);
                        } else {
                            LOGGER.warn("Configuration at {} cannot be combined in a CompositeConfiguration", sourceLocation);
                            return;
                        }
                    }
                }
                if (configs.size() > 1) {
                    ctx.start(new CompositeConfiguration(configs));
                } else {
                    ctx.start(configs.get(0));
                }
            }
        }
        catch (final Exception ex) {
            throw new IllegalStateException(
                "Could not initialize Log4J2 logging from " + location, ex);
        }
    }

    @Override
    public void cleanUp() {
        getLoggerContext().removeObject(ENVIRONMENT_KEY);
        super.cleanUp();
    }

    private String[] parseConfigLocations(final String configLocations) {
        final String[] uris = configLocations.split("\\?");
        final List<String> locations = new ArrayList<>();
        if (uris.length > 1) {
            locations.add(uris[0]);
            try {
                final URL url = new URL(configLocations);
                final String[] pairs = url.getQuery().split("&");
                for (final String pair : pairs) {
                    final int idx = pair.indexOf("=");
                    try {
                        final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                        if (key.equalsIgnoreCase(OVERRIDE_PARAM)) {
                            locations.add(URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                        }
                    } catch (final UnsupportedEncodingException ex) {
                        LOGGER.warn("Bad data in configuration string: {}", pair);
                    }
                }
                return locations.toArray(new String[0]);
            } catch (final MalformedURLException ex) {
                LOGGER.warn("Unable to parse configuration URL {}", configLocations);
            }
        }
        return new String[] {uris[0]};
    }

    private ConfigurationSource getConfigurationSource(final URL url) throws IOException, URISyntaxException {
        final URLConnection urlConnection = url.openConnection();
        final AuthorizationProvider provider = ConfigurationFactory.authorizationProvider(PropertiesUtil.getProperties());
        provider.addAuthorization(urlConnection);
        if (url.getProtocol().equals(HTTPS)) {
            final SslConfiguration sslConfiguration = SslConfigurationFactory.getSslConfiguration();
            if (sslConfiguration != null) {
                ((HttpsURLConnection) urlConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
                if (!sslConfiguration.isVerifyHostName()) {
                    ((HttpsURLConnection) urlConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
                }
            }
        }
        final File file = FileUtils.fileFromUri(url.toURI());
        try {
            if (file != null) {
                return new ConfigurationSource(urlConnection.getInputStream(), FileUtils.fileFromUri(url.toURI()));
            } else {
                return new ConfigurationSource(urlConnection.getInputStream(), url, urlConnection.getLastModified());
            }
        } catch (final FileNotFoundException ex) {
            LOGGER.info("Unable to locate file {}, ignoring.", url.toString());
            return null;
        }
    }

    private LoggerContext getLoggerContext() {
        return (LoggerContext) LogManager.getContext(false);
    }
}
