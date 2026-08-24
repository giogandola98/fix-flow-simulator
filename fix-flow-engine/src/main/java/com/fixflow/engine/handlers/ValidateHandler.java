package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ValidateHandler implements NodeHandler {

    private final ValidationEngine engine;

    public ValidateHandler(ValidationEngine engine) { this.engine = engine; }

    @Override
    public NodeType getSupportedType() { return NodeType.VALIDATE; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        // sourceNodeId points to the EXPECT_FIX node whose stored message we validate
        String sourceId = node.config().get("sourceNodeId") != null
                ? String.valueOf(node.config().get("sourceNodeId")) : null;
        FIXMessageData message = ctx.getNodeMessageData(sourceId != null ? sourceId : node.id());
        if (message == null) message = FIXMessageData.ofFields(Map.of());

        ValidationConfig cfg;
        try {
            cfg = toConfig(node.config());
        } catch (InvalidGroupTagException e) {
            return NodeHandlerResult.failure(node.onFailure(), e.getMessage());
        }
        ValidationSummary summary = engine.validate(cfg, message, ctx, Instant.now());

        return summary.passed()
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "validation failed");
    }

    @SuppressWarnings("unchecked")
    private ValidationConfig toConfig(Map<String, Object> raw) {
        // Accept both "rules" (DSL/UI) and legacy "validations"
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) raw.getOrDefault("rules",
                raw.getOrDefault("validations", List.of()));
        List<ValidationRuleConfig> rules = new ArrayList<>();
        for (Map<String, Object> rr : rawRules) {
            int tag = ((Number) rr.get("tag")).intValue();
            String rule = (String) rr.get("rule");
            String value = (String) rr.get("value");
            List<String> values = (List<String>) rr.get("values");
            String ref = (String) rr.get("ref");
            String dateRule = (String) rr.get("dateRule");
            String pattern = (String) rr.get("pattern");
            double num = rr.get("numericValue") == null ? 0 : ((Number) rr.get("numericValue")).doubleValue();
            Integer groupTag = parseGroupTag(rr.get("groupTag"));
            String index = rr.get("index") == null ? null : String.valueOf(rr.get("index"));
            rules.add(new ValidationRuleConfig(tag, rule, value, values, ref, dateRule, pattern, num,
                                               groupTag, index));
        }
        boolean strict = Boolean.TRUE.equals(raw.get("strictMode"));
        Map<String, DateRule> dateRules = (Map<String, DateRule>) raw.getOrDefault("dateRules", Map.of());
        return new ValidationConfig(rules, dateRules, strict);
    }

    /**
     * Parses a rule's {@code groupTag}, accepting a {@link Number} or a numeric {@link String}
     * (YAML may quote it, e.g. {@code groupTag: '555'}). Anything else raises
     * {@link InvalidGroupTagException} rather than letting a {@link ClassCastException} escape —
     * {@code handle} turns that into a normal validation failure via the node's onFailure edge,
     * matching how {@code index} was already hardened.
     */
    private Integer parseGroupTag(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new InvalidGroupTagException("Invalid groupTag: " + s);
            }
        }
        throw new InvalidGroupTagException("Invalid groupTag: " + raw);
    }

    static final class InvalidGroupTagException extends RuntimeException {
        InvalidGroupTagException(String message) { super(message); }
    }
}
