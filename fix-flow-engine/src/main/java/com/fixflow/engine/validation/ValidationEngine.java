package com.fixflow.engine.validation;

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
        Map<Integer, String> fields,
        ExecutionContext ctx,
        Instant receivedAt
    ) {
        List<ValidationResult> results = new ArrayList<>();
        Set<Integer> expectedTags = new HashSet<>();

        for (ValidationRuleConfig rc : config.validations()) {
            expectedTags.add(rc.tag());
            ValidationRule rule = build(rc, config);
            if (rule instanceof DateRuleValidator drv) {
                results.add(dateRuleEngine.validate(drv.rule(), rc.tag(), fields, ctx, receivedAt));
            } else {
                results.add(rule.validate(rc.tag(), fields, ctx));
            }
        }

        if (config.strictMode()) {
            for (Integer tag : fields.keySet()) {
                if (!expectedTags.contains(tag) && !isHeaderTag(tag)) {
                    results.add(ValidationResult.fail(
                        tag, "STRICT", "not present", fields.get(tag), "unexpected field"
                    ));
                }
            }
        }

        boolean passed = results.stream().allMatch(ValidationResult::passed);
        return new ValidationSummary(passed, List.copyOf(results));
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
