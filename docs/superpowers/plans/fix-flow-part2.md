# FIX Flow Simulator Implementation Plan — Part 2 of 3

*Tasks 19-33, Phases 6-9: Validation, Variable Resolution, Advanced Nodes, HotReload, REST API, WebSocket*

---

## Phase 6: ValidationEngine + DateRuleEngine + VariableResolver (Tasks 19-23)

---

### Task 19: VariableResolver

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolver.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolverPlugin.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/variable/VariableResolverTest.java`

#### Step 1: Write failing test

`fix-flow-engine/src/test/java/com/fixflow/engine/variable/VariableResolverTest.java`:

```java
package com.fixflow.engine.variable;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class VariableResolverTest {

    private VariableResolver resolver;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver();
        ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void resolvesNowAsValidIsoInstant() {
        String out = resolver.resolveAll("{{now}}", ctx);
        Instant parsed = Instant.parse(out);
        assertThat(parsed).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void resolvesUuidAsValidUuid() {
        String out = resolver.resolveAll("{{uuid}}", ctx);
        UUID parsed = UUID.fromString(out);
        assertThat(parsed).isNotNull();
    }

    @Test
    void resolvesSeqIncrementing() {
        String first = resolver.resolveAll("{{seq:orders}}", ctx);
        String second = resolver.resolveAll("{{seq:orders}}", ctx);
        assertThat(first).isEqualTo("1");
        assertThat(second).isEqualTo("2");
    }

    @Test
    void resolvesEnvVariable() {
        String out = resolver.resolveAll("{{env:HOME}}", ctx);
        assertThat(out).isNotNull().isNotBlank();
    }

    @Test
    void resolvesNodeFieldReference() {
        Map<Integer, String> fields = new HashMap<>();
        fields.put(131, "QR-12345");
        ctx.storeNodeMessage("n1", new FIXMessage("R", fields));
        String out = resolver.resolveAll("{{node:n1:tag131}}", ctx);
        assertThat(out).isEqualTo("QR-12345");
    }

    @Test
    void resolvesDateOffsetPlusFiveMinutes() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> fields = new HashMap<>();
        fields.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", fields));
        String out = resolver.resolveAll("{{node:n1:tag60:offset:+5m}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isEqualTo(base.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void resolvesDateOffsetMinusOneHour() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> fields = new HashMap<>();
        fields.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", fields));
        String out = resolver.resolveAll("{{node:n1:tag60:offset:-1h}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isEqualTo(base.minus(1, ChronoUnit.HOURS));
    }

    @Test
    void resolvesMultipleVariablesInTemplate() {
        Map<Integer, String> fields = new HashMap<>();
        fields.put(131, "QR-1");
        ctx.storeNodeMessage("n1", new FIXMessage("R", fields));
        String out = resolver.resolveAll("ID={{node:n1:tag131}};TS={{now}}", ctx);
        assertThat(out).startsWith("ID=QR-1;TS=");
        assertThat(Pattern.matches("ID=QR-1;TS=.+Z", out)).isTrue();
    }

    private static org.assertj.core.api.InstantAssert within(long amount, ChronoUnit unit) {
        return null; // unused, see assertThat usage
    }
}
```

Note: use `assertThat(parsed).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))` from AssertJ's `Assertions.within`. Replace the placeholder method with `import static org.assertj.core.api.Assertions.within;`.

Run — expect compile failure (no VariableResolver yet).

#### Step 2: Plugin interface

`fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolverPlugin.java`:

```java
package com.fixflow.engine.variable;

import com.fixflow.core.execution.ExecutionContext;

public interface VariableResolverPlugin {
    boolean supports(String expression);
    String resolve(String expression, ExecutionContext ctx);
}
```

#### Step 3: VariableResolver implementation

`fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolver.java`:

```java
package com.fixflow.engine.variable;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VariableResolver {

    private static final Pattern EXPR = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final List<VariableResolverPlugin> plugins;
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public VariableResolver() {
        this.plugins = List.of(
            new NowPlugin(),
            new UuidPlugin(),
            new SeqPlugin(sequences),
            new EnvPlugin(),
            new DateOffsetPlugin(),
            new NodeFieldPlugin()
        );
    }

    public String resolveAll(String template, ExecutionContext ctx) {
        if (template == null) return null;
        Matcher m = EXPR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String value = dispatch(expr, ctx);
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String dispatch(String expression, ExecutionContext ctx) {
        for (VariableResolverPlugin p : plugins) {
            if (p.supports(expression)) {
                return p.resolve(expression, ctx);
            }
        }
        throw new IllegalArgumentException("No plugin handles expression: " + expression);
    }

    // ----- Built-in plugins -----

    static final class NowPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("now"); }
        public String resolve(String e, ExecutionContext c) { return Instant.now().toString(); }
    }

    static final class UuidPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("uuid"); }
        public String resolve(String e, ExecutionContext c) { return UUID.randomUUID().toString(); }
    }

    static final class SeqPlugin implements VariableResolverPlugin {
        private final ConcurrentHashMap<String, AtomicLong> sequences;
        SeqPlugin(ConcurrentHashMap<String, AtomicLong> s) { this.sequences = s; }
        public boolean supports(String e) { return e.startsWith("seq:"); }
        public String resolve(String e, ExecutionContext c) {
            String name = e.substring("seq:".length());
            return Long.toString(sequences.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet());
        }
    }

    static final class EnvPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.startsWith("env:"); }
        public String resolve(String e, ExecutionContext c) {
            String var = e.substring("env:".length());
            String value = System.getenv(var);
            return value == null ? "" : value;
        }
    }

    static final class DateOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile(
            "^node:([^:]+):tag(\\d+):offset:([+\\-])(\\d+)([smhd])$"
        );

        public boolean supports(String e) { return P.matcher(e).matches(); }

        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad date offset: " + e);
            String nodeId = m.group(1);
            int tag = Integer.parseInt(m.group(2));
            String sign = m.group(3);
            long amount = Long.parseLong(m.group(4));
            String unit = m.group(5);
            FIXMessage msg = c.getNodeMessage(nodeId);
            if (msg == null) throw new IllegalStateException("No stored message for node: " + nodeId);
            String raw = msg.fields().get(tag);
            if (raw == null) throw new IllegalStateException("No tag " + tag + " on node " + nodeId);
            Instant base = Instant.parse(raw);
            ChronoUnit cu = switch (unit) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default -> throw new IllegalArgumentException("Bad unit: " + unit);
            };
            Instant result = sign.equals("+") ? base.plus(amount, cu) : base.minus(amount, cu);
            return result.toString();
        }
    }

    static final class NodeFieldPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^node:([^:]+):tag(\\d+)$");

        public boolean supports(String e) { return P.matcher(e).matches(); }

        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad node ref: " + e);
            String nodeId = m.group(1);
            int tag = Integer.parseInt(m.group(2));
            FIXMessage msg = c.getNodeMessage(nodeId);
            if (msg == null) throw new IllegalStateException("No stored message for node: " + nodeId);
            String v = msg.fields().get(tag);
            return v == null ? "" : v;
        }
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=VariableResolverTest
```

Expected: all pass.

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/variable/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/variable/
git commit -m "feat(engine): add VariableResolver with built-in plugins (now, uuid, seq, env, node, date-offset)"
```

---

