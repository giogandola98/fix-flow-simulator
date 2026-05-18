package com.fixflow.engine.variable;

import com.fixflow.core.domain.scenario.RuntimePolicy;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VariableResolverTest {

    private VariableResolver resolver;
    private ExecutionContext ctx;

    private static Scenario minScenario() {
        return new Scenario(UUID.randomUUID(), "test", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver();
        ctx = new ExecutionContext(UUID.randomUUID(), minScenario(), UUID.randomUUID());
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
        ctx.storeNodeMessage("n1", Map.of(131, "QR-12345"));
        String out = resolver.resolveAll("{{node:n1:tag131}}", ctx);
        assertThat(out).isEqualTo("QR-12345");
    }

    @Test
    void resolvesDateOffsetPlusFiveMinutes() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        ctx.storeNodeMessage("n1", Map.of(60, base.toString()));
        String out = resolver.resolveAll("{{node:n1:tag60:offset:+5m}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isEqualTo(base.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void resolvesDateOffsetMinusOneHour() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        ctx.storeNodeMessage("n1", Map.of(60, base.toString()));
        String out = resolver.resolveAll("{{node:n1:tag60:offset:-1h}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isEqualTo(base.minus(1, ChronoUnit.HOURS));
    }

    @Test
    void resolvesNowOffsetPlusOneHour() {
        Instant before = Instant.now();
        String out = resolver.resolveAll("{{now:offset:+1h}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isCloseTo(before.plus(1, ChronoUnit.HOURS), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void resolvesNowOffsetMinusTenMinutes() {
        Instant before = Instant.now();
        String out = resolver.resolveAll("{{now:offset:-10m}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isCloseTo(before.minus(10, ChronoUnit.MINUTES), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void resolvesNowdateAsYyyymmdd() {
        String out = resolver.resolveAll("{{nowdate}}", ctx);
        String expected = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(out).isEqualTo(expected);
        assertThat(out).matches("\\d{8}");
    }

    @Test
    void resolvesMultipleVariablesInTemplate() {
        ctx.storeNodeMessage("n1", Map.of(131, "QR-1"));
        String out = resolver.resolveAll("ID={{node:n1:tag131}};TS={{now}}", ctx);
        assertThat(out).startsWith("ID=QR-1;TS=");
        assertThat(Pattern.matches("ID=QR-1;TS=.+Z", out)).isTrue();
    }
}
