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

import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.impl.Log4jProperties;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.spi.ClassFactory;
import org.apache.logging.log4j.spi.InstanceFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertyResolver;

/**
 * Creates {@link AsyncQueueFullPolicy} instances based on user-specified system properties. The {@code AsyncQueueFullPolicy}
 * created by this factory is used in AsyncLogger, AsyncLoggerConfig and AsyncAppender
 * to control if events are logged in the current thread, the background thread, or discarded.
 * <p>
 * Property {@value Log4jProperties#ASYNC_LOGGER_QUEUE_FULL_POLICY} controls the routing behaviour. If this property is not specified or has
 * value {@code "Default"}, this factory creates {@link DefaultAsyncQueueFullPolicy} objects.
 * </p> <p>
 * If this property has value {@code "Discard"}, this factory creates {@link DiscardingAsyncQueueFullPolicy} objects.
 * By default, this router discards events of level {@code INFO}, {@code DEBUG} and {@code TRACE} if the queue is full.
 * This can be adjusted with property {@value Log4jProperties#ASYNC_LOGGER_DISCARD_THRESHOLD} (name of the level at which to start
 * discarding).
 * </p> <p>
 * For any other value, this
 * factory interprets the value as the fully qualified name of a class implementing the {@link AsyncQueueFullPolicy}
 * interface. The class must have a default constructor.
 * </p>
 *
 * @since 2.6
 */
public class AsyncQueueFullPolicyFactory implements Supplier<AsyncQueueFullPolicy> {
    static final String PROPERTY_VALUE_DEFAULT_ASYNC_EVENT_ROUTER = "Default";
    static final String PROPERTY_VALUE_DISCARDING_ASYNC_EVENT_ROUTER = "Discard";

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final PropertyResolver propertyResolver;
    private final ClassFactory classFactory;
    private final InstanceFactory instanceFactory;

    @Inject
    public AsyncQueueFullPolicyFactory(
            final PropertyResolver propertyResolver,
            final ClassFactory classFactory,
            final InstanceFactory instanceFactory) {
        this.propertyResolver = propertyResolver;
        this.classFactory = classFactory;
        this.instanceFactory = instanceFactory;
    }

    /**
     * Creates and returns {@link AsyncQueueFullPolicy} instances based on user-specified system properties.
     * <p>
     * Property {@value Log4jProperties#ASYNC_LOGGER_QUEUE_FULL_POLICY} controls the routing behaviour. If this property is not specified or
     * has value {@code "Default"}, this method returns {@link DefaultAsyncQueueFullPolicy} objects.
     * </p> <p>
     * If this property has value {@code "Discard"}, this method returns {@link DiscardingAsyncQueueFullPolicy} objects.
     * </p> <p>
     * For any other value, this method interprets the value as the fully qualified name of a class implementing the
     * {@link AsyncQueueFullPolicy} interface. The class must have a default constructor.
     * </p>
     *
     * @return a new AsyncQueueFullPolicy
     */
    @Override
    public AsyncQueueFullPolicy get() {
        final String router = propertyResolver.getString(Log4jProperties.ASYNC_LOGGER_QUEUE_FULL_POLICY).orElse(null);
        if (router == null || isRouterSelected(
                router, DefaultAsyncQueueFullPolicy.class, PROPERTY_VALUE_DEFAULT_ASYNC_EVENT_ROUTER)) {
            return new DefaultAsyncQueueFullPolicy();
        }
        if (isRouterSelected(
                router, DiscardingAsyncQueueFullPolicy.class, PROPERTY_VALUE_DISCARDING_ASYNC_EVENT_ROUTER)) {
            final Level thresholdLevel = propertyResolver.getString(Log4jProperties.ASYNC_LOGGER_DISCARD_THRESHOLD)
                    .map(Level::getLevel)
                    .orElse(Level.INFO);
            LOGGER.debug("Creating custom DiscardingAsyncQueueFullPolicy(discardThreshold:{})", thresholdLevel);
            return new DiscardingAsyncQueueFullPolicy(thresholdLevel);
        }
        return classFactory.tryGetClass(router, AsyncQueueFullPolicy.class)
                .<AsyncQueueFullPolicy>flatMap(instanceFactory::tryGetInstance)
                .orElseGet(DefaultAsyncQueueFullPolicy::new);
    }

    private static boolean isRouterSelected(
            final String propertyValue,
            final Class<? extends AsyncQueueFullPolicy> policy,
            final String shortPropertyValue) {
        return shortPropertyValue.equalsIgnoreCase(propertyValue)
                || policy.getName().equals(propertyValue)
                || policy.getSimpleName().equals(propertyValue);
    }
}
