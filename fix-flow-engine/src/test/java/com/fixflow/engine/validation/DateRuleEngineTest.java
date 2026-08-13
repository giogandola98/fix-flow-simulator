package com.fixflow.engine.validation;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.fixflow.engine.support.Fixtures.endPass;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class DateRuleEngineTest {

    private DateRuleEngine engine;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        engine = new DateRuleEngine();
        Scenario s = scenario("s", start("end"), endPass("end"));
        ctx = Fixtures.ctx(s);
    }

    private DateRule currentTimestamp(long toleranceSec) {
        return new DateRule("d", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0, TimeUnit.SECONDS,
                toleranceSec, TimeUnit.SECONDS);
    }

    private DateRule fieldOffset(String srcNode, int srcTag, long offsetSec, long tolSec) {
        return new DateRule("d", DateRuleType.FIELD_OFFSET, srcNode, srcTag, offsetSec, TimeUnit.SECONDS,
                tolSec, TimeUnit.SECONDS);
    }

    @Test
    void currentTimestampWithinTolerancePasses() {
        Instant now = Instant.now();
        ValidationResult r = engine.validate(currentTimestamp(5), 52, Fixtures.fields(52, now.toString()), ctx, now);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void currentTimestampOutsideTolferanceFails() {
        Instant now = Instant.now();
        Instant actual = now.plusSeconds(3600);
        ValidationResult r = engine.validate(currentTimestamp(5), 52, Fixtures.fields(52, actual.toString()), ctx, now);
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).contains("delta=");
    }

    @Test
    void missingFieldFails() {
        ValidationResult r = engine.validate(currentTimestamp(5), 52, Map.of(), ctx, Instant.now());
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).isEqualTo("field missing");
    }

    @Test
    void unparseableValueFails() {
        ValidationResult r = engine.validate(currentTimestamp(5), 52, Fixtures.fields(52, "not-a-date"), ctx, Instant.now());
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).isEqualTo("cannot parse");
    }

    @Test
    void fieldOffsetWithinTolerancePasses() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ctx.storeNodeMessage("src", Fixtures.fields(60, base.toString()));
        Instant actual = base.plusSeconds(30);
        ValidationResult r = engine.validate(fieldOffset("src", 60, 30, 2), 52,
                Fixtures.fields(52, actual.toString()), ctx, Instant.now());
        assertThat(r.passed()).isTrue();
    }

    @Test
    void fieldOffsetOutsideToleranceFails() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ctx.storeNodeMessage("src", Fixtures.fields(60, base.toString()));
        Instant actual = base.plusSeconds(300);
        ValidationResult r = engine.validate(fieldOffset("src", 60, 30, 2), 52,
                Fixtures.fields(52, actual.toString()), ctx, Instant.now());
        assertThat(r.passed()).isFalse();
    }

    @Test
    void fieldOffsetSourceNodeMissingFails() {
        ValidationResult r = engine.validate(fieldOffset("ghost", 60, 30, 2), 52,
                Fixtures.fields(52, Instant.now().toString()), ctx, Instant.now());
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).isEqualTo("source node not found");
    }

    @Test
    void fieldOffsetSourceTagMissingFails() {
        ctx.storeNodeMessage("src", Fixtures.fields(99, "x"));
        ValidationResult r = engine.validate(fieldOffset("src", 60, 30, 2), 52,
                Fixtures.fields(52, Instant.now().toString()), ctx, Instant.now());
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).isEqualTo("source tag missing");
    }

    @Test
    void fieldOffsetSourceUnparseableFails() {
        ctx.storeNodeMessage("src", Fixtures.fields(60, "bad"));
        ValidationResult r = engine.validate(fieldOffset("src", 60, 30, 2), 52,
                Fixtures.fields(52, Instant.now().toString()), ctx, Instant.now());
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).isEqualTo("source not parseable");
    }
}