### Task 20: ValidationRule interface + basic rules

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationResult.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/EqualsRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/NotEqualsRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/EnumRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/RegexRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/NumericMinRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/NumericMaxRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/FieldPresentRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/FieldAbsentRule.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationRulesTest.java`

#### Step 1: Write failing tests

`fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationRulesTest.java`:

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    private final ExecutionContext ctx = new ExecutionContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @Test
    void equalsRulePassesWhenValueMatches() {
        EqualsRule rule = new EqualsRule("S", null);
        ValidationResult r = rule.validate(35, Map.of(35, "S"), ctx);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void equalsRuleFailsWhenValueDiffers() {
        EqualsRule rule = new EqualsRule("S", null);
        ValidationResult r = rule.validate(35, Map.of(35, "D"), ctx);
        assertThat(r.passed()).isFalse();
        assertThat(r.expected()).isEqualTo("S");
        assertThat(r.actual()).isEqualTo("D");
    }

    @Test
    void enumRulePassesWhenInList() {
        EnumRule rule = new EnumRule(List.of("1", "2", "3"));
        assertThat(rule.validate(39, Map.of(39, "2"), ctx).passed()).isTrue();
    }

    @Test
    void enumRuleFailsWhenNotInList() {
        EnumRule rule = new EnumRule(List.of("1", "2", "3"));
        assertThat(rule.validate(39, Map.of(39, "9"), ctx).passed()).isFalse();
    }

    @Test
    void regexRulePassesWhenPatternMatches() {
        RegexRule rule = new RegexRule("^ORD-\\d+$");
        assertThat(rule.validate(11, Map.of(11, "ORD-123"), ctx).passed()).isTrue();
    }

    @Test
    void regexRuleFailsWhenPatternDoesNotMatch() {
        RegexRule rule = new RegexRule("^ORD-\\d+$");
        assertThat(rule.validate(11, Map.of(11, "X"), ctx).passed()).isFalse();
    }

    @Test
    void numericMinRulePassesWhenAboveMin() {
        NumericMinRule rule = new NumericMinRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "200"), ctx).passed()).isTrue();
    }

    @Test
    void numericMinRuleFailsWhenBelowMin() {
        NumericMinRule rule = new NumericMinRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "50"), ctx).passed()).isFalse();
    }

    @Test
    void numericMaxRulePassesWhenBelowMax() {
        NumericMaxRule rule = new NumericMaxRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "50"), ctx).passed()).isTrue();
    }

    @Test
    void numericMaxRuleFailsWhenAboveMax() {
        NumericMaxRule rule = new NumericMaxRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "150"), ctx).passed()).isFalse();
    }

    @Test
    void fieldPresentPassesWhenFieldExists() {
        assertThat(new FieldPresentRule().validate(131, Map.of(131, "X"), ctx).passed()).isTrue();
    }

    @Test
    void fieldPresentFailsWhenFieldMissing() {
        assertThat(new FieldPresentRule().validate(131, Map.of(), ctx).passed()).isFalse();
    }

    @Test
    void fieldAbsentPassesWhenFieldMissing() {
        assertThat(new FieldAbsentRule().validate(999, Map.of(), ctx).passed()).isTrue();
    }

    @Test
    void fieldAbsentFailsWhenFieldPresent() {
        assertThat(new FieldAbsentRule().validate(999, Map.of(999, "X"), ctx).passed()).isFalse();
    }

    @Test
    void notEqualsRulePassesWhenValuesDiffer() {
        assertThat(new NotEqualsRule("D", null).validate(35, Map.of(35, "S"), ctx).passed()).isTrue();
    }

    @Test
    void notEqualsRuleFailsWhenValuesMatch() {
        assertThat(new NotEqualsRule("D", null).validate(35, Map.of(35, "D"), ctx).passed()).isFalse();
    }
}
```

Run — expect compile failures.

#### Step 2: ValidationRule + ValidationResult

`fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRule.java`:

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;

import java.util.Map;

public interface ValidationRule {
    ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx);
}
```

`fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationResult.java`:

```java
package com.fixflow.engine.validation;

public record ValidationResult(
    boolean passed,
    int tag,
    String ruleName,
    String expected,
    String actual,
    String message
) {
    public static ValidationResult pass(int tag, String ruleName) {
        return new ValidationResult(true, tag, ruleName, null, null, null);
    }

    public static ValidationResult fail(int tag, String ruleName, String expected, String actual, String message) {
        return new ValidationResult(false, tag, ruleName, expected, actual, message);
    }
}
```

#### Step 3: Rule implementations

`EqualsRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class EqualsRule implements ValidationRule {
    private final String expected;
    private final String refExpression;

    public EqualsRule(String expected, String refExpression) {
        this.expected = expected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String target = expected;
        if (refExpression != null && ctx != null) {
            // ref expressions like "node:n1:tag131" are resolved upstream; here we accept literal
            target = refExpression;
        }
        if (target != null && target.equals(actual)) {
            return ValidationResult.pass(tag, "EQUALS");
        }
        return ValidationResult.fail(tag, "EQUALS", target, actual, "values differ");
    }
}
```

`NotEqualsRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NotEqualsRule implements ValidationRule {
    private final String unexpected;
    private final String refExpression;

    public NotEqualsRule(String unexpected, String refExpression) {
        this.unexpected = unexpected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String target = refExpression != null ? refExpression : unexpected;
        if (target == null || !target.equals(actual)) {
            return ValidationResult.pass(tag, "NOT_EQUALS");
        }
        return ValidationResult.fail(tag, "NOT_EQUALS", "!= " + target, actual, "values must differ");
    }
}
```

`EnumRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.List;
import java.util.Map;

public final class EnumRule implements ValidationRule {
    private final List<String> allowed;

    public EnumRule(List<String> allowed) {
        this.allowed = List.copyOf(allowed);
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual != null && allowed.contains(actual)) {
            return ValidationResult.pass(tag, "ENUM");
        }
        return ValidationResult.fail(tag, "ENUM", allowed.toString(), actual, "value not in allowed set");
    }
}
```

`RegexRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;
import java.util.regex.Pattern;

public final class RegexRule implements ValidationRule {
    private final Pattern pattern;
    private final String raw;

