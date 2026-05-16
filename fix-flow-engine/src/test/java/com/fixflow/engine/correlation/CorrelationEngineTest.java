package com.fixflow.engine.correlation;

import com.fixflow.core.domain.scenario.CorrelationRule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationEngineTest {

    @Test
    void deliversMatchingMessageToWaiter() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationRule rule = new CorrelationRule(131, "n1", 131, 1000);
        CompletableFuture<Map<Integer, String>> f = engine.register("exec-1", rule, "REQ-1");

        engine.onMessage("sess", Map.of(131, "REQ-1", 35, "8"));

        Map<Integer, String> got = f.get(500, TimeUnit.MILLISECONDS);
        assertThat(got).containsEntry(131, "REQ-1");
    }

    @Test
    void nonMatchingMessageLeavesWaiterPending() {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationRule rule = new CorrelationRule(131, "n1", 131, 1000);
        CompletableFuture<Map<Integer, String>> f = engine.register("exec-1", rule, "REQ-1");

        engine.onMessage("sess", Map.of(131, "REQ-OTHER"));

        assertThatThrownBy(() -> f.get(100, TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void cancelCompletesFutureExceptionally() {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationRule rule = new CorrelationRule(131, "n1", 131, 1000);
        CompletableFuture<Map<Integer, String>> f = engine.register("exec-1", rule, "REQ-1");

        engine.cancel("exec-1");
        assertThat(f).isCancelled();
    }
}
