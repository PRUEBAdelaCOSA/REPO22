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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.message.MapMessage;

/**
 * A map-based lookup.
 */
@Plugin(name = "map", category = StrLookup.CATEGORY)
public class MapLookup implements StrLookup {

    /**
     * Map keys are variable names and value.
     */
    private final Map<String, String> map;

    /**
     * Constructor when used directly as a plugin.
     */
    public MapLookup() {
        this.map = null;
    }

    /**
     * Creates a new instance backed by a Map. Used by the default lookup.
     *
     * @param map
     *        the map of keys to values, may be null
     */
    public MapLookup(final Map<String, String> map) {
        this.map = map;
    }

    static Map<String, String> initMap(final String[] srcArgs, final Map<String, String> destMap) {
        for (int i = 0; i < srcArgs.length; i++) {
            final int next = i + 1;
            final String value = srcArgs[i];
            destMap.put(Integer.toString(i), value);
            destMap.put(value, next < srcArgs.length ? srcArgs[next] : null);
        }
        return destMap;
    }

    static HashMap<String, String> newMap(final int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }

    static Map<String, String> toMap(final List<String> args) {
        if (args == null) {
            return null;
        }
        final int size = args.size();
        return initMap(args.toArray(new String[size]), newMap(size));
    }

    static Map<String, String> toMap(final String[] args) {
        if (args == null) {
            return null;
        }
        return initMap(args, newMap(args.length));
    }

    protected Map<String, String> getMap() {
        return map;
    }

    @Override
    public String lookup(final LogEvent event, final String key) {
        final boolean isMapMessage = event != null && event.getMessage() instanceof MapMessage;
        if (map == null && !isMapMessage) {
            return null;
        }
        if (map != null && map.containsKey(key)) {
            final String obj = map.get(key);
            if (obj != null) {
                return obj;
            }
        }
        if (isMapMessage) {
            return ((MapMessage) event.getMessage()).get(key);
        }
        return null;
    }

    /**
     * Looks up a String key to a String value using the map.
     * <p>
     * If the map is null, then null is returned. The map result object is converted to a string using toString().
     * </p>
     *
     * @param key
     *        the key to be looked up, may be null
     * @return the matching value, null if no match
     */
    @Override
    public String lookup(final String key) {
        if (map == null) {
            return null;
        }
        return map.get(key);
    }

}