    public RegexRule(String pattern) {
        this.pattern = Pattern.compile(pattern);
        this.raw = pattern;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual != null && pattern.matcher(actual).matches()) {
            return ValidationResult.pass(tag, "REGEX");
        }
        return ValidationResult.fail(tag, "REGEX", raw, actual, "value does not match pattern");
    }
}
```

`NumericMinRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NumericMinRule implements ValidationRule {
    private final double min;

    public NumericMinRule(double min) { this.min = min; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual == null) {
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, null, "missing");
        }
        try {
            double v = Double.parseDouble(actual);
            if (v >= min) return ValidationResult.pass(tag, "NUMERIC_MIN");
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, actual, "below minimum");
        } catch (NumberFormatException e) {
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, actual, "not numeric");
        }
    }
}
```

`NumericMaxRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NumericMaxRule implements ValidationRule {
    private final double max;

    public NumericMaxRule(double max) { this.max = max; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual == null) {
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, null, "missing");
        }
        try {
            double v = Double.parseDouble(actual);
            if (v <= max) return ValidationResult.pass(tag, "NUMERIC_MAX");
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, actual, "above maximum");
        } catch (NumberFormatException e) {
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, actual, "not numeric");
        }
    }
}
```

`FieldPresentRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class FieldPresentRule implements ValidationRule {
    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        if (fields.containsKey(tag)) return ValidationResult.pass(tag, "FIELD_PRESENT");
        return ValidationResult.fail(tag, "FIELD_PRESENT", "present", "absent", "required field missing");
    }
}
```

`FieldAbsentRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class FieldAbsentRule implements ValidationRule {
    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        if (!fields.containsKey(tag)) return ValidationResult.pass(tag, "FIELD_ABSENT");
        return ValidationResult.fail(tag, "FIELD_ABSENT", "absent", fields.get(tag), "field must not be present");
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=ValidationRulesTest
```

Expected: all pass.

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/
git commit -m "feat(engine): add ValidationRule interface and 8 built-in rules"
```

---

### Task 21: DateRuleEngine

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/DateRuleEngine.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/DateRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/DateRuleType.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/DateRuleValidator.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/DateRuleEngineTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DateRuleEngineTest {

    private final DateRuleEngine engine = new DateRuleEngine();
    private final ExecutionContext ctx = new ExecutionContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @Test
    void currentTimestampPassesWhenWithinTolerance() {
        Instant now = Instant.now();
        DateRule rule = new DateRule("ct", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0, TimeUnit.SECONDS, 4, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(60, now.toString());
        ValidationResult r = engine.validate(rule, 60, fields, ctx, now);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void currentTimestampFailsWhenOutsideTolerance() {
        Instant now = Instant.now();
        Instant tenMinAgo = now.minus(10, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("ct", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0, TimeUnit.SECONDS, 4, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(60, tenMinAgo.toString());
        ValidationResult r = engine.validate(rule, 60, fields, ctx, now);
        assertThat(r.passed()).isFalse();
    }

    @Test
    void fieldOffsetPassesWhenOffsetMatches() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> source = new HashMap<>();
        source.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", source));
        Instant target = base.plus(5, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("fo", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(126, target.toString());
        ValidationResult r = engine.validate(rule, 126, fields, ctx, Instant.now());
        assertThat(r.passed()).isTrue();
    }

    @Test
    void fieldOffsetFailsWhenOffsetTooLarge() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> source = new HashMap<>();
        source.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", source));
        Instant target = base.plus(10, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("fo", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(126, target.toString());
        ValidationResult r = engine.validate(rule, 126, fields, ctx, Instant.now());
        assertThat(r.passed()).isFalse();
    }
}
```

#### Step 2: DateRuleType + DateRule

`DateRuleType.java`:

```java
package com.fixflow.engine.validation;

public enum DateRuleType {
    CURRENT_TIMESTAMP,
    FIELD_OFFSET
}
```

`DateRule.java`:

```java
package com.fixflow.engine.validation;

import java.util.concurrent.TimeUnit;

public record DateRule(
    String id,
    DateRuleType type,
    String sourceNode,
    int sourceTag,
    long offsetValue,
    TimeUnit offsetUnit,
    long toleranceValue,
    TimeUnit toleranceUnit
) {}
```

#### Step 3: DateRuleEngine implementation

`DateRuleEngine.java`:

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
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
        FIXMessage src = ctx.getNodeMessage(rule.sourceNode());
        if (src == null) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "source node " + rule.sourceNode(), null, "source node not found");
        }
        String srcRaw = src.fields().get(rule.sourceTag());
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
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=DateRuleEngineTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/DateRuleEngineTest.java
git commit -m "feat(engine): add DateRuleEngine with CURRENT_TIMESTAMP and FIELD_OFFSET rule types"
```

---

### Task 22: ValidationEngine (orchestrates all rules)

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationEngine.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationConfig.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRuleConfig.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationSummary.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationEngineTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationEngineTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());
    private final ExecutionContext ctx = new ExecutionContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @Test
    void passesWhenAllRulesPass() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0),
                new ValidationRuleConfig(131, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "S", 131, "QR-1");
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void failsInStrictModeWhenUnexpectedTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0),
                new ValidationRuleConfig(131, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            true
        );
        Map<Integer, String> fields = Map.of(35, "S", 131, "QR-1", 999, "EXTRA");
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isFalse();
        assertThat(s.results()).anyMatch(r -> !r.passed() && r.tag() == 999);
    }

    @Test
    void passesInNonStrictModeWhenExtraTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0)
            ),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "S", 999, "EXTRA");
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void appliesDateRuleFromConfig() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> source = new HashMap<>();
        source.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", source));
        DateRule fo = new DateRule("fo1", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(126, "DATE_RULE", null, null, null, "fo1", null, 0)),
            Map.of("fo1", fo),
            false
        );
        Map<Integer, String> fields = Map.of(126, base.plusSeconds(300).toString());
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }
}
```

#### Step 2: ValidationConfig + ValidationRuleConfig + ValidationSummary

`ValidationRuleConfig.java`:

```java
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
    double numericValue
) {}
```

`ValidationConfig.java`:

```java
package com.fixflow.engine.validation;

import java.util.List;
import java.util.Map;

public record ValidationConfig(
    List<ValidationRuleConfig> validations,
    Map<String, DateRule> dateRules,
    boolean strictMode
) {}
```

`ValidationSummary.java`:

```java
package com.fixflow.engine.validation;

import java.util.List;

public record ValidationSummary(boolean passed, List<ValidationResult> results) {}
```

#### Step 3: ValidationEngine implementation

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
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
            case "EQUALS" -> new EqualsRule(rc.value(), rc.ref());
            case "NOT_EQUALS" -> new NotEqualsRule(rc.value(), rc.ref());
            case "ENUM" -> new EnumRule(rc.values() == null ? List.of() : rc.values());
            case "REGEX" -> new RegexRule(rc.pattern() == null ? rc.value() : rc.pattern());
            case "NUMERIC_MIN" -> new NumericMinRule(rc.numericValue());
            case "NUMERIC_MAX" -> new NumericMaxRule(rc.numericValue());
            case "FIELD_PRESENT" -> new FieldPresentRule();
            case "FIELD_ABSENT" -> new FieldAbsentRule();
            case "DATE_RULE" -> {
                DateRule dr = cfg.dateRules().get(rc.dateRule());
                if (dr == null) throw new IllegalArgumentException("Unknown dateRule id: " + rc.dateRule());
                yield new DateRuleValidator(dr);
            }
            default -> throw new IllegalArgumentException("Unknown rule type: " + rc.rule());
        };
    }
}
```

`fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/DateRuleValidator.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.DateRule;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class DateRuleValidator implements ValidationRule {
    private final DateRule rule;

    public DateRuleValidator(DateRule rule) { this.rule = rule; }

    public DateRule rule() { return rule; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        throw new UnsupportedOperationException("DateRuleValidator must be dispatched via DateRuleEngine");
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=ValidationEngineTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationEngineTest.java
git commit -m "feat(engine): add ValidationEngine orchestrating rules, date rules, and strict mode"
```

---

### Task 23: ValidateHandler + wire VariableResolver into SendFIXHandler

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ValidateHandler.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/SendFIXHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/ValidateHandlerTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
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

    @Test
    void returnsOnSuccessWhenAllRulesPass() {
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("v1", new FIXMessage("8", Map.of(35, "8", 39, "2")));
        ScenarioNode node = new ScenarioNode(
            "v1", NodeType.VALIDATE, "validate",
            Map.of("validations", List.of(
                Map.of("tag", 35, "rule", "EQUALS", "value", "8"),
                Map.of("tag", 39, "rule", "EQUALS", "value", "2")
            )),
            null, null, null, null, null, "next", "fail"
        );
        NodeHandlerResult r = handler.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void returnsOnFailureWhenRuleFails() {
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("v1", new FIXMessage("8", Map.of(35, "8", 39, "1")));
        ScenarioNode node = new ScenarioNode(
            "v1", NodeType.VALIDATE, "validate",
            Map.of("validations", List.of(
                Map.of("tag", 39, "rule", "EQUALS", "value", "2")
            )),
            null, null, null, null, null, "next", "fail"
        );
        NodeHandlerResult r = handler.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("fail");
    }
}
```

#### Step 2: ValidateHandler implementation

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
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
    public NodeType supports() { return NodeType.VALIDATE; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        FIXMessage msg = ctx.getNodeMessage(node.id());
        Map<Integer, String> fields = msg == null ? Map.of() : msg.fields();

        ValidationConfig cfg = toConfig(node.config());
        ValidationSummary summary = engine.validate(cfg, fields, ctx, Instant.now());
        ctx.storeValidationSummary(node.id(), summary);

        return summary.passed()
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "validation failed");
    }

    @SuppressWarnings("unchecked")
    private ValidationConfig toConfig(Map<String, Object> raw) {
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) raw.getOrDefault("validations", List.of());
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
            rules.add(new ValidationRuleConfig(tag, rule, value, values, ref, dateRule, pattern, num));
        }
        boolean strict = Boolean.TRUE.equals(raw.get("strictMode"));
        Map<String, DateRule> dateRules = (Map<String, DateRule>) raw.getOrDefault("dateRules", Map.of());
        return new ValidationConfig(rules, dateRules, strict);
    }
}
```

