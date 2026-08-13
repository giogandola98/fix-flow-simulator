package com.fixflow.engine.variable;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
    void nowDateOffsetMatchesFixFormat() {
        assertThat(resolve("{{nowdate:offset:+30m}}")).matches("\\d{8}-\\d{2}:\\d{2}:\\d{2}");
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
