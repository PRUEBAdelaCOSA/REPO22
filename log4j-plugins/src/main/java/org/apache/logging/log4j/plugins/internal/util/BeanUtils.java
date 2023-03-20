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
package org.apache.logging.log4j.plugins.internal.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.plugins.FactoryType;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.plugins.QualifierType;
import org.apache.logging.log4j.plugins.di.AmbiguousInjectConstructorException;
import org.apache.logging.log4j.plugins.di.DependencyChain;
import org.apache.logging.log4j.plugins.di.Key;
import org.apache.logging.log4j.plugins.di.NotInjectableException;
import org.apache.logging.log4j.plugins.util.AnnotationUtil;
import org.apache.logging.log4j.util.Cast;
import org.apache.logging.log4j.util.InternalApi;

/**
 * Utility methods.
 */
@InternalApi
public final class BeanUtils {
    private BeanUtils() {
    }

    public static String decapitalize(String string) {
        if (string.isEmpty()) {
            return string;
        }
        char[] chars = string.toCharArray();
        if (chars.length >= 2 && Character.isUpperCase(chars[0]) && Character.isUpperCase(chars[1])) {
            return string;
        }
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    public static Executable getInjectableFactory(final Class<?> clazz) {
        return Stream.of(clazz.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()) &&
                        AnnotationUtil.isMetaAnnotationPresent(method, FactoryType.class))
                .min(Comparator.comparingInt(Method::getParameterCount).thenComparing(Method::getReturnType, (c1, c2) -> {
                    if (c1.equals(c2)) {
                        return 0;
                    } else if (Supplier.class.isAssignableFrom(c1)) {
                        return -1;
                    } else if (Supplier.class.isAssignableFrom(c2)) {
                        return 1;
                    } else {
                        return c1.getName().compareTo(c2.getName());
                    }
                }))
                .map(Executable.class::cast)
                .orElseGet(() -> getInjectableConstructor(clazz));
    }

    public static <T> Constructor<T> getInjectableConstructor(final Class<T> clazz) {
        final Constructor<T> constructor = findInjectableConstructor(clazz);
        if (constructor == null) {
            throw new NotInjectableException(clazz);
        }
        return constructor;
    }

    public static <T> Constructor<T> getInjectableConstructor(final Key<T> key, final DependencyChain chain) {
        final Constructor<T> constructor = findInjectableConstructor(key.getRawType());
        if (constructor == null) {
            throw new NotInjectableException(key, chain);
        }
        return constructor;
    }

    public static boolean isInjectable(final Field field) {
        return field.isAnnotationPresent(Inject.class) || AnnotationUtil.isMetaAnnotationPresent(field, QualifierType.class);
    }

    public static boolean isInjectable(final Method method) {
        if (method.isAnnotationPresent(Inject.class)) {
            return true;
        }
        if (!AnnotationUtil.isMetaAnnotationPresent(method, FactoryType.class)) {
            for (final Parameter parameter : method.getParameters()) {
                if (AnnotationUtil.isMetaAnnotationPresent(parameter, QualifierType.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> Constructor<T> findInjectableConstructor(final Class<T> clazz) {
        final List<Constructor<?>> constructors = Stream.of(clazz.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
                .collect(Collectors.toList());
        final int size = constructors.size();
        if (size > 1) {
            throw new AmbiguousInjectConstructorException(clazz);
        }
        if (size == 1) {
            return Cast.cast(constructors.get(0));
        }
        try {
            return clazz.getDeclaredConstructor();
        } catch (final NoSuchMethodException ignored) {
        }
        try {
            return clazz.getConstructor();
        } catch (final NoSuchMethodException ignored) {
        }
        return null;
    }
}