#### Step 3: Update SendFIXHandler to resolve variables

Modify `SendFIXHandler.java` — inject `VariableResolver` and resolve each field value template:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.core.ports.FIXSessionPort;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SendFIXHandler implements NodeHandler {

    private final FIXSessionPort sessionPort;
    private final VariableResolver variableResolver;

    public SendFIXHandler(FIXSessionPort sessionPort, VariableResolver variableResolver) {
        this.sessionPort = sessionPort;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType supports() { return NodeType.SEND_FIX; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        Map<String, Object> cfg = node.config();
        String msgType = (String) cfg.get("msgType");
        @SuppressWarnings("unchecked")
        Map<String, Object> rawFields = (Map<String, Object>) cfg.getOrDefault("fields", Map.of());

        Map<Integer, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : rawFields.entrySet()) {
            int tag = Integer.parseInt(e.getKey());
            String template = String.valueOf(e.getValue());
            String value = variableResolver.resolveAll(template, ctx);
            resolved.put(tag, value);
        }

        FIXMessage msg = new FIXMessage(msgType, resolved);
        sessionPort.send(ctx.sessionId(), msg);
        ctx.storeNodeMessage(node.id(), msg);
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=ValidateHandlerTest
mvn test -pl fix-flow-engine -Dtest=SendFIXHandlerTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/ValidateHandlerTest.java
git commit -m "feat(engine): add ValidateHandler and wire VariableResolver into SendFIXHandler"
```

---

## Phase 7: Advanced Node Types (Tasks 24-26)

---

### Task 24: DecisionHandler + WaitHandler + DelayHandler

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DecisionHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/WaitHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DelayHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/DecisionHandlerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.domain.TimeoutConfig;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHandlerTest {

    private final VariableResolver resolver = new VariableResolver();

    @Test
    void decisionGoesOnSuccessWhenConditionTrue() {
        DecisionHandler h = new DecisionHandler(resolver);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("n1", new FIXMessage("8", Map.of(39, "2")));
        ScenarioNode node = new ScenarioNode("d", NodeType.DECISION, "decide",
            Map.of("condition", "{{node:n1:tag39}} == 2"),
            null, null, null, null, null, "yes", "no");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("yes");
    }

    @Test
    void decisionGoesOnFailureWhenConditionFalse() {
        DecisionHandler h = new DecisionHandler(resolver);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("n1", new FIXMessage("8", Map.of(39, "1")));
        ScenarioNode node = new ScenarioNode("d", NodeType.DECISION, "decide",
            Map.of("condition", "{{node:n1:tag39}} == 2"),
            null, null, null, null, null, "yes", "no");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("no");
    }

    @Test
    void waitBlocksForConfiguredDuration() {
        WaitHandler h = new WaitHandler();
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        TimeoutConfig t = new TimeoutConfig(50, TimeUnit.MILLISECONDS);
        ScenarioNode node = new ScenarioNode("w", NodeType.WAIT, "wait",
            Map.of(), t, null, null, null, null, "next", "fail");
        long start = System.nanoTime();
        NodeHandlerResult r = h.execute(node, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(50L);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void delayBlocksForConfiguredMs() {
        DelayHandler h = new DelayHandler();
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("d", NodeType.DELAY, "delay",
            Map.of("delayMs", 50), null, null, null, null, null, "next", "fail");
        long start = System.nanoTime();
        NodeHandlerResult r = h.execute(node, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(50L);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }
}
```

#### Step 2: DecisionHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DecisionHandler implements NodeHandler {

    private static final Pattern COND = Pattern.compile(
        "^\\s*(.+?)\\s*(==|!=|contains)\\s*(.+?)\\s*$"
    );

    private final VariableResolver resolver;

    public DecisionHandler(VariableResolver resolver) { this.resolver = resolver; }

    @Override
    public NodeType supports() { return NodeType.DECISION; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        String condition = (String) node.config().get("condition");
        if (condition == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing condition");
        }
        String resolvedCond = resolver.resolveAll(condition, ctx);
        boolean result = evaluate(resolvedCond);
        return result
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "condition false");
    }

    private boolean evaluate(String expr) {
        Matcher m = COND.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported condition: " + expr);
        }
        String left = m.group(1).trim();
        String op = m.group(2);
        String right = m.group(3).trim();
        left = unquote(left);
        right = unquote(right);
        return switch (op) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "contains" -> left.contains(right);
            default -> throw new IllegalArgumentException("Unknown operator: " + op);
        };
    }

    private String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
```

#### Step 3: WaitHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.domain.TimeoutConfig;
import com.fixflow.core.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class WaitHandler implements NodeHandler {

    @Override
    public NodeType supports() { return NodeType.WAIT; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        TimeoutConfig t = node.timeout();
        long ms = t == null ? 0L : t.unit().toMillis(t.value());
        try {
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeHandlerResult.failure(node.onFailure(), "wait interrupted");
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 4: DelayHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class DelayHandler implements NodeHandler {

    @Override
    public NodeType supports() { return NodeType.DELAY; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        Object raw = node.config().get("delayMs");
        long ms = raw == null ? 0L : ((Number) raw).longValue();
        try {
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeHandlerResult.failure(node.onFailure(), "delay interrupted");
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 5: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=DecisionHandlerTest
```

#### Step 6: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DecisionHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/WaitHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DelayHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/DecisionHandlerTest.java
git commit -m "feat(engine): add Decision/Wait/Delay node handlers"
```

---

### Task 25: RetryHandler + LoopHandler

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RetryHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/LoopHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RetryHandlerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.RetryPolicy;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.NodeDispatcher;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryHandlerTest {

    @Test
    void succeedsBeforeMaxAttempts() {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch(eq("inner"), any())).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            return n < 3 ? NodeHandlerResult.failure("retry", "fail")
                          : NodeHandlerResult.success("after");
        });
        RetryHandler h = new RetryHandler(dispatcher);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("r", NodeType.RETRY, "retry",
            Map.of("targetNodeId", "inner", "delayMs", 1),
            null, new RetryPolicy(3, 1L), null, null, null, "ok", "ko");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void failsWhenExceedsMaxAttempts() {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        when(dispatcher.dispatch(eq("inner"), any()))
            .thenReturn(NodeHandlerResult.failure("retry", "x"));
        RetryHandler h = new RetryHandler(dispatcher);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("r", NodeType.RETRY, "retry",
            Map.of("targetNodeId", "inner", "delayMs", 1),
            null, new RetryPolicy(2, 1L), null, null, null, "ok", "ko");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("ko");
    }

    @Test
    void loopRunsTargetTheConfiguredNumberOfTimes() {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch(eq("body"), any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return NodeHandlerResult.success("body");
        });
        LoopHandler h = new LoopHandler(dispatcher);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("l", NodeType.LOOP, "loop",
            Map.of("targetNodeId", "body", "iterations", 4),
            null, null, null, null, null, "done", "fail");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(4);
    }
}
```

#### Step 2: RetryHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.RetryPolicy;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.NodeDispatcher;
import org.springframework.stereotype.Component;

