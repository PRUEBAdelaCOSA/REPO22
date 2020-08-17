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
package org.apache.logging.log4j.core.lookup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.ConfigurationAware;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.ReflectionUtil;
import org.apache.logging.log4j.plugins.util.PluginManager;
import org.apache.logging.log4j.plugins.util.PluginType;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Proxies other {@link StrLookup}s using a keys within ${} markers.
 */
public class Interpolator extends AbstractConfigurationAwareLookup {

    /** Constant for the prefix separator. */
    public static final char PREFIX_SEPARATOR = ':';

    private static final String LOOKUP_KEY_WEB = "web";

    private static final String LOOKUP_KEY_DOCKER = "docker";

    private static final String LOOKUP_KEY_KUBERNETES = "kubernetes";

    private static final String LOOKUP_KEY_SPRING = "spring";

    private static final String LOOKUP_KEY_JNDI = "jndi";

    private static final String LOOKUP_KEY_JVMRUNARGS = "jvmrunargs";

    private static final String PURE_LOOKUP_KEY = "";

    private static final Logger LOGGER = StatusLogger.getLogger();

    /**
     * Lookups in this map are used to resolve variables prefixed with specified strings
     * like "sys", "ctx", "web" etc..
     */
    private final Map<String, StrLookup> sourceSpecifiedLookupMap = new HashMap<>();

    /**
     * <p>Lookups in this list are used to resolve variables without prefix. </p>
     * <p><b>NOTE: </b>There might be multiple pure lookups, hence it is a sorted lookup list.
     * The lookup with a larger order would be called later.
     * </p>
     *
     */
    private final Set<StrLookup> pureLookupSet = new TreeSet<StrLookup>(
            Comparator.comparing(o -> o.getClass().isAnnotationPresent(Order.class) ? o.getClass().getDeclaredAnnotation(Order.class).value() : Integer.MAX_VALUE )
    );

    private final StrLookup defaultLookup;

    public Interpolator(final StrLookup defaultLookup) {
        this(defaultLookup, null);
    }

    /**
     * Constructs an Interpolator using a given StrLookup and a list of packages to find Lookup plugins in.
     *
     * @param defaultLookup  the default StrLookup to use as a fallback
     * @param pluginPackages a list of packages to scan for Lookup plugins
     * @since 2.1
     */
    public Interpolator(final StrLookup defaultLookup, final List<String> pluginPackages) {
        this.defaultLookup = defaultLookup == null ? new MapLookup(new HashMap<String, String>()) : defaultLookup;

        // load lookup plugins
        final PluginManager manager = new PluginManager(CATEGORY);
        manager.collectPlugins(pluginPackages);
        final Map<String, PluginType<?>> plugins = manager.getPlugins();

        for (final Map.Entry<String, PluginType<?>> entry : plugins.entrySet()) {
            try {
                final Class<? extends StrLookup> clazz = entry.getValue().getPluginClass().asSubclass(StrLookup.class);
                String lookupName = entry.getKey().toLowerCase();
                StrLookup lookupInstance = ReflectionUtil.instantiate(clazz);
                if(PURE_LOOKUP_KEY.equals(lookupName)){
                    pureLookupSet.add(lookupInstance);
                }
                else{
                    sourceSpecifiedLookupMap.put(lookupName, lookupInstance);
                }

            } catch (final Throwable t) {
                handleError(entry.getKey(), t);
            }
        }

    }

    /**
     * Create the default Interpolator using only Lookups that work without an event.
     */
    public Interpolator() {
        this((Map<String, String>) null);
    }


    /**
     * Creates the Interpolator using only Lookups that work without an event and initial properties.
     */
    public Interpolator(final Map<String, String> properties) {

        this.defaultLookup =  new MapLookup(properties == null ? new HashMap<String, String>() : properties);

        // load pre-loaded lookups via SPI
        Set<StrLookup> strLookupSetViaServiceLoader =  new TreeSet<StrLookup>(Comparator.comparing(o -> o.getClass().getName()));

        for (final ClassLoader classLoader : LoaderUtil.getClassLoaders()) {
            try {
                for (final StrLookup lookupSource : ServiceLoader.load(StrLookup.class, classLoader)) {
                    strLookupSetViaServiceLoader.add(lookupSource);
                }
            } catch (final Throwable ex) {
                LOGGER.warn("There is something wrong occurred in interpolator initialization. Details: {}", ex.getMessage());
            }
        }

        strLookupSetViaServiceLoader.stream().forEach(lookup->{
            Plugin plugin = lookup.getClass().getDeclaredAnnotation(Plugin.class);
            String pluginName = "";
            if(plugin != null){
                pluginName = plugin.name();
            }
            if(PURE_LOOKUP_KEY.equals(pluginName)){
                // pure lookup
                pureLookupSet.add(lookup);
            }
            else{
                // lookup for special use.
                sourceSpecifiedLookupMap.put(pluginName, lookup);
            }
        });
    }

