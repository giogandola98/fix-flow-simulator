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
}
