package com.fixflow.engine.correlation;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationEngineTest {

    private CorrelationEngine engine;
    private static final String SESSION = "sess-1";

    @BeforeEach
    void setUp() { engine = new CorrelationEngine(); }

    private CorrelationRule rule(int tag) { return new CorrelationRule(tag, "node", tag, 0); }

    @Test
    void registerThenMatchingMessageCompletesFuture() {
        CompletableFuture<FIXMessageData> f = engine.register("exec-1", SESSION, rule(11), "ORD1");
        assertThat(engine.pendingCount()).isEqualTo(1);

        boolean consumed = engine.onMessage(SESSION, Fixtures.fields(11, "ORD1", 35, "8"));
        assertThat(consumed).isTrue();
        assertThat(f).isCompleted();
        assertThat(f.join().flatFields()).containsEntry(11, "ORD1");
        assertThat(engine.pendingCount()).isZero();
    }

    @Test
    void nonMatchingValueDoesNotConsume() {
        engine.register("exec-1", SESSION, rule(11), "ORD1");
        assertThat(engine.onMessage(SESSION, Fixtures.fields(11, "OTHER"))).isFalse();
        assertThat(engine.pendingCount()).isEqualTo(1);
    }

    @Test
    void differentSessionIsIgnored() {
        engine.register("exec-1", SESSION, rule(11), "ORD1");
        assertThat(engine.onMessage("other-session", Fixtures.fields(11, "ORD1"))).isFalse();
        assertThat(engine.pendingCount()).isEqualTo(1);
    }

    @Test
    void cancelRemovesWaiterAndCancelsFuture() {
        CompletableFuture<FIXMessageData> f = engine.register("exec-1", SESSION, rule(11), "ORD1");
        engine.cancel("exec-1");
        assertThat(f).isCancelled();
        assertThat(engine.pendingCount()).isZero();
        assertThat(engine.onMessage(SESSION, Fixtures.fields(11, "ORD1"))).isFalse();
    }

    @Test
    void duplicateExecutionIdThrows() {
        engine.register("exec-1", SESSION, rule(11), "ORD1");
        assertThatThrownBy(() -> engine.register("exec-1", SESSION, rule(11), "ORD2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void multiRuleRoutesToFirstMatchingRule() {
        List<CorrelationEngine.RoutingRule> rules = List.of(
                new CorrelationEngine.RoutingRule("r1", "typeD", Map.of(35, "D"), "t1"),
                new CorrelationEngine.RoutingRule("r2", "typeEight", Map.of(35, "8"), "t2"));
        CompletableFuture<CorrelationEngine.RoutedResult> f = engine.registerMulti("m1", SESSION, rules);
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "8"))).isTrue();
        CorrelationEngine.RoutedResult res = f.join();
        assertThat(res.matchedRuleId()).isEqualTo("r2");
        assertThat(res.targetNodeId()).isEqualTo("t2");
    }

    @Test
    void multiRuleFallsBackToDefaultRule() {
        List<CorrelationEngine.RoutingRule> rules = List.of(
                new CorrelationEngine.RoutingRule("r1", "typeD", Map.of(35, "D"), "t1"),
                new CorrelationEngine.RoutingRule("def", "default", Map.of(), "tDefault"));
        CompletableFuture<CorrelationEngine.RoutedResult> f = engine.registerMulti("m1", SESSION, rules);
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "Z"))).isTrue();
        assertThat(f.join().targetNodeId()).isEqualTo("tDefault");
    }

    @Test
    void multiRuleNoMatchAndNoDefaultDoesNotConsume() {
        List<CorrelationEngine.RoutingRule> rules = List.of(
                new CorrelationEngine.RoutingRule("r1", "typeD", Map.of(35, "D"), "t1"));
        engine.registerMulti("m1", SESSION, rules);
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "Z"))).isFalse();
        assertThat(engine.pendingCount()).isEqualTo(1);
    }

    @Test
    void cancelMultiRemovesAndCancels() {
        CompletableFuture<CorrelationEngine.RoutedResult> f = engine.registerMulti("m1", SESSION,
                List.of(new CorrelationEngine.RoutingRule("r1", "x", Map.of(35, "D"), "t1")));
        engine.cancelMulti("m1");
        assertThat(f).isCancelled();
        assertThat(engine.pendingCount()).isZero();
    }

    @Test
    void duplicateMultiExecutionIdThrows() {
        engine.registerMulti("m1", SESSION, List.of());
        assertThatThrownBy(() -> engine.registerMulti("m1", SESSION, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void two_waiters_same_type_message_goes_to_matching_one() {
        CompletableFuture<FIXMessageData> a = engine.registerAll("exec-A", SESSION, Map.of(35, "S", 20117, "1"));
        CompletableFuture<FIXMessageData> b = engine.registerAll("exec-B", SESSION, Map.of(35, "S", 20117, "2"));
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "S", 20117, "2"))).isTrue();
        assertThat(b).isCompleted();
        assertThat(a).isNotDone();
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "S", 20117, "1"))).isTrue();
        assertThat(a).isCompleted();
    }

    @Test
    void specific_waiter_wins_over_catch_all() {
        // Catch-alls registered first, under several ids so map iteration order cannot hide the bug:
        // registration order alone must not hand them the message.
        List<CompletableFuture<FIXMessageData>> catchAlls = new java.util.ArrayList<>();
        for (String id : List.of("exec-any", "a", "zz", "q7", "m")) catchAlls.add(engine.registerAll(id, SESSION, Map.of()));
        CompletableFuture<FIXMessageData> any = catchAlls.get(0);
        CompletableFuture<FIXMessageData> specific =
                engine.registerAll("exec-specific", SESSION, Map.of(35, "S", 20117, "2"));
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "S", 20117, "2"))).isTrue();
        assertThat(specific).isCompleted();
        assertThat(catchAlls).noneMatch(CompletableFuture::isDone);
        // The catch-all still takes what nobody else matches.
        assertThat(engine.onMessage(SESSION, Fixtures.fields(35, "S", 20117, "9"))).isTrue();
        assertThat(any).isCompleted();
    }

    @Test
    void specific_waiter_wins_over_catch_all_registered_after_it() {
        CompletableFuture<FIXMessageData> specific =
                engine.registerAll("exec-specific", SESSION, Map.of(35, "S", 20117, "2"));
        CompletableFuture<FIXMessageData> any = engine.registerAll("exec-any", SESSION, Map.of());
        engine.onMessage(SESSION, Fixtures.fields(35, "S", 20117, "2"));
        assertThat(specific).isCompleted();
        assertThat(any).isNotDone();
    }

    @Test
    void equally_specific_waiters_go_in_registration_order() {
        // Ids chosen so that hash iteration order differs from registration order.
        String[] ids = {"z9", "a1", "m5", "b2", "y8"};
        List<CompletableFuture<FIXMessageData>> futures = new java.util.ArrayList<>();
        for (String id : ids) futures.add(engine.registerAll(id, SESSION, Map.of(35, "S")));
        for (int i = 0; i < ids.length; i++) {
            engine.onMessage(SESSION, Fixtures.fields(35, "S"));
            assertThat(futures.get(i)).as("waiter %s", ids[i]).isCompleted();
            for (int j = i + 1; j < ids.length; j++) assertThat(futures.get(j)).isNotDone();
        }
    }

    @Test
    void route_rule_and_single_waiter_compete_on_matcher_count() {
        CompletableFuture<CorrelationEngine.RoutedResult> route = engine.registerMulti("exec-route:route:n", SESSION,
                List.of(new CorrelationEngine.RoutingRule("r1", "quote", Map.of(35, "S", 20117, "7"), "t1"),
                        new CorrelationEngine.RoutingRule("rd", "default", Map.of(), "tDefault")));
        CompletableFuture<FIXMessageData> single = engine.registerAll("exec-single", SESSION, Map.of(35, "S"));
        // 20117=7: the route's 2-matcher rule beats the single 1-matcher waiter.
        engine.onMessage(SESSION, Fixtures.fields(35, "S", 20117, "7"));
        assertThat(route).isCompleted();
        assertThat(route.join().matchedRuleId()).isEqualTo("r1");
        assertThat(single).isNotDone();

        CompletableFuture<CorrelationEngine.RoutedResult> route2 = engine.registerMulti("exec-route2:route:n", SESSION,
                List.of(new CorrelationEngine.RoutingRule("rd", "default", Map.of(), "tDefault")));
        // A default rule counts 0: the single 35=S waiter wins.
        engine.onMessage(SESSION, Fixtures.fields(35, "S"));
        assertThat(single).isCompleted();
        assertThat(route2).isNotDone();
    }

    @Test
    void concurrent_messages_complete_each_waiter_once() {
        int n = 50;
        List<CompletableFuture<FIXMessageData>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) futures.add(engine.registerAll("e" + i, SESSION, Map.of(35, "S")));
        java.util.concurrent.atomic.AtomicInteger consumed = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n * 2; i++) {
                pool.submit(() -> {
                    if (engine.onMessage(SESSION, Fixtures.fields(35, "S"))) consumed.incrementAndGet();
                });
            }
        }
        assertThat(consumed.get()).isEqualTo(n);
        assertThat(futures).allMatch(CompletableFuture::isDone);
        assertThat(engine.pendingCount()).isZero();
    }
}
