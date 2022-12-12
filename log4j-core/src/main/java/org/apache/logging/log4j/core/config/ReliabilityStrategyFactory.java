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
package org.apache.logging.log4j.core.config;

import org.apache.logging.log4j.core.impl.Log4jProperties;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.plugins.Singleton;
import org.apache.logging.log4j.spi.ClassFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertyResolver;

/**
 * Factory for ReliabilityStrategies.
 */
@Singleton
public final class ReliabilityStrategyFactory {
    private final PropertyResolver propertyResolver;
    private final ClassFactory classFactory;

    @Inject
    public ReliabilityStrategyFactory(final PropertyResolver propertyResolver, final ClassFactory classFactory) {
        this.propertyResolver = propertyResolver;
        this.classFactory = classFactory;
    }

    /**
     * Returns a new {@code ReliabilityStrategy} instance based on the value of system property
     * {@value Log4jProperties#CONFIG_RELIABILITY_STRATEGY}. If not value was specified this method returns a new
     * {@code AwaitUnconditionallyReliabilityStrategy}.
     * <p>
     * Valid values for this system property are {@code "AwaitUnconditionally"} (use
     * {@code AwaitUnconditionallyReliabilityStrategy}), {@code "Locking"} (use {@code LockingReliabilityStrategy}) and
     * {@code "AwaitCompletion"} (use the default {@code AwaitCompletionReliabilityStrategy}).
     * <p>
     * Users may also use this system property to specify the fully qualified class name of a class that implements the
     * {@code ReliabilityStrategy} and has a constructor that accepts a single {@code LoggerConfig} argument.
     *
     * @param loggerConfig the LoggerConfig the resulting {@code ReliabilityStrategy} is associated with
     * @return a ReliabilityStrategy that helps the specified LoggerConfig to log events reliably during or after a
     *         configuration change
     */
    public ReliabilityStrategy getReliabilityStrategy(final LoggerConfig loggerConfig) {
        final String strategy = propertyResolver.getString(Log4jProperties.CONFIG_RELIABILITY_STRATEGY)
                .orElse("AwaitCompletion");
        if ("AwaitCompletion".equals(strategy)) {
            return new AwaitCompletionReliabilityStrategy(loggerConfig);
        }
        if ("AwaitUnconditionally".equals(strategy)) {
            return new AwaitUnconditionallyReliabilityStrategy(loggerConfig);
        }
        if ("Locking".equals(strategy)) {
            return new LockingReliabilityStrategy(loggerConfig);
        }
        return classFactory.tryGetClass(strategy, ReliabilityStrategy.class)
                .<ReliabilityStrategy>map(clazz -> {
                    try {
                        return clazz.getConstructor(LoggerConfig.class).newInstance(loggerConfig);
                    } catch (final Exception dynamicFailed) {
                        StatusLogger.getLogger().warn(
                                "Could not create ReliabilityStrategy for '{}', using default AwaitCompletionReliabilityStrategy: {}",
                                strategy, dynamicFailed);
                        return null;
                    }
                })
                .orElseGet(() -> new AwaitCompletionReliabilityStrategy(loggerConfig));
    }
}
