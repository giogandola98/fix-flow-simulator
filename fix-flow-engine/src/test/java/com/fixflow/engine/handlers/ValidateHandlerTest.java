package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.DateRuleEngine;
import com.fixflow.engine.validation.ValidationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateHandlerTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());
    private final ValidateHandler handler = new ValidateHandler(engine);

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "t", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of());
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void returnsOnSuccessWhenAllRulesPass() throws Exception {
        ExecutionContext ctx = freshCtx();
        ctx.storeNodeMessage("v1", Map.of(35, "8", 39, "2"));
        ScenarioNode node = new ScenarioNode("v1", "validate", NodeType.VALIDATE,
            Map.of("validations", List.of(
                Map.of("tag", 35, "rule", "EQUALS", "value", "8"),
                Map.of("tag", 39, "rule", "EQUALS", "value", "2")
            )),
            null, null, "next", "fail", null);
        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("next");
        assertThat(r.success()).isTrue();
    }

    @Test
    void returnsOnFailureWhenRuleFails() throws Exception {
        ExecutionContext ctx = freshCtx();
        ctx.storeNodeMessage("v1", Map.of(35, "8", 39, "1"));
        ScenarioNode node = new ScenarioNode("v1", "validate", NodeType.VALIDATE,
            Map.of("validations", List.of(
                Map.of("tag", 39, "rule", "EQUALS", "value", "2")
            )),
            null, null, "next", "fail", null);
        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("fail");
        assertThat(r.success()).isFalse();
    }
}