    public Map<String, StrLookup> getSourceSpecifiedLookupMap() {
        return sourceSpecifiedLookupMap;
    }

    private void handleError(final String lookupKey, final Throwable t) {
        switch (lookupKey) {
            case LOOKUP_KEY_JNDI:
                // java.lang.VerifyError: org/apache/logging/log4j/core/lookup/JndiLookup
                LOGGER.warn( // LOG4J2-1582 don't print the whole stack trace (it is just a warning...)
                        "JNDI lookup class is not available because this JRE does not support JNDI." +
                        " JNDI string lookups will not be available, continuing configuration. Ignoring " + t);
                break;
            case LOOKUP_KEY_JVMRUNARGS:
                // java.lang.VerifyError: org/apache/logging/log4j/core/lookup/JmxRuntimeInputArgumentsLookup
                LOGGER.warn(
                        "JMX runtime input lookup class is not available because this JRE does not support JMX. " +
                        "JMX lookups will not be available, continuing configuration. Ignoring " + t);
                break;
            case LOOKUP_KEY_WEB:
                LOGGER.info("Log4j appears to be running in a Servlet environment, but there's no log4j-web module " +
                        "available. If you want better web container support, please add the log4j-web JAR to your " +
                        "web archive or server lib directory.");
                break;
            case LOOKUP_KEY_DOCKER: case LOOKUP_KEY_SPRING:
                break;
            case LOOKUP_KEY_KUBERNETES:
                if (t instanceof NoClassDefFoundError) {
                    LOGGER.warn("Unable to create Kubernetes lookup due to missing dependency: {}", t.getMessage());
                }
                break;
            default:
                LOGGER.error("Unable to create Lookup for {}", lookupKey, t);
        }
    }

    /**
     * Resolves the specified variable. This implementation will try to extract
     * a variable prefix from the given variable name (the first colon (':') is
     * used as prefix separator). It then passes the name of the variable with
     * the prefix stripped to the lookup object registered for this prefix. If
     * no prefix can be found or if the associated lookup object cannot resolve
     * this variable, the default lookup object will be used.
     *
     * @param event The current LogEvent or null.
     * @param var the name of the variable whose value is to be looked up
     * @return the value of this variable or <b>null</b> if it cannot be
     * resolved
     */
    @Override
    public String lookup(final LogEvent event, String var) {
        if (var == null) {
            return null;
        }

        final int prefixPos = var.indexOf(PREFIX_SEPARATOR);
        if (prefixPos >= 0) {
            final String prefix = var.substring(0, prefixPos).toLowerCase(Locale.US);
            final String name = var.substring(prefixPos + 1);
            final StrLookup lookup = sourceSpecifiedLookupMap.get(prefix);
            if (lookup instanceof ConfigurationAware) {
                ((ConfigurationAware) lookup).setConfiguration(configuration);
            }
            String value = null;
            if (lookup != null) {
                value = event == null ? lookup.lookup(name) : lookup.lookup(event, name);
            }

            if (value != null) {
                return value;
            }
            var = var.substring(prefixPos + 1);
        }
        else{
            for(StrLookup pureLookup : pureLookupSet){
                String value =  event == null ? pureLookup.lookup(var) : pureLookup.lookup(event, var);
                if (value != null) {
                    return value;
                }
            }
        }
        if (defaultLookup != null) {
            return event == null ? defaultLookup.lookup(var) : defaultLookup.lookup(event, var);
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final String name : sourceSpecifiedLookupMap.keySet()) {
            if (sb.length() == 0) {
                sb.append('{');
            } else {
                sb.append(", ");
            }

            sb.append(name);
        }
        if (sb.length() > 0) {
            sb.append('}');
        }
        return sb.toString();
    }

}
