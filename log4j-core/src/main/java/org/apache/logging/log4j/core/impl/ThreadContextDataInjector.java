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
package org.apache.logging.log4j.core.impl;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.util.ContextDataProvider;
import org.apache.logging.log4j.spi.CopyOnWrite;
import org.apache.logging.log4j.spi.DefaultThreadContextMap;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.ServiceRegistry;
import org.apache.logging.log4j.util.StringMap;

/**
 * {@code ThreadContextDataInjector} contains a number of strategies for copying key-value pairs from the various
 * {@code ThreadContext} map implementations into a {@code StringMap}. In the case of duplicate keys,
 * thread context values overwrite configuration {@code Property} values.
 * <p>
 * These are the default {@code ContextDataInjector} objects returned by the {@link DefaultBundle}.
 * </p>
 *
 * @see org.apache.logging.log4j.ThreadContext
 * @see Property
 * @see ReadOnlyStringMap
 * @see ContextDataInjector
 * @since 2.7
 */
public class ThreadContextDataInjector {

    /**
     * ContextDataProviders loaded via OSGi.
     */
    public static Collection<ContextDataProvider> contextDataProviders =
            new ConcurrentLinkedDeque<>();

    private static final List<ContextDataProvider> SERVICE_PROVIDERS = getServiceProviders();

    private static List<ContextDataProvider> getServiceProviders() {
        final List<ContextDataProvider> providers = new ArrayList<>();
        final List<ContextDataProvider> services = ServiceRegistry.getInstance()
                .getServices(ContextDataProvider.class, MethodHandles.lookup(), null);
        for (final ContextDataProvider provider : services) {
            if (providers.stream().noneMatch((p) -> p.getClass().isAssignableFrom(provider.getClass()))) {
                providers.add(provider);
            }
        }
        return Collections.unmodifiableList(providers);
    }

    public static ContextDataInjector create(final ContextDataFactory factory) {
        final ReadOnlyThreadContextMap threadContextMap = ThreadContext.getThreadContextMap();

        // note: map may be null (if legacy custom ThreadContextMap was installed by user)
        if (threadContextMap instanceof DefaultThreadContextMap || threadContextMap == null) {
            // for non StringMap-based context maps
            return new ThreadContextDataInjector.ForDefaultThreadContextMap(factory);
        }
        if (threadContextMap instanceof CopyOnWrite) {
            return new ThreadContextDataInjector.ForCopyOnWriteThreadContextMap(factory);
        }
        return new ThreadContextDataInjector.ForGarbageFreeThreadContextMap();
    }


    /**
     * Default {@code ContextDataInjector} for the legacy {@code Map<String, String>}-based ThreadContext (which is
     * also the ThreadContext implementation used for web applications).
     * <p>
     * This injector always puts key-value pairs into the specified reusable StringMap.
     */
    public static class ForDefaultThreadContextMap implements ContextDataInjector {

        private final List<ContextDataProvider> providers;
        private final ContextDataFactory factory;

        public ForDefaultThreadContextMap() {
            this(new DefaultContextDataFactory());
        }

        public ForDefaultThreadContextMap(final ContextDataFactory factory) {
            this.factory = factory;
            providers = getProviders();
        }

        /**
         * Puts key-value pairs from both the specified list of properties as well as the thread context into the
         * specified reusable StringMap.
         *
         * @param props list of configuration properties, may be {@code null}
         * @param contextData a {@code StringMap} instance from the log event
         * @return a {@code StringMap} combining configuration properties with thread context data
         */
        @Override
        public StringMap injectContextData(final List<Property> props, final StringMap contextData) {

            final Map<String, String> copy;

            if (providers.size() == 1) {
                copy = providers.get(0).supplyContextData();
            } else {
                copy = new HashMap<>();
                for (final ContextDataProvider provider : providers) {
                    copy.putAll(provider.supplyContextData());
                }
            }

            // The DefaultThreadContextMap stores context data in a Map<String, String>.
            // This is a copy-on-write data structure so we are sure ThreadContext changes will not affect our copy.
            // If there are no configuration properties or providers returning a thin wrapper around the copy
            // is faster than copying the elements into the LogEvent's reusable StringMap.
            if ((props == null || props.isEmpty())) {
                // this will replace the LogEvent's context data with the returned instance.
                // NOTE: must mark as frozen or downstream components may attempt to modify (UnsupportedOperationEx)
                return copy.isEmpty() ? factory.emptyFrozenContextData() : frozenStringMap(copy);
            }
            // If the list of Properties is non-empty we need to combine the properties and the ThreadContext
            // data. Note that we cannot reuse the specified StringMap: some Loggers may have properties defined
            // and others not, so the LogEvent's context data may have been replaced with an immutable copy from
            // the ThreadContext - this will throw an UnsupportedOperationException if we try to modify it.
            final StringMap result = new JdkMapAdapterStringMap(new HashMap<>(copy));
            props.forEach(prop -> {
                if (!copy.containsKey(prop.getName())) {
                    result.putValue(prop.getName(), prop.getValue());
                }
            });
            result.freeze();
            return result;
        }

        private JdkMapAdapterStringMap frozenStringMap(final Map<String, String> copy) {
            final JdkMapAdapterStringMap result = new JdkMapAdapterStringMap(copy);
            result.freeze();
            return result;
        }

