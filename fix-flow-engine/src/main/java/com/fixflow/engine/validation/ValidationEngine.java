package com.fixflow.engine.validation;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.validation.rules.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ValidationEngine {

    private final DateRuleEngine dateRuleEngine;

    public ValidationEngine(DateRuleEngine dateRuleEngine) {
        this.dateRuleEngine = dateRuleEngine;
    }

    public ValidationSummary validate(
        ValidationConfig config,
        FIXMessageData message,
        ExecutionContext ctx,
        Instant receivedAt
    ) {
        Map<Integer, String> topLevel = message.flatFields();
        List<ValidationResult> results = new ArrayList<>();
        Set<Integer> expectedTags = new HashSet<>();

        for (ValidationRuleConfig rc : config.validations()) {
            if (rc.groupTag() == null) {
                expectedTags.add(rc.tag());
                results.add(evaluate(rc, topLevel, config, ctx, receivedAt));
                continue;
            }

            List<FIXMessageData> entries = message.group(rc.groupTag());
            if (entries.isEmpty()) {
                results.add(ValidationResult.fail(rc.tag(), rc.rule(), "group " + rc.groupTag() + " present",
                        "absent", "repeating group " + rc.groupTag() + " not found"));
                continue;
            }

            String idx = rc.index() == null ? "0" : rc.index().trim();
            if ("*".equals(idx)) {
                for (FIXMessageData entry : entries) {
                    results.add(evaluate(rc, entry.flatFields(), config, ctx, receivedAt));
                }
            } else {
                Integer i = parseIndex(idx);
                if (i == null) {
                    results.add(ValidationResult.fail(rc.tag(), rc.rule(),
                            "group " + rc.groupTag() + " numeric entry index or '*'",
                            idx, "invalid group entry index"));
                } else if (i < 0 || i >= entries.size()) {
                    results.add(ValidationResult.fail(rc.tag(), rc.rule(),
                            "group " + rc.groupTag() + " entry " + i,
                            entries.size() + " entries", "group entry index out of range"));
                } else {
                    results.add(evaluate(rc, entries.get(i).flatFields(), config, ctx, receivedAt));
                }
            }
        }

        if (config.strictMode()) {
            for (Integer tag : topLevel.keySet()) {
                if (!expectedTags.contains(tag) && !isHeaderTag(tag)) {
                    results.add(ValidationResult.fail(
                        tag, "STRICT", "not present", topLevel.get(tag), "unexpected field"
                    ));
                }
            }
        }

        boolean passed = results.stream().allMatch(ValidationResult::passed);
        return new ValidationSummary(passed, List.copyOf(results));
    }

    /** Legacy flat-map entry point, kept for existing tests and callers. */
    public ValidationSummary validate(ValidationConfig config, Map<Integer, String> fields,
                                      ExecutionContext ctx, Instant receivedAt) {
        return validate(config, FIXMessageData.ofFields(fields), ctx, receivedAt);
    }

    /** Parses a group entry index; returns null (rather than throwing) when it is not a valid integer. */
    private Integer parseIndex(String idx) {
        try {
            return Integer.parseInt(idx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ValidationResult evaluate(ValidationRuleConfig rc, Map<Integer, String> fields,
                                      ValidationConfig config, ExecutionContext ctx, Instant receivedAt) {
        ValidationRule rule = build(rc, config);
        return rule instanceof DateRuleValidator drv
                ? dateRuleEngine.validate(drv.rule(), rc.tag(), fields, ctx, receivedAt)
                : rule.validate(rc.tag(), fields, ctx);
    }

    private boolean isHeaderTag(int tag) {
        return tag == 8 || tag == 9 || tag == 10 || tag == 34 || tag == 35
            || tag == 49 || tag == 52 || tag == 56;
    }

    private ValidationRule build(ValidationRuleConfig rc, ValidationConfig cfg) {
        return switch (rc.rule()) {
            case "EQUALS"        -> new EqualsRule(rc.value(), rc.ref());
            case "NOT_EQUALS"    -> new NotEqualsRule(rc.value(), rc.ref());
            case "ENUM"          -> new EnumRule(rc.values() == null ? List.of() : rc.values());
            case "REGEX"         -> new RegexRule(rc.pattern() == null ? rc.value() : rc.pattern());
            case "NUMERIC_MIN"   -> new NumericMinRule(rc.numericValue());
            case "NUMERIC_MAX"   -> new NumericMaxRule(rc.numericValue());
            case "FIELD_PRESENT" -> new FieldPresentRule();
            case "FIELD_ABSENT"  -> new FieldAbsentRule();
            case "DATE_RULE" -> {
                DateRule dr = cfg.dateRules().get(rc.dateRule());
                if (dr == null) throw new IllegalArgumentException("Unknown dateRule id: " + rc.dateRule());
                yield new DateRuleValidator(dr);
            }
            default -> throw new IllegalArgumentException("Unknown rule type: " + rc.rule());
        };
    }
}