@Component
public class RetryHandler implements NodeHandler {

    private final NodeDispatcher dispatcher;

    public RetryHandler(NodeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public NodeType supports() { return NodeType.RETRY; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        RetryPolicy policy = node.retryPolicy() == null
            ? new RetryPolicy(1, 0L)
            : node.retryPolicy();
        long delayMs = node.config().get("delayMs") == null
            ? policy.delayMs()
            : ((Number) node.config().get("delayMs")).longValue();
        int max = policy.maxAttempts();
        NodeHandlerResult last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            last = dispatcher.dispatch(targetId, ctx);
            if (last.success()) {
                return NodeHandlerResult.success(node.onSuccess());
            }
            if (attempt < max && delayMs > 0) {
                try { Thread.sleep(delayMs); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return NodeHandlerResult.failure(node.onFailure(), "interrupted");
                }
            }
        }
        return NodeHandlerResult.failure(node.onFailure(),
            "exhausted retries: " + (last == null ? "no attempts" : last.reason()));
    }
}
```

#### Step 3: LoopHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.NodeDispatcher;
import org.springframework.stereotype.Component;

@Component
public class LoopHandler implements NodeHandler {

    private final NodeDispatcher dispatcher;

    public LoopHandler(NodeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public NodeType supports() { return NodeType.LOOP; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        Object rawIter = node.config().get("iterations");
        int iterations = rawIter == null ? 1 : ((Number) rawIter).intValue();
        for (int i = 0; i < iterations; i++) {
            ctx.setLoopIndex(node.id(), i);
            NodeHandlerResult r = dispatcher.dispatch(targetId, ctx);
            if (!r.success()) {
                return NodeHandlerResult.failure(node.onFailure(),
                    "loop iteration " + i + " failed: " + r.reason());
            }
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=RetryHandlerTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RetryHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/LoopHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RetryHandlerTest.java
git commit -m "feat(engine): add Retry and Loop node handlers with NodeDispatcher delegation"
```

---

### Task 26: HotReloadService

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/HotReloadService.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/HotReloadServiceTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import com.fixflow.engine.MessageBuffer;
import com.fixflow.engine.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HotReloadServiceTest {

    @Test
    void pausesBufferReloadsRegistryThenResumes() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        MessageBuffer buffer = mock(MessageBuffer.class);
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        UUID scenarioId = UUID.randomUUID();
        Scenario latest = mock(Scenario.class);
        when(repo.findById(scenarioId)).thenReturn(Optional.of(latest));

        HotReloadService svc = new HotReloadService(registry, buffer, repo);
        svc.reload(scenarioId);

        var inOrder = inOrder(buffer, registry);
        inOrder.verify(buffer).pause();
        inOrder.verify(registry).reload(latest);
        inOrder.verify(buffer).resume();
    }

    @Test
    void resumesBufferEvenIfReloadThrows() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        MessageBuffer buffer = mock(MessageBuffer.class);
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        UUID scenarioId = UUID.randomUUID();
        Scenario latest = mock(Scenario.class);
        when(repo.findById(scenarioId)).thenReturn(Optional.of(latest));
        doThrow(new RuntimeException("boom")).when(registry).reload(any());

        HotReloadService svc = new HotReloadService(registry, buffer, repo);
        try { svc.reload(scenarioId); } catch (RuntimeException expected) {}

        verify(buffer).pause();
        verify(buffer).resume();
    }
}
```

#### Step 2: HotReloadService

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import com.fixflow.engine.MessageBuffer;
import com.fixflow.engine.ScenarioRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HotReloadService {

    private final ScenarioRegistry registry;
    private final MessageBuffer buffer;
    private final ScenarioRepositoryPort scenarioRepo;

    public HotReloadService(ScenarioRegistry registry, MessageBuffer buffer, ScenarioRepositoryPort scenarioRepo) {
        this.registry = registry;
        this.buffer = buffer;
        this.scenarioRepo = scenarioRepo;
    }

    public void reload(UUID scenarioId) {
        buffer.pause();
        try {
            Scenario latest = scenarioRepo.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("scenario not found: " + scenarioId));
            registry.reload(latest);
        } finally {
            buffer.resume();
        }
    }
}
```

#### Step 3: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=HotReloadServiceTest
```

#### Step 4: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/fix/HotReloadService.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/fix/HotReloadServiceTest.java
git commit -m "feat(engine): add HotReloadService with buffer pause/resume around registry reload"
```

---

## Phase 8: REST API + WebSocket STOMP (Tasks 27-33)

---

### Task 27: Spring Boot app entry point + WebSocket config

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/WebSocketConfig.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/AppConfig.java`
- Create: `fix-flow-api/src/main/resources/application.yml`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/FixFlowApplicationTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FixFlowApplicationTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextLoads() {
        assertThat(ctx).isNotNull();
    }
}
```

#### Step 2: application.yml

```yaml
spring:
  application:
    name: fix-flow
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:h2:file:./data/fixflow;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ''
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

server:
  port: 8080

logging:
  level:
    com.fixflow: DEBUG
```

#### Step 3: FixFlowApplication

```java
package com.fixflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.fixflow")
public class FixFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixFlowApplication.class, args);
    }
}
```

#### Step 4: WebSocketConfig

```java
package com.fixflow.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

#### Step 5: AppConfig

```java
package com.fixflow.api.config;

import com.fixflow.adapters.persistence.ExecutionRepositoryAdapter;
import com.fixflow.adapters.persistence.ScenarioRepositoryAdapter;
import com.fixflow.adapters.quickfix.QuickFIXAdapter;
import com.fixflow.core.ports.ExecutionRepositoryPort;
import com.fixflow.core.ports.FIXSessionPort;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ScenarioRepositoryPort scenarioRepositoryPort(ScenarioRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public ExecutionRepositoryPort executionRepositoryPort(ExecutionRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public FIXSessionPort fixSessionPort(QuickFIXAdapter adapter) {
        return adapter;
    }
}
```

#### Step 6: Run test

```bash
mvn test -pl fix-flow-api -Dtest=FixFlowApplicationTest
```

#### Step 7: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java \
        fix-flow-api/src/main/java/com/fixflow/api/config/ \
        fix-flow-api/src/main/resources/application.yml \
        fix-flow-api/src/test/java/com/fixflow/api/FixFlowApplicationTest.java
git commit -m "feat(api): bootstrap Spring Boot app with WebSocket STOMP and adapter wiring"
```

---

### Task 28: StompEventPublisher

**Files:**
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/events/StompEventPublisher.java`
- Test: `fix-flow-adapters/src/test/java/com/fixflow/adapters/events/StompEventPublisherTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.adapters.events;

import com.fixflow.core.domain.ExecutionEvent;
import com.fixflow.core.domain.ExecutionEventType;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class StompEventPublisherTest {

    @Test
    void publishesEventToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID execId = UUID.randomUUID();
        ExecutionEvent event = new ExecutionEvent(
            UUID.randomUUID(), execId, ExecutionEventType.NODE_ENTER,
            "n1", "info", Instant.now(), Map.of()
        );

        pub.publish(event);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/events", event);
    }

    @Test
    void publishesFixMessageToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID execId = UUID.randomUUID();
        FIXMessage msg = new FIXMessage("D", Map.of(35, "D"));

        pub.publishFIXMessage(execId, msg);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/messages", msg);
    }

    @Test
    void publishesSessionStatusToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID sessionId = UUID.randomUUID();
        pub.publishSessionStatus(sessionId, "CONNECTED");

        verify(messaging).convertAndSend(
            eq("/topic/sessions/" + sessionId + "/status"),
            (Object) argThat((Object o) -> o instanceof Map<?,?> m
                && "CONNECTED".equals(m.get("status"))
                && sessionId.equals(m.get("sessionId")))
        );
    }
}
```

#### Step 2: StompEventPublisher

```java
package com.fixflow.adapters.events;

