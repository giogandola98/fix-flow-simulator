package com.fixflow.engine.variable;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableResolverTest {

    private final VariableResolver resolver = new VariableResolver();
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        Scenario s = scenario("s", start("end"), Fixtures.endPass("end"));
        ctx = Fixtures.ctx(s);
    }

    private String resolve(String template) { return resolver.resolveAll(template, ctx); }

    @Test
    void nullTemplateReturnsNull() {
        assertThat(resolver.resolveAll(null, ctx)).isNull();
    }

    @Test
    void plainTextPassesThroughUnchanged() {
        assertThat(resolve("no placeholders here")).isEqualTo("no placeholders here");
    }

    @Test
    void nowResolvesToParseableInstant() {
        String out = resolve("{{now}}");
        assertThat(Instant.parse(out)).isNotNull();
    }

    @Test
    void nowOffsetPlusIsInTheFuture() {
        Instant before = Instant.now();
        Instant out = Instant.parse(resolve("{{now:offset:+1h}}"));
        assertThat(out).isAfter(before);
    }

    @Test
    void nowOffsetMinusIsInThePast() {
        Instant out = Instant.parse(resolve("{{now:offset:-1d}}"));
        assertThat(out).isBefore(Instant.now());
    }

    @Test
    void nowDateIsEightDigits() {
        assertThat(resolve("{{nowdate}}")).matches("\\d{8}");
    }

    @Test
    void nowDateOffsetIsEightDigitDate() {
        assertThat(resolve("{{nowdate:offset:+30m}}")).matches("\\d{8}");
    }

    @Test
    void nowDateOffsetPlusOneDayIsTomorrow() {
        // Retry once around UTC midnight so the two clock reads see the same day.
        String expected = LocalDate.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        String out = resolve("{{nowdate:offset:+1d}}");
        if (!out.equals(expected)) {
            expected = LocalDate.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
            out = resolve("{{nowdate:offset:+1d}}");
        }
        assertThat(out).isEqualTo(expected);
    }

    @Test
    void nowDateOffsetMinusTwoDaysIsInThePast() {
        String expected = LocalDate.now(ZoneOffset.UTC).minusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE);
        String out = resolve("{{nowdate:offset:-2d}}");
        if (!out.equals(expected)) {
            expected = LocalDate.now(ZoneOffset.UTC).minusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE);
            out = resolve("{{nowdate:offset:-2d}}");
        }
        assertThat(out).matches("\\d{8}").isEqualTo(expected);
    }

    @Test
    void fixTimeIsFixUtcTimestamp() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String out = resolve("{{fixtime}}");
        Instant after = Instant.now();
        assertThat(out).matches(FIX_TS);
        Instant parsed = parseFixTimestamp(out);
        assertThat(parsed).isBetween(before, after);
    }

    @Test
    void fixTimeOffsetPlusAppliesOffset() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String out = resolve("{{fixtime:offset:+5m}}");
        Instant after = Instant.now();
        assertThat(out).matches(FIX_TS);
        assertThat(parseFixTimestamp(out))
            .isBetween(before.plus(5, ChronoUnit.MINUTES), after.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void fixTimeOffsetMinusAppliesOffset() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String out = resolve("{{fixtime:offset:-1d}}");
        Instant after = Instant.now();
        assertThat(out).matches(FIX_TS);
        assertThat(parseFixTimestamp(out))
            .isBetween(before.minus(1, ChronoUnit.DAYS), after.minus(1, ChronoUnit.DAYS));
    }

    @Test
    void nowStaysIso8601() {
        String out = resolve("{{now}}");
        assertThat(out).contains("T").endsWith("Z");
        assertThat(Instant.parse(out)).isNotNull();
    }

    private static final String FIX_TS = "^\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}$";

    private static Instant parseFixTimestamp(String s) {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
            .parse(s, Instant::from);
    }

    @Test
    void uuidResolvesToUuid() {
        assertThat(resolve("{{uuid}}")).matches("[0-9a-f-]{36}");
    }

    @Test
    void seqIncrementsPerName() {
        assertThat(resolve("{{seq:orders}}")).isEqualTo("1");
        assertThat(resolve("{{seq:orders}}")).isEqualTo("2");
        assertThat(resolve("{{seq:other}}")).isEqualTo("1");
    }

    @Test
    void envUnknownVariableResolvesToEmpty() {
        assertThat(resolve("{{env:DEFINITELY_NOT_SET_" + System.nanoTime() + "}}")).isEmpty();
    }

    @Test
    void envKnownVariableResolves() {
        String path = System.getenv("PATH");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null && !path.isBlank());
        assertThat(resolve("{{env:PATH}}")).isEqualTo(path);
    }

    @Test
    void varResolvesFromContextAndMissingIsEmpty() {
        ctx.setVariable("side", "BUY");
        assertThat(resolve("{{var:side}}")).isEqualTo("BUY");
        assertThat(resolve("{{var:missing}}")).isEmpty();
    }

    @Test
    void nodeFieldRefResolvesStoredTag() {
        ctx.storeNodeMessage("n1", Fixtures.fields(11, "ORD1"));
        assertThat(resolve("{{node:n1:tag11}}")).isEqualTo("ORD1");
    }

    @Test
    void nodeFieldRefMissingTagIsEmpty() {
        ctx.storeNodeMessage("n1", Fixtures.fields(11, "ORD1"));
        assertThat(resolve("{{node:n1:tag99}}")).isEmpty();
    }

    @Test
    void nodeFieldRefUnknownNodeThrows() {
        assertThatThrownBy(() -> resolve("{{node:ghost:tag11}}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nodeFieldOffsetShiftsStoredTimestamp() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ctx.storeNodeMessage("n1", Fixtures.fields(52, base.toString()));
        Instant shifted = Instant.parse(resolve("{{node:n1:tag52:offset:+1h}}"));
        assertThat(shifted).isEqualTo(base.plusSeconds(3600));
    }

    @Test
    void unknownExpressionThrows() {
        assertThatThrownBy(() -> resolve("{{totally-unknown}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplePlaceholdersInOneTemplate() {
        ctx.setVariable("a", "X");
        ctx.setVariable("b", "Y");
        assertThat(resolve("{{var:a}}-{{var:b}}")).isEqualTo("X-Y");
    }
}
