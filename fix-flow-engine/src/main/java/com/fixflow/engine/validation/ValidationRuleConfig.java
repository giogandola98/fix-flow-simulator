package com.fixflow.engine.validation;

import java.util.List;

public record ValidationRuleConfig(
    int tag,
    String rule,
    String value,
    List<String> values,
    String ref,
    String dateRule,
    String pattern,
    double numericValue,
    /** Repeating group counter tag, or null to validate a top-level field. */
    Integer groupTag,
    /** Group entry index as a string; "*" means every entry. Null defaults to "0". */
    String index
) {
    /** Legacy 8-arg form used by pre-existing callers and tests. */
    public ValidationRuleConfig(int tag, String rule, String value, List<String> values,
                                String ref, String dateRule, String pattern, double numericValue) {
        this(tag, rule, value, values, ref, dateRule, pattern, numericValue, null, null);
    }
}