import com.fixflow.core.domain.ExecutionEvent;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.core.ports.EventPublisherPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class StompEventPublisher implements EventPublisherPort {

    private final SimpMessagingTemplate messaging;

    public StompEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(ExecutionEvent event) {
        messaging.convertAndSend(
            "/topic/executions/" + event.executionId() + "/events",
            event
        );
    }

    public void publishFIXMessage(UUID executionId, FIXMessage msg) {
        messaging.convertAndSend(
            "/topic/executions/" + executionId + "/messages",
            msg
        );
    }

    public void publishSessionStatus(UUID sessionId, String status) {
        messaging.convertAndSend(
            "/topic/sessions/" + sessionId + "/status",
            Map.of("sessionId", sessionId, "status", status)
        );
    }
}
```

#### Step 3: Run test

```bash
mvn test -pl fix-flow-adapters -Dtest=StompEventPublisherTest
```

#### Step 4: Commit

```bash
git add fix-flow-adapters/src/main/java/com/fixflow/adapters/events/StompEventPublisher.java \
        fix-flow-adapters/src/test/java/com/fixflow/adapters/events/StompEventPublisherTest.java
git commit -m "feat(adapters): add StompEventPublisher for execution/message/session topics"
```

---

### Task 29: ScenarioController (CRUD + import/export)

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/ScenarioController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ScenarioDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ScenarioRequest.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ValidationErrorDto.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/ScenarioControllerTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ScenarioRepositoryPort repo;

    @Test
    void postCreatesScenario() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario saved = new Scenario(id, "s1", "desc", "1", "sess1",
            "scenario:\n  nodes: []", List.of(), List.of(), Instant.now());
        when(repo.save(any())).thenReturn(saved);

        ScenarioRequest req = new ScenarioRequest("s1", "desc", "sess1", "scenario:\n  nodes: []");
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("s1"));
    }

    @Test
    void getListsScenarios() throws Exception {
        when(repo.findAll()).thenReturn(List.of(
            new Scenario(UUID.randomUUID(), "a", null, "1", null, "", List.of(), List.of(), Instant.now())
        ));
        mvc.perform(get("/api/v1/scenarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getOneReturnsScenario() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Scenario(id, "x", null, "1", null, "", List.of(), List.of(), Instant.now())
        ));
        mvc.perform(get("/api/v1/scenarios/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/scenarios/" + id))
            .andExpect(status().isNoContent());
    }

    @Test
    void exportReturnsYaml() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Scenario(id, "x", null, "1", null, "scenario: {}", List.of(), List.of(), Instant.now())
        ));
        mvc.perform(get("/api/v1/scenarios/" + id + "/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/x-yaml"))
            .andExpect(content().string("scenario: {}"));
    }
}
```

#### Step 2: DTOs

`ScenarioRequest.java`:

```java
package com.fixflow.api.rest.dto;

public record ScenarioRequest(
    String name,
    String description,
    String sessionRef,
    String yamlDsl
) {}
```

`ScenarioDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.Scenario;

import java.time.Instant;
import java.util.UUID;

public record ScenarioDto(
    UUID id,
    String name,
    String description,
    String version,
    String sessionRef,
    String yamlDsl,
    Instant createdAt
) {
    public static ScenarioDto from(Scenario s) {
        return new ScenarioDto(
            s.id(), s.name(), s.description(), s.version(),
            s.sessionRef(), s.yamlDsl(), s.createdAt()
        );
    }
}
```

`ValidationErrorDto.java`:

```java
package com.fixflow.api.rest.dto;

import java.util.List;

public record ValidationErrorDto(boolean valid, List<String> errors) {}
```

#### Step 3: ScenarioController

```java
package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ScenarioDto;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.api.rest.dto.ValidationErrorDto;
import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioRepositoryPort repo;

    public ScenarioController(ScenarioRepositoryPort repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@RequestBody ScenarioRequest req) {
        Scenario s = new Scenario(
            UUID.randomUUID(), req.name(), req.description(), "1",
            req.sessionRef(), req.yamlDsl(), List.of(), List.of(), Instant.now()
        );
        Scenario saved = repo.save(s);
        return ResponseEntity.status(201).body(ScenarioDto.from(saved));
    }

    @GetMapping
    public List<ScenarioDto> list() {
        return repo.findAll().stream().map(ScenarioDto::from).toList();
    }

    @GetMapping("/{id}")
    public ScenarioDto get(@PathVariable UUID id) {
        return ScenarioDto.from(repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<ScenarioDto> importYaml(@RequestParam("file") MultipartFile file) throws Exception {
        String yaml = new String(file.getBytes());
        Scenario s = new Scenario(
            UUID.randomUUID(), file.getOriginalFilename(), "imported", "1",
            null, yaml, List.of(), List.of(), Instant.now()
        );
        return ResponseEntity.status(201).body(ScenarioDto.from(repo.save(s)));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found"));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/x-yaml"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + s.name() + ".yaml");
        return new ResponseEntity<>(s.yamlDsl(), h, 200);
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationErrorDto> validate(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found"));
        List<String> errors = validateScenario(s);
        if (errors.isEmpty()) {
            return ResponseEntity.ok(new ValidationErrorDto(true, List.of()));
        }
        return ResponseEntity.status(400).body(new ValidationErrorDto(false, errors));
    }

    private List<String> validateScenario(Scenario s) {
        List<String> errs = new ArrayList<>();
        var nodeIds = s.nodes().stream().map(n -> n.id()).toList();
        for (var n : s.nodes()) {
            if (n.onSuccess() != null && !nodeIds.contains(n.onSuccess())) {
                errs.add("node " + n.id() + " references missing onSuccess: " + n.onSuccess());
            }
            if (n.onFailure() != null && !nodeIds.contains(n.onFailure())) {
                errs.add("node " + n.id() + " references missing onFailure: " + n.onFailure());
            }
        }
        return errs;
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=ScenarioControllerTest
```

#### Step 5: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/ScenarioController.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ \
        fix-flow-api/src/test/java/com/fixflow/api/rest/ScenarioControllerTest.java
git commit -m "feat(api): add ScenarioController with CRUD, import/export YAML, validate"
```

---

### Task 30: ExecutionController

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/StartExecutionRequest.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionReportDto.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.domain.Execution;
import com.fixflow.core.domain.ExecutionStatus;
import com.fixflow.core.ports.ExecutionRepositoryPort;
import com.fixflow.engine.ExecutionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ExecutionManager manager;
    @MockBean ExecutionRepositoryPort repo;

    @Test
    void startsExecutionReturns202() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(manager.start(any(), any())).thenReturn(execId);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new StartExecutionRequest(sessionId))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value(execId.toString()));
    }

    @Test
    void stopReturns200() throws Exception {
        UUID execId = UUID.randomUUID();
        mvc.perform(post("/api/v1/executions/" + execId + "/stop"))
            .andExpect(status().isOk());
    }

    @Test
    void getExecutionReturnsDto() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, "n1",
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getEventsReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, null,
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id + "/events"))
            .andExpect(status().isOk());
    }

    @Test
    void getMessagesReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.PASSED, Instant.now(), Instant.now(), null,
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id + "/messages"))
            .andExpect(status().isOk());
    }

    @Test
    void getReportReturnsJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.PASSED, Instant.now(), Instant.now(), null,
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id + "/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.execution.id").value(id.toString()));
    }
}
```

#### Step 2: DTOs

`StartExecutionRequest.java`:

```java
package com.fixflow.api.rest.dto;

import java.util.UUID;

public record StartExecutionRequest(UUID sessionId) {}
```

`ExecutionDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.Execution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionDto(
    UUID id,
    UUID scenarioId,
    String scenarioVersion,
    UUID sessionId,
    String status,
    Instant startTime,
    Instant endTime,
    String currentNodeId
) {
    public static ExecutionDto from(Execution e) {
        return new ExecutionDto(
            e.id(), e.scenarioId(), e.scenarioVersion(), e.sessionId(),
            e.status().name(), e.startTime(), e.endTime(), e.currentNodeId()
        );
    }
}
```

`ExecutionReportDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.Execution;
import com.fixflow.core.domain.ExecutionEvent;
import com.fixflow.core.domain.NodeResult;
import com.fixflow.core.fix.FIXMessage;

import java.util.List;

public record ExecutionReportDto(
    ExecutionDto execution,
    List<ExecutionEvent> events,
    List<FIXMessage> messages,
    List<NodeResult> nodeResults
) {
    public static ExecutionReportDto from(Execution e) {
        return new ExecutionReportDto(
            ExecutionDto.from(e),
            e.events(),
            e.messages(),
            e.nodeResults()
        );
    }
}
```

#### Step 3: ExecutionController

```java
package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ExecutionDto;
import com.fixflow.api.rest.dto.ExecutionReportDto;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.domain.Execution;
import com.fixflow.core.ports.ExecutionRepositoryPort;
import com.fixflow.engine.ExecutionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class ExecutionController {

    private final ExecutionManager manager;
    private final ExecutionRepositoryPort repo;

    public ExecutionController(ExecutionManager manager, ExecutionRepositoryPort repo) {
        this.manager = manager;
        this.repo = repo;
    }

    @PostMapping("/api/v1/scenarios/{scenarioId}/execute")
    public ResponseEntity<Map<String, UUID>> start(
        @PathVariable UUID scenarioId,
        @RequestBody StartExecutionRequest req
    ) {
        UUID execId = manager.start(scenarioId, req.sessionId());
        return ResponseEntity
            .accepted()
            .location(URI.create("/api/v1/executions/" + execId))
            .body(Map.of("executionId", execId));
    }

    @PostMapping("/api/v1/executions/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable UUID id) {
        manager.stop(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/v1/executions/{id}")
    public ExecutionDto get(@PathVariable UUID id) {
        return ExecutionDto.from(load(id));
    }

    @GetMapping("/api/v1/executions/{id}/events")
    public List<?> events(@PathVariable UUID id) {
        return load(id).events();
    }

    @GetMapping("/api/v1/executions/{id}/messages")
    public List<?> messages(@PathVariable UUID id) {
        return load(id).messages();
    }

    @GetMapping("/api/v1/executions/{id}/report")
    public ExecutionReportDto report(@PathVariable UUID id) {
        return ExecutionReportDto.from(load(id));
    }

    private Execution load(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("execution not found: " + id));
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=ExecutionControllerTest
```

#### Step 5: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionDto.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/StartExecutionRequest.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionReportDto.java \
        fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java
git commit -m "feat(api): add ExecutionController with start/stop/get/events/messages/report"
```

---

### Task 31: SessionController

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/SessionController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionRequest.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/SessionControllerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.FIXSessionConfig;
import com.fixflow.core.ports.FIXSessionPort;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FIXSessionManager manager;
    @MockBean FIXSessionPort port;
    @MockBean HotReloadService hotReload;

    @Test
    void createSessionReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig saved = new FIXSessionConfig(id, "s1", "ACCEPTOR",
            "FIX.4.4", null, "SENDER", "TARGET", "localhost", 9999, 30, 5,
            true, true, Instant.now());
        when(manager.create(any())).thenReturn(saved);

        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR",
            "FIX.4.4", null, "SENDER", "TARGET", "localhost", 9999, 30, 5, true, true);
        mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void putUpdatesSession() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig existing = new FIXSessionConfig(id, "s1", "ACCEPTOR",
            "FIX.4.4", null, "S", "T", "h", 9, 30, 5, true, true, Instant.now());
        when(manager.findById(id)).thenReturn(Optional.of(existing));
        when(manager.isConnected(id)).thenReturn(false);
        when(manager.update(any(), any())).thenReturn(existing);

        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR",
            "FIX.4.4", null, "S", "T", "h", 9, 30, 5, true, true);
        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    @Test
    void connectReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put("/api/v1/sessions/" + id + "/connect"))
            .andExpect(status().isOk());
    }

    @Test
    void statusReturnsConnectedFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(manager.isConnected(id)).thenReturn(true);
        mvc.perform(get("/api/v1/sessions/" + id + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void putWhileConnectedChangingFixVersionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig existing = new FIXSessionConfig(id, "s1", "ACCEPTOR",
            "FIX.4.4", null, "S", "T", "h", 9, 30, 5, true, true, Instant.now());
        when(manager.findById(id)).thenReturn(Optional.of(existing));
        when(manager.isConnected(id)).thenReturn(true);

        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR",
            "FIX.5.0", null, "S", "T", "h", 9, 30, 5, true, true);
        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }
}
```

#### Step 2: DTOs

`FIXSessionRequest.java`:

```java
package com.fixflow.api.rest.dto;

public record FIXSessionRequest(
    String name,
    String mode,
    String fixVersion,
    String defaultApplVerID,
    String senderCompID,
    String targetCompID,
    String host,
    int port,
    int heartbeatInterval,
    int reconnectInterval,
    boolean resetOnLogon,
    boolean resetOnLogout
) {}
```

`FIXSessionDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.FIXSessionConfig;

import java.time.Instant;
import java.util.UUID;

public record FIXSessionDto(
    UUID id,
    String name,
    String mode,
    String fixVersion,
    String defaultApplVerID,
    String senderCompID,
    String targetCompID,
    String host,
    int port,
    int heartbeatInterval,
    int reconnectInterval,
    boolean resetOnLogon,
    boolean resetOnLogout,
    Instant createdAt,
    boolean connected
) {
    public static FIXSessionDto from(FIXSessionConfig c, boolean connected) {
        return new FIXSessionDto(
            c.id(), c.name(), c.mode(), c.fixVersion(), c.defaultApplVerID(),
            c.senderCompID(), c.targetCompID(), c.host(), c.port(),
            c.heartbeatInterval(), c.reconnectInterval(),
            c.resetOnLogon(), c.resetOnLogout(), c.createdAt(), connected
        );
    }
}
```

#### Step 3: SessionController

```java
package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.FIXSessionDto;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.FIXSessionConfig;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final FIXSessionManager manager;
    private final HotReloadService hotReload;

    public SessionController(FIXSessionManager manager, HotReloadService hotReload) {
        this.manager = manager;
        this.hotReload = hotReload;
    }

    @PostMapping
    public ResponseEntity<FIXSessionDto> create(@RequestBody FIXSessionRequest req) {
        FIXSessionConfig saved = manager.create(req);
        return ResponseEntity.status(201)
            .body(FIXSessionDto.from(saved, false));
    }

    @GetMapping
    public List<FIXSessionDto> list() {
        return manager.findAll().stream()
            .map(c -> FIXSessionDto.from(c, manager.isConnected(c.id())))
            .toList();
    }

    @GetMapping("/{id}")
    public FIXSessionDto get(@PathVariable UUID id) {
        FIXSessionConfig c = manager.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found"));
        return FIXSessionDto.from(c, manager.isConnected(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody FIXSessionRequest req) {
        FIXSessionConfig existing = manager.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found"));
        boolean connected = manager.isConnected(id);
        if (connected && !existing.fixVersion().equals(req.fixVersion())) {
            return ResponseEntity.status(409).body(Map.of(
                "error", "Conflict",
                "message", "Disconnect session before changing FIX version"
            ));
        }
        FIXSessionConfig updated = manager.update(id, req);
        return ResponseEntity.ok(FIXSessionDto.from(updated, connected));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        manager.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/connect")
    public ResponseEntity<Void> connect(@PathVariable UUID id) {
        manager.connect(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/disconnect")
    public ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        manager.disconnect(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable UUID id) {
        return Map.of("sessionId", id, "connected", manager.isConnected(id));
    }

    @PostMapping("/{id}/reload")
    public ResponseEntity<Void> reload(@PathVariable UUID id) {
        hotReload.reload(id);
        return ResponseEntity.ok().build();
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=SessionControllerTest
```

