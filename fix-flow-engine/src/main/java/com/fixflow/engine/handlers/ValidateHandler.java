package com.fixflow.engine.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ValidateHandler implements NodeHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ValidationEngine engine;

    public ValidateHandler(ValidationEngine engine) { this.engine = engine; }

    @Override
    public NodeType getSupportedType() { return NodeType.VALIDATE; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        FIXMessageData message = resolveMessage(node, ctx);
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
            : NodeHandlerResult.failure(node.onFailure(), describeFailures(summary));
    }

    /**
     * Renders the failed rules as the JSON array the Validation Errors panel parses out of the
     * ERROR event detail. Reporting the bare string "validation failed" left that panel with a
     * single {@code tag 0 / UNKNOWN} row, so a scenario could branch on a validation failure
     * without ever showing which rule failed and why (issue #76).
     */
    private String describeFailures(ValidationSummary summary) {
        List<Map<String, Object>> failures = new ArrayList<>();
        for (ValidationResult r : summary.results()) {
            if (r.passed()) continue;
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("tag", r.tag());
            failure.put("rule", r.ruleName());
            failure.put("expected", r.expected() == null ? "" : r.expected());
            failure.put("actual", r.actual() == null ? "" : r.actual());
            if (r.message() != null) failure.put("message", r.message());
            failures.add(failure);
        }
        if (failures.isEmpty()) return "validation failed";
        try {
            return JSON.writeValueAsString(failures);
        } catch (JsonProcessingException e) {
            // Never worth failing a run over: fall back to the plain wording.
            return "validation failed";
        }
    }

    /**
     * Finds the message to validate.
     *
     * <ol>
     *   <li>{@code config.sourceNodeId}, when the scenario names the receiving node explicitly;</li>
     *   <li>otherwise the run's most recent inbound message. The graphical editor had no field for
     *       {@code sourceNodeId} until this fix, so every VALIDATE authored from the GUI fell
     *       through to its own node id and validated an empty message (issue #77);</li>
     *   <li>finally the node's own id, preserving the original lookup for any scenario that
     *       stored a message under it.</li>
     * </ol>
     */
    private FIXMessageData resolveMessage(ScenarioNode node, ExecutionContext ctx) {
        Object rawSource = node.config().get("sourceNodeId");
        String sourceId = rawSource == null ? null : String.valueOf(rawSource).trim();
        if (sourceId != null && !sourceId.isEmpty()) {
            return ctx.getNodeMessageData(sourceId);
        }
        FIXMessageData lastInbound = ctx.lastInboundMessage();
        return lastInbound != null ? lastInbound : ctx.getNodeMessageData(node.id());
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
        return new ValidationConfig(rules, toDateRules(raw.get("dateRules")), strict);
    }

    /**
     * Builds the {@code ruleId -> DateRule} lookup from whatever the DSL carried.
     *
     * <p>The value is a raw YAML structure, never a {@link DateRule}: the editor writes a LIST of
     * rule objects, hand-written DSL tends to write a map keyed by rule id, and the old code cast
     * it straight to {@code Map<String, DateRule>}. That cast is a real checkcast, so a node
     * carrying the editor's {@code dateRules: []} threw ClassCastException before a single rule
     * ran — for every VALIDATE node authored from the GUI, whether or not it used a DATE_RULE
     * (issue #77).
     */
    @SuppressWarnings("unchecked")
    private Map<String, DateRule> toDateRules(Object raw) {
        Map<String, DateRule> out = new LinkedHashMap<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> m) addDateRule(out, null, (Map<String, Object>) m);
            }
        } else if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof DateRule dr) {
                    out.put(dr.id(), dr);
                } else if (e.getValue() instanceof Map<?, ?> m) {
                    addDateRule(out, String.valueOf(e.getKey()), (Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    private void addDateRule(Map<String, DateRule> out, String key, Map<String, Object> m) {
        String id = firstNonBlank(str(m.get("ruleId")), str(m.get("id")), key);
        if (id == null) return;
        DateRuleType type = m.get("type") == null
                ? DateRuleType.CURRENT_TIMESTAMP
                : DateRuleType.valueOf(String.valueOf(m.get("type")).trim());
        out.put(id, new DateRule(
                id,
                type,
                str(m.get("sourceNode")),
                num(m.get("sourceTag"), 0).intValue(),
                num(m.get("offsetValue"), 0).longValue(),
                unit(m.get("offsetUnit"), TimeUnit.MILLISECONDS),
                num(m.get("toleranceValue"), 0).longValue(),
                unit(m.get("toleranceUnit"), TimeUnit.SECONDS)));
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) if (c != null && !c.isBlank()) return c;
        return null;
    }

    private static Number num(Object o, long fallback) {
        if (o instanceof Number n) return n;
        if (o != null) {
            try { return Long.parseLong(String.valueOf(o).trim()); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static TimeUnit unit(Object o, TimeUnit fallback) {
        String s = str(o);
        if (s == null) return fallback;
        try { return TimeUnit.valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return fallback; }
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
