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
import java.util.concurrent.atomic.AtomicLong;

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
     *
     * <p>{@code seq} is the registration sequence number that breaks ties in {@link #onMessage}.
     */
    public record CorrelationWaiter(
            String executionId,
            String sessionId,
            CorrelationRule rule,
            String expectedValue,
            Map<Integer, String> matchers,
            CompletableFuture<FIXMessageData> future,
            long seq) {}

    public record RoutingRule(String ruleId, String label, Map<Integer, String> matchers, String targetNodeId) {}

    public record RoutedResult(FIXMessageData message, String matchedRuleId, String targetNodeId) {}

    public record MultiRouteWaiter(
            String executionId,
            String sessionId,
            List<RoutingRule> rules,
            CompletableFuture<RoutedResult> future,
            long seq) {}

    private final ConcurrentHashMap<String, CorrelationWaiter> waiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MultiRouteWaiter> multiWaiters = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    /**
     * Serialises waiter selection and removal so two inbound messages handled at the same time can
     * never both pick (and complete) the same waiter, and the "most specific" choice is made against
     * one consistent set of waiters. Registration and cancellation stay lock-free.
     */
    private final Object selectLock = new Object();

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
                Collections.unmodifiableMap(new LinkedHashMap<>(matchers)), future,
                sequence.incrementAndGet());
        if (waiters.putIfAbsent(executionId, waiter) != null) {
            throw new IllegalStateException("duplicate executionId: " + executionId);
        }
        return future;
    }

    public CompletableFuture<RoutedResult> registerMulti(String executionId, String sessionId, List<RoutingRule> rules) {
        CompletableFuture<RoutedResult> future = new CompletableFuture<>();
        MultiRouteWaiter waiter = new MultiRouteWaiter(executionId, sessionId, rules, future,
                sequence.incrementAndGet());
        if (multiWaiters.putIfAbsent(executionId, waiter) != null) {
            throw new IllegalStateException("duplicate executionId: " + executionId);
        }
        return future;
    }

    /**
     * Hands {@code message} to at most one waiter of {@code sessionId}.
     *
     * <p>Among the waiters whose conditions all hold, the one with the <b>most matchers</b> wins;
     * on a tie the earliest registered wins. This is what lets several executions wait on one
     * session for the same MsgType, each for its own correlation value (e.g. an inquiry id): the
     * message goes to the waiter that names its value, and a waiter with no matchers (a catch-all)
     * only receives what no more specific waiter wants.
     *
     * <p>A ROUTE_FIX waiter counts as specific as its first matching rule (rules keep their own
     * authoring order inside one waiter); its default rule (no matchers) counts 0.
     */
    public boolean onMessage(String sessionId, FIXMessageData message) {
        Map<Integer, String> fields = message.flatFields();
        CorrelationWaiter single;
        MultiRouteWaiter multi;
        RoutingRule multiRule;
        synchronized (selectLock) {
            single = null;
            int singleScore = -1;
            for (CorrelationWaiter w : waiters.values()) {
                if (!w.sessionId().equals(sessionId)) continue;
                if (!matchesAll(w.matchers(), fields)) continue;
                int score = w.matchers().size();
                if (score > singleScore || (score == singleScore && w.seq() < single.seq())) {
                    single = w;
                    singleScore = score;
                }
            }
            multi = null;
            multiRule = null;
            int multiScore = -1;
            for (MultiRouteWaiter w : multiWaiters.values()) {
                if (!w.sessionId().equals(sessionId)) continue;
                RoutingRule matched = matchedRule(w, fields);
                if (matched == null) continue;
                int score = matched.matchers().size();
                if (score > multiScore || (score == multiScore && w.seq() < multi.seq())) {
                    multi = w;
                    multiRule = matched;
                    multiScore = score;
                }
            }
            if (single == null && multi == null) return false;
            boolean singleWins = multi == null
                    || (single != null && (singleScore > multiScore
                        || (singleScore == multiScore && single.seq() < multi.seq())));
            // A false remove means a concurrent cancel took the waiter: choose again.
            boolean removed;
            if (singleWins) {
                removed = waiters.remove(single.executionId(), single);
                multi = null;
            } else {
                removed = multiWaiters.remove(multi.executionId(), multi);
                single = null;
            }
            if (!removed) return onMessage(sessionId, message);
        }
        // Completed outside the lock: completion runs the waiting execution's continuation.
        if (single != null) {
            single.future().complete(message);
        } else {
            multi.future().complete(new RoutedResult(message, multiRule.ruleId(), multiRule.targetNodeId()));
        }
        return true;
    }

    /** The first non-default rule of {@code w} that matches, else its default rule, else null. */
    private static RoutingRule matchedRule(MultiRouteWaiter w, Map<Integer, String> fields) {
        RoutingRule defaultRule = null;
        for (RoutingRule rule : w.rules()) {
            if (rule.matchers().isEmpty()) { defaultRule = rule; continue; }
            if (matchesAll(rule.matchers(), fields)) return rule;
        }
        return defaultRule;
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