#### Step 5: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/SessionController.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionDto.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionRequest.java \
        fix-flow-api/src/test/java/com/fixflow/api/rest/SessionControllerTest.java
git commit -m "feat(api): add SessionController with CRUD, connect/disconnect, status, hot reload"
```

---

### Task 32: GlobalExceptionHandler + CORS config

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/GlobalExceptionHandler.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/CorsConfig.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ErrorResponse.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/config/GlobalExceptionHandlerTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @Test
    void noSuchElementReturns404() throws Exception {
        mvc.perform(get("/test/notfound"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void illegalArgumentReturns400() throws Exception {
        mvc.perform(get("/test/badarg"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void genericExceptionReturns500() throws Exception {
        mvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/notfound")
        public String notFound() { throw new NoSuchElementException("missing"); }

        @GetMapping("/test/badarg")
        public String bad() { throw new IllegalArgumentException("bad"); }

        @GetMapping("/test/boom")
        public String boom() { throw new RuntimeException("kaboom"); }
    }
}
```

#### Step 2: ErrorResponse

```java
package com.fixflow.api.rest.dto;

import java.time.Instant;

public record ErrorResponse(int status, String error, String message, Instant timestamp) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
```

#### Step 3: GlobalExceptionHandler

```java
package com.fixflow.api.config;

import com.fixflow.api.rest.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(
            ErrorResponse.of(404, "Not Found", ex.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400).body(
            ErrorResponse.of(400, "Bad Request", ex.getMessage())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(500).body(
            ErrorResponse.of(500, "Internal Server Error", ex.getMessage())
        );
    }
}
```

#### Step 4: CorsConfig

```java
package com.fixflow.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173", "http://localhost:8080")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

#### Step 5: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=GlobalExceptionHandlerTest
```

#### Step 6: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/config/GlobalExceptionHandler.java \
        fix-flow-api/src/main/java/com/fixflow/api/config/CorsConfig.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ErrorResponse.java \
        fix-flow-api/src/test/java/com/fixflow/api/config/GlobalExceptionHandlerTest.java
git commit -m "feat(api): add GlobalExceptionHandler with 404/400/500 mapping and CORS config"
```

---

### Task 33: Full API integration test (Spring Boot test)

**Files:**
- Test: `fix-flow-api/src/test/java/com/fixflow/api/FullApiIntegrationTest.java`

#### Step 1: Write the integration test

```java
package com.fixflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.fake.FakeFixAdapter;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.core.ports.FIXSessionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(FullApiIntegrationTest.TestConfig.class)
class FullApiIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired FakeFixAdapter fakeAdapter;

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        FIXSessionPort fixSessionPort() {
            return new FakeFixAdapter();
        }
        @Bean
        FakeFixAdapter fakeFixAdapter(FIXSessionPort port) {
            return (FakeFixAdapter) port;
        }
    }

    @Test
    void endToEndScenarioExecution() throws Exception {
        // 1. Create session
        FIXSessionRequest sessionReq = new FIXSessionRequest(
            "test-session", "ACCEPTOR", "FIX.4.4", null,
            "SENDER", "TARGET", "localhost", 9999, 30, 5, true, true
        );
        ResponseEntity<JsonNode> sessionResp = rest.postForEntity(
            "/api/v1/sessions", sessionReq, JsonNode.class
        );
        assertThat(sessionResp.getStatusCode().value()).isEqualTo(201);
        UUID sessionId = UUID.fromString(sessionResp.getBody().get("id").asText());

        // 2. Create scenario
        String yaml = """
            scenario:
              name: e2e
              nodes:
                - id: start
                  type: START
                  onSuccess: send
                - id: send
                  type: SEND_FIX
                  config:
                    msgType: D
                    fields: {35: D, 11: ORDER-1}
                  onSuccess: expect
                - id: expect
                  type: EXPECT_FIX
                  config:
                    msgType: 8
                  timeout: {value: 3000, unit: MILLISECONDS}
                  onSuccess: end
                  onFailure: failend
                - id: end
                  type: END
                  config: {status: PASSED}
                - id: failend
                  type: END
                  config: {status: FAILED}
            """;
        ScenarioRequest scenarioReq = new ScenarioRequest("e2e", "test", sessionId.toString(), yaml);
        ResponseEntity<JsonNode> scenarioResp = rest.postForEntity(
            "/api/v1/scenarios", scenarioReq, JsonNode.class
        );
        assertThat(scenarioResp.getStatusCode().value()).isEqualTo(201);
        UUID scenarioId = UUID.fromString(scenarioResp.getBody().get("id").asText());

        // 3. Validate scenario
        ResponseEntity<JsonNode> validateResp = rest.postForEntity(
            "/api/v1/scenarios/" + scenarioId + "/validate", null, JsonNode.class
        );
        assertThat(validateResp.getStatusCode().value()).isEqualTo(200);

        // 4. Connect session
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> connectResp = rest.exchange(
            "/api/v1/sessions/" + sessionId + "/connect",
            HttpMethod.PUT, new HttpEntity<>(headers), Void.class
        );
        assertThat(connectResp.getStatusCode().value()).isEqualTo(200);

        // 5. Start execution
        ResponseEntity<JsonNode> execResp = rest.postForEntity(
            "/api/v1/scenarios/" + scenarioId + "/execute",
            new StartExecutionRequest(sessionId), JsonNode.class
        );
        assertThat(execResp.getStatusCode().value()).isEqualTo(202);
        UUID execId = UUID.fromString(execResp.getBody().get("executionId").asText());

        // 6. Inject inbound FIX message via fake adapter
        fakeAdapter.injectInbound(sessionId,
            new FIXMessage("8", Map.of(35, "8", 11, "ORDER-1", 39, "2", 150, "F"))
        );

        // 7. Poll until PASSED
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<JsonNode> r = rest.getForEntity(
                "/api/v1/executions/" + execId, JsonNode.class
            );
            assertThat(r.getBody().get("status").asText()).isEqualTo("PASSED");
        });

        // 8. Verify messages stored
        ResponseEntity<JsonNode> msgs = rest.getForEntity(
            "/api/v1/executions/" + execId + "/messages", JsonNode.class
        );
        assertThat(msgs.getStatusCode().value()).isEqualTo(200);
        assertThat(msgs.getBody().isArray()).isTrue();
        assertThat(msgs.getBody().size()).isGreaterThanOrEqualTo(1);
    }
}
```

#### Step 2: Add Awaitility dependency

Verify `fix-flow-api/pom.xml` contains:

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <version>4.2.0</version>
  <scope>test</scope>
</dependency>
```

If missing, add it before running.

#### Step 3: Run the test

```bash
mvn test -pl fix-flow-api -Dtest=FullApiIntegrationTest
```

Expected: PASS.

#### Step 4: Commit

```bash
git add fix-flow-api/src/test/java/com/fixflow/api/FullApiIntegrationTest.java \
        fix-flow-api/pom.xml
git commit -m "test(api): add end-to-end integration test covering scenario lifecycle via REST"
```

---

*Part 2 of 3 — continues in fix-flow-part3.md (Phases 10-15)*
