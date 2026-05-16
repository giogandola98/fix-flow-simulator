package com.fixflow.engine.correlation;

import com.fixflow.core.domain.scenario.CorrelationRule;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CorrelationEngine {

    public record CorrelationWaiter(
            String executionId,
            CorrelationRule rule,
            String expectedValue,
            CompletableFuture<Map<Integer, String>> future) {}

    private final ConcurrentHashMap<String, CorrelationWaiter> waiters = new ConcurrentHashMap<>();

    public CompletableFuture<Map<Integer, String>> register(String executionId,
                                                            CorrelationRule rule,
                                                            String expectedValue) {
        CompletableFuture<Map<Integer, String>> future = new CompletableFuture<>();
        CorrelationWaiter waiter = new CorrelationWaiter(executionId, rule, expectedValue, future);
        if (waiters.putIfAbsent(executionId, waiter) != null) {
            throw new IllegalStateException("duplicate executionId: " + executionId);
        }
        return future;
    }

    public boolean onMessage(String sessionId, Map<Integer, String> fields) {
        for (CorrelationWaiter w : waiters.values()) {
            String actual = fields.get(w.rule().sourceTag());
            if (actual != null && actual.equals(w.expectedValue())) {
                waiters.remove(w.executionId());
                w.future().complete(Map.copyOf(fields));
                return true;
            }
        }
        return false;
    }

    public void cancel(String executionId) {
        CorrelationWaiter w = waiters.remove(executionId);
        if (w != null) w.future().cancel(true);
    }

    public int pendingCount() { return waiters.size(); }
}
