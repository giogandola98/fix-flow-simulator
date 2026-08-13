package com.fixflow.engine.fix;

import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBufferTest {

    @Test
    void parkThenPollReturnsMatchingMessageAndRemovesIt() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", Fixtures.fields(11, "ORD1"));
        assertThat(buffer.size("s1")).isEqualTo(1);

        Optional<Map<Integer, String>> polled = buffer.poll("s1", f -> "ORD1".equals(f.get(11)));
        assertThat(polled).isPresent();
        assertThat(polled.get()).containsEntry(11, "ORD1");
        assertThat(buffer.size("s1")).isZero();
    }

    @Test
    void pollNonMatchingLeavesMessageParked() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", Fixtures.fields(11, "ORD1"));
        assertThat(buffer.poll("s1", f -> "nope".equals(f.get(11)))).isEmpty();
        assertThat(buffer.size("s1")).isEqualTo(1);
    }

    @Test
    void pollUnknownSessionIsEmpty() {
        assertThat(new MessageBuffer().poll("ghost", f -> true)).isEmpty();
    }

    @Test
    void pollReturnsNewestMatchFirst() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("s1", Fixtures.fields(11, "OLD"));
        buffer.park("s1", Fixtures.fields(11, "NEW"));
        Optional<Map<Integer, String>> polled = buffer.poll("s1", f -> true);
        assertThat(polled).isPresent();
        assertThat(polled.get()).containsEntry(11, "NEW"); // head = most recently parked
    }

    @Test
    void capacityEvictsOldestKeepingNewest() {
        MessageBuffer buffer = new MessageBuffer(2, Long.MAX_VALUE);
        buffer.park("s1", Fixtures.fields(1, "A"));
        buffer.park("s1", Fixtures.fields(1, "B"));
        buffer.park("s1", Fixtures.fields(1, "C"));
        assertThat(buffer.size("s1")).isEqualTo(2);
        // oldest (A) evicted; B and C remain
        assertThat(buffer.poll("s1", f -> "A".equals(f.get(1)))).isEmpty();
        assertThat(buffer.poll("s1", f -> "C".equals(f.get(1)))).isPresent();
        assertThat(buffer.poll("s1", f -> "B".equals(f.get(1)))).isPresent();
    }

    @Test
    void expiredMessagesAreEvictedOnPoll() {
        MessageBuffer buffer = new MessageBuffer(1024, -1L); // any age exceeds ttl
        buffer.park("s1", Fixtures.fields(11, "ORD1"));
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
        Map<Integer, String> src = Fixtures.fields(11, "ORD1");
        buffer.park("s1", src);
        src.put(99, "x");
        Optional<Map<Integer, String>> polled = buffer.poll("s1", f -> true);
        assertThat(polled.get()).doesNotContainKey(99);
    }
}
