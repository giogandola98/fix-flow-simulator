package com.fixflow.engine.fix;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBufferTest {

    @Test
    void parkAndPollExactMatch() {
        MessageBuffer buf = new MessageBuffer(10, 60_000);
        buf.park("s1", Map.of(35, "8", 131, "REQ-1"));

        Optional<Map<Integer, String>> found =
                buf.poll("s1", f -> "REQ-1".equals(f.get(131)));

        assertThat(found).isPresent();
        assertThat(found.get()).containsEntry(35, "8");
        assertThat(buf.poll("s1", f -> "REQ-1".equals(f.get(131)))).isEmpty();
    }

    @Test
    void capacityEvictsOldestOnOverflow() {
        MessageBuffer buf = new MessageBuffer(2, 60_000);
        buf.park("s1", Map.of(11, "A"));
        buf.park("s1", Map.of(11, "B"));
        buf.park("s1", Map.of(11, "C"));

        assertThat(buf.poll("s1", f -> "A".equals(f.get(11)))).isEmpty();
        assertThat(buf.poll("s1", f -> "B".equals(f.get(11)))).isPresent();
        assertThat(buf.poll("s1", f -> "C".equals(f.get(11)))).isPresent();
    }

    @Test
    void ttlExpiryRemovesStaleEntries() throws Exception {
        MessageBuffer buf = new MessageBuffer(10, 50);
        buf.park("s1", Map.of(11, "X"));
        Thread.sleep(100);
        assertThat(buf.poll("s1", f -> "X".equals(f.get(11)))).isEmpty();
    }

    @Test
    void pauseAndResume() {
        MessageBuffer buf = new MessageBuffer(10, 60_000);
        assertThat(buf.isPaused()).isFalse();
        buf.pause();
        assertThat(buf.isPaused()).isTrue();
        buf.resume();
        assertThat(buf.isPaused()).isFalse();
    }
}
