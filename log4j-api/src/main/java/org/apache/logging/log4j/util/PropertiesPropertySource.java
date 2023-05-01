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
package org.apache.logging.log4j.util;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * PropertySource backed by a {@link Properties} instance. Normalized property names follow a scheme like this:
 * {@code Log4jContextSelector} would normalize to {@code log4j2.contextSelector}.
 *
 * @since 2.10.0
 */
public class PropertiesPropertySource extends ContextAwarePropertySource implements PropertySource {

    private final int priority;

    public PropertiesPropertySource(final Properties properties) {
        this(properties, SYSTEM_CONTEXT, DEFAULT_PRIORITY, false);
    }

    public PropertiesPropertySource(final Properties properties, final int priority) {
        this(properties, SYSTEM_CONTEXT, priority, false);
    }

    public PropertiesPropertySource(final Properties properties, final String contextName, final int priority) {
        this(properties, contextName, priority, false);
    }

    public PropertiesPropertySource(final Properties properties, final String contextName, final int priority,
                                    final boolean includeInvalid) {
        super(properties, contextName, includeInvalid);
        this.priority = priority;
    }


    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void forEach(final BiConsumer<String, String> action) {
        Properties properties = propertiesMap.get(SYSTEM_CONTEXT);
        if (properties != null) {
            for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
                action.accept(((String) entry.getKey()), ((String) entry.getValue()));
            }
        }
    }

    @Override
    public CharSequence getNormalForm(final Iterable<? extends CharSequence> tokens) {
        final CharSequence result = Util.join(tokens);
        return result.length() > 0 ? PREFIX + result : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertiesPropertySource that = (PropertiesPropertySource) o;
        return priority == that.priority && propertiesMap.equals(that.propertiesMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertiesMap, priority);
    }
}
