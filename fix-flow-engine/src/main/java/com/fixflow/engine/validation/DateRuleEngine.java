package com.fixflow.engine.validation;

import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class DateRuleEngine {

    public ValidationResult validate(
        DateRule rule,
        int tag,
        Map<Integer, String> fields,
        ExecutionContext ctx,
        Instant messageReceivedAt
    ) {
        String raw = fields.get(tag);
        if (raw == null) {
            return ValidationResult.fail(tag, "DATE_RULE:" + rule.type(),
                "datetime", null, "field missing");
        }
        Instant actual;
        try {
            actual = Instant.parse(raw);
        } catch (Exception e) {
            return ValidationResult.fail(tag, "DATE_RULE:" + rule.type(),
                "iso-8601 datetime", raw, "cannot parse");
        }
        return switch (rule.type()) {
            case CURRENT_TIMESTAMP -> validateCurrentTimestamp(rule, tag, actual, messageReceivedAt);
            case FIELD_OFFSET -> validateFieldOffset(rule, tag, actual, ctx);
        };
    }

    private ValidationResult validateCurrentTimestamp(DateRule rule, int tag, Instant actual, Instant receivedAt) {
        long toleranceMs = rule.toleranceUnit().toMillis(rule.toleranceValue());
        long deltaMs = Math.abs(Duration.between(receivedAt, actual).toMillis());
        if (deltaMs <= toleranceMs) {
            return ValidationResult.pass(tag, "DATE_RULE:CURRENT_TIMESTAMP");
        }
        return ValidationResult.fail(tag, "DATE_RULE:CURRENT_TIMESTAMP",
            "within " + toleranceMs + "ms of " + receivedAt,
            actual.toString(),
            "delta=" + deltaMs + "ms");
    }

    private ValidationResult validateFieldOffset(DateRule rule, int tag, Instant actual, ExecutionContext ctx) {
        Map<Integer, String> srcFields = ctx.getNodeMessage(rule.sourceNode());
        if (srcFields == null) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "source node " + rule.sourceNode(), null, "source node not found");
        }
        String srcRaw = srcFields.get(rule.sourceTag());
        if (srcRaw == null) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "tag " + rule.sourceTag() + " on " + rule.sourceNode(),
                null, "source tag missing");
        }
        Instant srcInstant;
        try {
            srcInstant = Instant.parse(srcRaw);
        } catch (Exception e) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "iso-8601", srcRaw, "source not parseable");
        }
        long offsetMs = rule.offsetUnit().toMillis(rule.offsetValue());
        Instant expected = srcInstant.plusMillis(offsetMs);
        long toleranceMs = rule.toleranceUnit().toMillis(rule.toleranceValue());
        long deltaMs = Math.abs(Duration.between(expected, actual).toMillis());
        if (deltaMs <= toleranceMs) {
            return ValidationResult.pass(tag, "DATE_RULE:FIELD_OFFSET");
        }
        return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
            "within " + toleranceMs + "ms of " + expected,
            actual.toString(),
            "delta=" + deltaMs + "ms");
    }
}
