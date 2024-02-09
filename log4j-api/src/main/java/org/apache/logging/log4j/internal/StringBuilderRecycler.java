/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.internal;

import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.InternalApi;

/**
 * An <b>internal</b> tool to recycle {@link StringBuilder} instances to implement garbage-free formatting.
 * <h1>Internal usage only!</h1>
 * <p>
 * This interface is only intended to be used for internals of the Log4j API.
 * Log4j API can break this contract at any time to adapt to its needs.
 * <b>{@link StringBuilderRecycler} should not be used by any other code except the Log4j API!</b>
 * </p>
 *
 * @since 2.23.0
 */
@InternalApi
public interface StringBuilderRecycler {

    int MAX_STRING_BUILDER_CAPACITY = Constants.MAX_REUSABLE_MESSAGE_SIZE;

    StringBuilder acquire();

    void release(final StringBuilder stringBuilder);

    /**
     * Creates a {@link StringBuilderRecycler} using the current environment, e.g., {@link Constants}, {@link org.apache.logging.log4j.util.PropertiesUtil}, etc.
     *
     * @param recursionDepth This value indicates the maximum recursion depth to be supported before falling back to allocating new instances.
     *                       For instance, {@link QueueingThreadLocalStringBuilderRecycler} uses this to set the capacity of the instances to be pooled in a {@link ThreadLocal}.
     * @return a new {@link StringBuilderRecycler} instance
     */
    static StringBuilderRecycler ofEnvironment(final int recursionDepth) {
        if (recursionDepth < 0) {
            throw new IllegalArgumentException("was expecting a positive `recursionDepth`, found: " + recursionDepth);
        }
        if (!Constants.ENABLE_THREADLOCALS) {
            return new DummyStringBuilderRecycler();
        } else if (recursionDepth == 0) {
            return new ThreadLocalStringBuilderRecycler();
        } else {
            return new QueueingThreadLocalStringBuilderRecycler(recursionDepth);
        }
    }
}
