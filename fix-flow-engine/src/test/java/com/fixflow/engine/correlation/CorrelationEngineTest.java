package com.fixflow.engine.correlation;

import com.fixflow.core.domain.scenario.CorrelationRule;
import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    void multiRuleRoutesToFirstMatchingRule() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationEngine.RoutingRule quoteRule = new CorrelationEngine.RoutingRule("r1", "Quote", Map.of(35, "S"), "quote-node");
        CorrelationEngine.RoutingRule rejectRule = new CorrelationEngine.RoutingRule("r2", "Reject", Map.of(35, "AG"), "reject-node");

        CompletableFuture<CorrelationEngine.RoutedResult> f =
                engine.registerMulti("exec-1", List.of(quoteRule, rejectRule));

        engine.onMessage("sess", Map.of(35, "S", 131, "RFQ-001"));

        CorrelationEngine.RoutedResult result = f.get(500, TimeUnit.MILLISECONDS);
        assertThat(result.matchedRuleId()).isEqualTo("r1");
        assertThat(result.targetNodeId()).isEqualTo("quote-node");
        assertThat(result.fields()).containsEntry(35, "S");
    }

    @Test
    void multiRuleDefaultRuleMatchesWhenNoSpecificRuleFires() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationEngine.RoutingRule quoteRule = new CorrelationEngine.RoutingRule("r1", "Quote", Map.of(35, "S"), "quote-node");
        CorrelationEngine.RoutingRule defaultRule = new CorrelationEngine.RoutingRule("r2", "Default", Map.of(), "default-node");

        CompletableFuture<CorrelationEngine.RoutedResult> f =
                engine.registerMulti("exec-1", List.of(quoteRule, defaultRule));

        engine.onMessage("sess", Map.of(35, "8", 39, "2"));

        CorrelationEngine.RoutedResult result = f.get(500, TimeUnit.MILLISECONDS);
        assertThat(result.matchedRuleId()).isEqualTo("r2");
        assertThat(result.targetNodeId()).isEqualTo("default-node");
    }

    @Test
    void multiRuleCompoundAndConditionRequiresAllTagsToMatch() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationEngine.RoutingRule rejectedRule = new CorrelationEngine.RoutingRule(
                "r1", "Rejected", Map.of(35, "8", 39, "8"), "rejected-node");

        CompletableFuture<CorrelationEngine.RoutedResult> f =
                engine.registerMulti("exec-1", List.of(rejectedRule));

        // Partial match — tag 35 matches but tag 39 does not
        boolean matched = engine.onMessage("sess", Map.of(35, "8", 39, "2"));
        assertThat(matched).isFalse();

        // Full match — both tags match
        engine.onMessage("sess", Map.of(35, "8", 39, "8"));

        CorrelationEngine.RoutedResult result = f.get(500, TimeUnit.MILLISECONDS);
        assertThat(result.matchedRuleId()).isEqualTo("r1");
        assertThat(result.targetNodeId()).isEqualTo("rejected-node");
    }
}
