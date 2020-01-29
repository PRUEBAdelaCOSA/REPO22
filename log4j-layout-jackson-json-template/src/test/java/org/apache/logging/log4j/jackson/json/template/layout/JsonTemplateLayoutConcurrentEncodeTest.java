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
package org.apache.logging.log4j.jackson.json.template.layout;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;
import org.apache.logging.log4j.message.SimpleMessage;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JsonTemplateLayoutConcurrentEncodeTest {

    private static class ConcurrentAccessError extends RuntimeException {

        public static final long serialVersionUID = 0;

        private ConcurrentAccessError(final int concurrentAccessCount) {
            super("concurrentAccessCount=" + concurrentAccessCount);
        }

    }

    private static class ConcurrentAccessDetectingByteBufferDestination extends BlackHoleByteBufferDestination {

        private final AtomicInteger concurrentAccessCounter = new AtomicInteger(0);

        ConcurrentAccessDetectingByteBufferDestination(final int maxByteCount) {
            super(maxByteCount);
        }

        @Override
        public ByteBuffer getByteBuffer() {
            final int concurrentAccessCount = concurrentAccessCounter.incrementAndGet();
            if (concurrentAccessCount > 1) {
                throw new ConcurrentAccessError(concurrentAccessCount);
            }
            try {
                return super.getByteBuffer();
            } finally {
                concurrentAccessCounter.decrementAndGet();
            }
        }

        @Override
        public ByteBuffer drain(final ByteBuffer byteBuffer) {
            final int concurrentAccessCount = concurrentAccessCounter.incrementAndGet();
            if (concurrentAccessCount > 1) {
                throw new ConcurrentAccessError(concurrentAccessCount);
            }
            try {
                return super.drain(byteBuffer);
            } finally {
                concurrentAccessCounter.decrementAndGet();
            }
        }

        @Override
        public void writeBytes(final ByteBuffer byteBuffer) {
            final int concurrentAccessCount = concurrentAccessCounter.incrementAndGet();
            if (concurrentAccessCount > 1) {
                throw new ConcurrentAccessError(concurrentAccessCount);
            }
            try {
                super.writeBytes(byteBuffer);
            } finally {
                concurrentAccessCounter.decrementAndGet();
            }
        }

        @Override
        public void writeBytes(final byte[] buffer, final int offset, final int length) {
            int concurrentAccessCount = concurrentAccessCounter.incrementAndGet();
            if (concurrentAccessCount > 1) {
                throw new ConcurrentAccessError(concurrentAccessCount);
            }
            try {
                super.writeBytes(buffer, offset, length);
            } finally {
                concurrentAccessCounter.decrementAndGet();
            }
        }

    }

    private static final LogEvent[] LOG_EVENTS = createMessages();

    private static LogEvent[] createMessages() {
        final int messageCount = 1_000;
        final int minMessageLength = 1;
        final int maxMessageLength = 1_000;
        final Random random = new Random(0);
        return IntStream
                .range(0, messageCount)
                .mapToObj(ignored -> {
                    final int messageLength = minMessageLength + random.nextInt(maxMessageLength);
                    final int startIndex = random.nextInt(10);
                    final String messageText = IntStream
                            .range(0, messageLength)
                            .mapToObj(charIndex -> {
                                final int digit = (startIndex + charIndex) % 10;
                                return String.valueOf(digit);
                            })
                            .collect(Collectors.joining(""));
                    final SimpleMessage message = new SimpleMessage(messageText);
                    return Log4jLogEvent
                            .newBuilder()
                            .setMessage(message)
                            .build();
                })
                .toArray(LogEvent[]::new);
    }

    @Test
    public void test_concurrent_encode() {
        final int threadCount = 10;
        final int maxAppendCount = 1_000;
        final AtomicReference<Exception> encodeFailureRef = new AtomicReference<>(null);
        produce(threadCount, maxAppendCount, encodeFailureRef);
        Assertions.assertThat(encodeFailureRef.get()).isNull();
    }

    private void produce(
            final int threadCount,
            final int maxEncodeCount,
            final AtomicReference<Exception> encodeFailureRef) {
        final int maxByteCount = JsonTemplateLayout.newBuilder().getMaxByteCount();
        final JsonTemplateLayout layout = createLayout(maxByteCount);
        final ByteBufferDestination destination =
                new ConcurrentAccessDetectingByteBufferDestination(maxByteCount);
        final AtomicLong encodeCounter = new AtomicLong(0);
        final List<Thread> workers = IntStream
                .range(0, threadCount)
                .mapToObj((final int threadIndex) ->
                        createWorker(
                                layout,
                                destination,
                                encodeFailureRef,
                                maxEncodeCount,
                                encodeCounter,
                                threadIndex))
                .collect(Collectors.toList());
        workers.forEach(Thread::start);
        workers.forEach((final Thread worker) -> {
            try {
                worker.join();
            } catch (final InterruptedException ignored) {
                System.err.format("join to %s interrupted%n", worker.getName());
            }
        });
    }

    private JsonTemplateLayout createLayout(final int maxByteCount) {
        final Configuration config = new DefaultConfiguration();
        return JsonTemplateLayout
                .newBuilder()
                .setConfiguration(config)
                .setMaxByteCount(maxByteCount)
                .setEventTemplate("{\"message\": \"${json:message}\"}")
                .setStackTraceEnabled(false)
                .setLocationInfoEnabled(false)
                .setPrettyPrintEnabled(false)
                .build();
    }

    private Thread createWorker(
            final JsonTemplateLayout layout,
            final ByteBufferDestination destination,
            final AtomicReference<Exception> encodeFailureRef,
            final int maxEncodeCount,
            final AtomicLong encodeCounter,
            final int threadIndex) {
        final String threadName = String.format("Worker-%d", threadIndex);
        return new Thread(
                () -> {
                    try {
                        for (int logEventIndex = threadIndex % LOG_EVENTS.length;
                             encodeFailureRef.get() == null && encodeCounter.incrementAndGet() < maxEncodeCount;
                             logEventIndex = (logEventIndex + 1) % LOG_EVENTS.length) {
                            final LogEvent logEvent = LOG_EVENTS[logEventIndex];
                            layout.encode(logEvent, destination);
                        }
                    } catch (final Exception error) {
                        final boolean succeeded = encodeFailureRef.compareAndSet(null, error);
                        if (succeeded) {
                            System.err.format("%s failed%n", threadName);
                            error.printStackTrace(System.err);
                        }
                    }
                },
                threadName);
    }

}
