package com.fixflow.engine.correlation;

import com.fixflow.core.domain.scenario.CorrelationRule;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public record RoutingRule(String ruleId, String label, Map<Integer, String> matchers, String targetNodeId) {}

    public record RoutedResult(Map<Integer, String> fields, String matchedRuleId, String targetNodeId) {}

    public record MultiRouteWaiter(
            String executionId,
            List<RoutingRule> rules,
            CompletableFuture<RoutedResult> future) {}

    private final ConcurrentHashMap<String, CorrelationWaiter> waiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MultiRouteWaiter> multiWaiters = new ConcurrentHashMap<>();

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

    public CompletableFuture<RoutedResult> registerMulti(String executionId, List<RoutingRule> rules) {
        CompletableFuture<RoutedResult> future = new CompletableFuture<>();
        multiWaiters.put(executionId, new MultiRouteWaiter(executionId, rules, future));
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
        for (MultiRouteWaiter w : multiWaiters.values()) {
            RoutingRule matched = null;
            RoutingRule defaultRule = null;
            for (RoutingRule rule : w.rules()) {
                if (rule.matchers().isEmpty()) { defaultRule = rule; continue; }
                boolean allMatch = rule.matchers().entrySet().stream()
                        .allMatch(e -> e.getValue().equals(fields.get(e.getKey())));
                if (allMatch) { matched = rule; break; }
            }
            if (matched == null) matched = defaultRule;
            if (matched != null) {
                multiWaiters.remove(w.executionId());
                w.future().complete(new RoutedResult(Map.copyOf(fields), matched.ruleId(), matched.targetNodeId()));
                return true;
            }
        }
        return false;
    }

    public void cancel(String executionId) {
        CorrelationWaiter w = waiters.remove(executionId);
        if (w != null) w.future().cancel(true);
    }

    public void cancelMulti(String executionId) {
        MultiRouteWaiter w = multiWaiters.remove(executionId);
        if (w != null) w.future().cancel(true);
    }

    public int pendingCount() { return waiters.size() + multiWaiters.size(); }
}