        @Override
        public ReadOnlyStringMap rawContextData() {
            final ReadOnlyThreadContextMap map = ThreadContext.getThreadContextMap();
            if (map instanceof ReadOnlyStringMap) {
                return (ReadOnlyStringMap) map;
            }
            // note: default ThreadContextMap is null
            final Map<String, String> copy = ThreadContext.getImmutableContext();
            return copy.isEmpty() ? factory.emptyFrozenContextData() : new JdkMapAdapterStringMap(copy);
        }
    }

    /**
     * The {@code ContextDataInjector} used when the ThreadContextMap implementation is a garbage-free
     * StringMap-based data structure.
     * <p>
     * This injector always puts key-value pairs into the specified reusable StringMap.
     */
    public static class ForGarbageFreeThreadContextMap implements ContextDataInjector {
        private final List<ContextDataProvider> providers;

        public ForGarbageFreeThreadContextMap() {
            this.providers = getProviders();
        }

        /**
         * Puts key-value pairs from both the specified list of properties as well as the thread context into the
         * specified reusable StringMap.
         *
         * @param props list of configuration properties, may be {@code null}
         * @param reusable a {@code StringMap} instance that may be reused to avoid creating temporary objects
         * @return a {@code StringMap} combining configuration properties with thread context data
         */
        @Override
        public StringMap injectContextData(final List<Property> props, final StringMap reusable) {
            // When the ThreadContext is garbage-free, we must copy its key-value pairs into the specified reusable
            // StringMap. We cannot return the ThreadContext's internal data structure because it may be modified later
            // and such modifications should not be reflected in the log event.
            copyProperties(props, reusable);
            providers.forEach(provider -> reusable.putAll(provider.supplyStringMap()));
            return reusable;
        }

        @Override
        public ReadOnlyStringMap rawContextData() {
            return ThreadContext.getThreadContextMap().getReadOnlyContextData();
        }
    }

    /**
     * The {@code ContextDataInjector} used when the ThreadContextMap implementation is a copy-on-write
     * StringMap-based data structure.
     * <p>
     * If there are no configuration properties, this injector will return the thread context's internal data
     * structure. Otherwise, the configuration properties are combined with the thread context key-value pairs into the
     * specified reusable StringMap.
     */
    public static class ForCopyOnWriteThreadContextMap implements ContextDataInjector {
        private final List<ContextDataProvider> providers;
        private final ContextDataFactory factory;

        public ForCopyOnWriteThreadContextMap() {
            this(new DefaultContextDataFactory());
        }

        public ForCopyOnWriteThreadContextMap(final ContextDataFactory factory) {
            this.factory = factory;
            this.providers = getProviders();
        }
        /**
         * If there are no configuration properties, this injector will return the thread context's internal data
         * structure. Otherwise, the configuration properties are combined with the thread context key-value pairs into the
         * specified reusable StringMap.
         *
         * @param props list of configuration properties, may be {@code null}
         * @param ignore a {@code StringMap} instance from the log event
         * @return a {@code StringMap} combining configuration properties with thread context data
         */
        @Override
        public StringMap injectContextData(final List<Property> props, final StringMap ignore) {
            // If there are no configuration properties we want to just return the ThreadContext's StringMap:
            // it is a copy-on-write data structure so we are sure ThreadContext changes will not affect our copy.
            if (providers.size() == 1 && (props == null || props.isEmpty())) {
                // this will replace the LogEvent's context data with the returned instance
                return providers.get(0).supplyStringMap();
            }
            int count = props == null ? 0 : props.size();
            final StringMap[] maps = new StringMap[providers.size()];
            for (int i = 0; i < providers.size(); ++i) {
                maps[i] = providers.get(i).supplyStringMap();
                count += maps[i].size();
            }
            // However, if the list of Properties is non-empty we need to combine the properties and the ThreadContext
            // data. Note that we cannot reuse the specified StringMap: some Loggers may have properties defined
            // and others not, so the LogEvent's context data may have been replaced with an immutable copy from
            // the ThreadContext - this will throw an UnsupportedOperationException if we try to modify it.
            final StringMap result = factory.createContextData(count);
            copyProperties(props, result);
            for (final StringMap map : maps) {
                result.putAll(map);
            }
            return result;
        }

        @Override
        public ReadOnlyStringMap rawContextData() {
            return ThreadContext.getThreadContextMap().getReadOnlyContextData();
        }
    }

    /**
     * Copies key-value pairs from the specified property list into the specified {@code StringMap}.
     *
     * @param properties list of configuration properties, may be {@code null}
     * @param result the {@code StringMap} object to add the key-values to. Must be non-{@code null}.
     */
    public static void copyProperties(final List<Property> properties, final StringMap result) {
        if (properties != null) {
            properties.forEach(property -> result.putValue(property.getName(), property.getValue()));
        }
    }

    private static List<ContextDataProvider> getProviders() {
        final List<ContextDataProvider> providers =
                new ArrayList<>(contextDataProviders.size() + SERVICE_PROVIDERS.size());
        providers.addAll(contextDataProviders);
        providers.addAll(SERVICE_PROVIDERS);
        return providers;
    }
}
