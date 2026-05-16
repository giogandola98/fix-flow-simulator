package com.fixflow.engine.fix;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@Service
public class MessageBuffer {

    public record BufferedMessage(Map<Integer, String> fields, Instant parkedAt) {}

    private final int capacity;
    private final long ttlMs;
    private final Map<String, Deque<BufferedMessage>> buffers = new ConcurrentHashMap<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public MessageBuffer() {
        this(1024, Duration.ofMinutes(5).toMillis());
    }

    public MessageBuffer(int capacity, long ttlMs) {
        this.capacity = capacity;
        this.ttlMs = ttlMs;
    }

    public void park(String sessionId, Map<Integer, String> fields) {
        Deque<BufferedMessage> deque =
                buffers.computeIfAbsent(sessionId, k -> new ConcurrentLinkedDeque<>());
        deque.addFirst(new BufferedMessage(Map.copyOf(fields), Instant.now()));
        while (deque.size() > capacity) deque.pollLast();
    }

    public Optional<Map<Integer, String>> poll(String sessionId, Predicate<Map<Integer, String>> matcher) {
        Deque<BufferedMessage> deque = buffers.get(sessionId);
        if (deque == null) return Optional.empty();

        Instant now = Instant.now();
        Iterator<BufferedMessage> it = deque.iterator();
        while (it.hasNext()) {
            BufferedMessage m = it.next();
            if (now.toEpochMilli() - m.parkedAt().toEpochMilli() > ttlMs) {
                it.remove();
                continue;
            }
            if (matcher.test(m.fields())) {
                it.remove();
                return Optional.of(m.fields());
            }
        }
        return Optional.empty();
    }

    public void pause() { paused.set(true); }
    public void resume() { paused.set(false); }
    public boolean isPaused() { return paused.get(); }

    public int size(String sessionId) {
        Deque<BufferedMessage> d = buffers.get(sessionId);
        return d == null ? 0 : d.size();
    }
}
