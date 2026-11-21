package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBufferTest {

    @Test
    void parkThenPollReturnsMatchingMessageAndRemovesIt() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(11, "ORD1")));
        assertThat(buffer.size("s1")).isEqualTo(1);

        Optional<FIXMessageData> polled = buffer.poll("s1", f -> "ORD1".equals(f.flatFields().get(11)));
        assertThat(polled).isPresent();
        assertThat(polled.get().flatFields()).containsEntry(11, "ORD1");
        assertThat(buffer.size("s1")).isZero();
    }

    @Test
    void pollNonMatchingLeavesMessageParked() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(11, "ORD1")));
        assertThat(buffer.poll("s1", f -> "nope".equals(f.flatFields().get(11)))).isEmpty();
        assertThat(buffer.size("s1")).isEqualTo(1);
    }

    @Test
    void pollUnknownSessionIsEmpty() {
        assertThat(new MessageBuffer().poll("ghost", f -> true)).isEmpty();
    }

    @Test
    void pollReturnsNewestMatchFirst() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(11, "OLD")));
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(11, "NEW")));
        Optional<FIXMessageData> polled = buffer.poll("s1", f -> true);
        assertThat(polled).isPresent();
        assertThat(polled.get().flatFields()).containsEntry(11, "NEW"); // head = most recently parked
    }

    @Test
    void capacityEvictsOldestKeepingNewest() {
        MessageBuffer buffer = new MessageBuffer(2, Long.MAX_VALUE);
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(1, "A")));
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(1, "B")));
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(1, "C")));
        assertThat(buffer.size("s1")).isEqualTo(2);
        // oldest (A) evicted; B and C remain
        assertThat(buffer.poll("s1", f -> "A".equals(f.flatFields().get(1)))).isEmpty();
        assertThat(buffer.poll("s1", f -> "C".equals(f.flatFields().get(1)))).isPresent();
        assertThat(buffer.poll("s1", f -> "B".equals(f.flatFields().get(1)))).isPresent();
    }

    @Test
    void expiredMessagesAreEvictedOnPoll() {
        MessageBuffer buffer = new MessageBuffer(1024, -1L); // any age exceeds ttl
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(11, "ORD1")));
        assertThat(buffer.poll("s1", f -> true)).isEmpty();
        assertThat(buffer.size("s1")).isZero();
    }

    @Test
    void pauseResumeTogglesState() {
        MessageBuffer buffer = new MessageBuffer();
        assertThat(buffer.isPaused()).isFalse();
        buffer.pause();
        assertThat(buffer.isPaused()).isTrue();
        buffer.resume();
        assertThat(buffer.isPaused()).isFalse();
    }

    @Test
    void parkCopiesFieldsDefensively() {
        MessageBuffer buffer = new MessageBuffer();
        var src = Fixtures.fields(11, "ORD1");
        buffer.park("s1", FIXMessageData.ofFields(src));
        src.put(99, "x");
        Optional<FIXMessageData> polled = buffer.poll("s1", f -> true);
        assertThat(polled.get().flatFields()).doesNotContainKey(99);
    }

    @Test
    void concurrent_polls_never_hand_one_message_to_two_matchers() throws Exception {
        // Two executions draining one session at once (PARALLEL children): the matcher consumes
        // the message (it completes a waiter), so it must run at most once per buffered message.
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", FIXMessageData.ofFields(Fixtures.fields(35, "S")));
        java.util.concurrent.CountDownLatch bothPolling = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger consumed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Predicate<FIXMessageData> consuming = m -> {
            bothPolling.countDown();
            try { bothPolling.await(300, java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            consumed.incrementAndGet();
            return true;
        };
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> buffer.poll("s1", consuming));
            var b = pool.submit(() -> buffer.poll("s1", consuming));
            long present = java.util.stream.Stream.of(a.get(), b.get()).filter(Optional::isPresent).count();
            assertThat(present).isEqualTo(1);
        }
        assertThat(consumed.get()).isEqualTo(1);
        assertThat(buffer.size("s1")).isZero();
    }
}
