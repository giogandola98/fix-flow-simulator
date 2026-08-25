package com.fixflow.engine.correlation;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.CorrelationRule;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CorrelationEngine {

    /**
     * A single-message waiter. {@code matchers} is the authoritative predicate: every
     * {@code tag -> value} entry must be present and equal on the inbound message. An EMPTY
     * matcher map matches the first application message on the session — that is what an
     * EXPECT_FIX with neither a MsgType nor a correlation asks for.
     *
     * <p>{@code rule}/{@code expectedValue} are kept for introspection and for the legacy
     * single-tag {@link #register} entry point; they are null for matcher-based waiters.
     */
    public record CorrelationWaiter(
            String executionId,
            String sessionId,
            CorrelationRule rule,
            String expectedValue,
            Map<Integer, String> matchers,
            CompletableFuture<FIXMessageData> future) {}

    public record RoutingRule(String ruleId, String label, Map<Integer, String> matchers, String targetNodeId) {}

    public record RoutedResult(FIXMessageData message, String matchedRuleId, String targetNodeId) {}

    public record MultiRouteWaiter(
            String executionId,
            String sessionId,
            List<RoutingRule> rules,
            CompletableFuture<RoutedResult> future) {}

    private final ConcurrentHashMap<String, CorrelationWaiter> waiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MultiRouteWaiter> multiWaiters = new ConcurrentHashMap<>();

    /** Single-tag waiter: the inbound message must carry {@code rule.sourceTag() == expectedValue}. */
    public CompletableFuture<FIXMessageData> register(String executionId,
                                                      String sessionId,
                                                      CorrelationRule rule,
                                                      String expectedValue) {
        Map<Integer, String> matchers = new LinkedHashMap<>();
        matchers.put(rule.sourceTag(), expectedValue);
        return register(executionId, sessionId, rule, expectedValue, matchers);
    }

    /**
     * Multi-tag waiter: the inbound message must satisfy EVERY entry of {@code matchers}.
     * This is how EXPECT_FIX combines a MsgType (tag 35) with a correlation tag instead of
     * letting one silently replace the other.
     */
    public CompletableFuture<FIXMessageData> registerAll(String executionId,
                                                         String sessionId,
                                                         Map<Integer, String> matchers) {
        return register(executionId, sessionId, null, null, matchers);
    }

    private CompletableFuture<FIXMessageData> register(String executionId,
                                                       String sessionId,
                                                       CorrelationRule rule,
                                                       String expectedValue,
                                                       Map<Integer, String> matchers) {
        CompletableFuture<FIXMessageData> future = new CompletableFuture<>();
        // A LinkedHashMap copy, not Map.copyOf: the legacy single-tag entry point may carry a
        // null expected value, which Map.copyOf rejects. matchesAll treats it as never-matching,
        // exactly as the previous equals-based comparison did.
        CorrelationWaiter waiter = new CorrelationWaiter(
                executionId, sessionId, rule, expectedValue,
                Collections.unmodifiableMap(new LinkedHashMap<>(matchers)), future);
        if (waiters.putIfAbsent(executionId, waiter) != null) {
            throw new IllegalStateException("duplicate executionId: " + executionId);
        }
        return future;
    }

    public CompletableFuture<RoutedResult> registerMulti(String executionId, String sessionId, List<RoutingRule> rules) {
        CompletableFuture<RoutedResult> future = new CompletableFuture<>();
        if (multiWaiters.putIfAbsent(executionId, new MultiRouteWaiter(executionId, sessionId, rules, future)) != null) {
            throw new IllegalStateException("duplicate executionId: " + executionId);
        }
        return future;
    }

    public boolean onMessage(String sessionId, FIXMessageData message) {
        Map<Integer, String> fields = message.flatFields();
        for (CorrelationWaiter w : waiters.values()) {
            if (!w.sessionId().equals(sessionId)) continue;
            if (!matchesAll(w.matchers(), fields)) continue;
            waiters.remove(w.executionId());
            w.future().complete(message);
            return true;
        }
        for (MultiRouteWaiter w : multiWaiters.values()) {
            if (!w.sessionId().equals(sessionId)) continue;
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
                w.future().complete(new RoutedResult(message, matched.ruleId(), matched.targetNodeId()));
                return true;
            }
        }
        return false;
    }

    /** Every matcher must be present and equal. An empty matcher map matches any message. */
    private static boolean matchesAll(Map<Integer, String> matchers, Map<Integer, String> fields) {
        for (Map.Entry<Integer, String> e : matchers.entrySet()) {
            String actual = fields.get(e.getKey());
            if (actual == null || !actual.equals(e.getValue())) return false;
        }
        return true;
    }

    /** Legacy flat-map entry point, kept for existing tests. */
    public boolean onMessage(String sessionId, Map<Integer, String> fields) {
        return onMessage(sessionId, FIXMessageData.ofFields(fields));
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
