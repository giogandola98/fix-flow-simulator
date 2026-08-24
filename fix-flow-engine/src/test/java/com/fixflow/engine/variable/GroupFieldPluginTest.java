package com.fixflow.engine.variable;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GroupFieldPluginTest {

    private final VariableResolver resolver = new VariableResolver();

    private ExecutionContext ctxWithLegs() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref",
                null, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        ExecutionContext c = new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());

        FIXMessageData near = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "1", 588, "2026-08-26T00:00:00Z"));
        FIXMessageData far  = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "2"));
        c.storeNodeMessage("order", new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(near, far))));
        return c;
    }

    @Test
    void resolvesFieldFromFirstGroupEntry() {
        assertEquals("EUR/USD", resolver.resolveAll("{{node:order:g555.0:tag600}}", ctxWithLegs()));
    }

    @Test
    void resolvesFieldFromSecondGroupEntry() {
        assertEquals("2", resolver.resolveAll("{{node:order:g555.1:tag624}}", ctxWithLegs()));
    }

    @Test
    void interpolatesInsideALargerString() {
        assertEquals("leg=EUR/USD side=1",
                resolver.resolveAll("leg={{node:order:g555.0:tag600}} side={{node:order:g555.0:tag624}}", ctxWithLegs()));
    }

    @Test
    void missingTagResolvesToEmptyString() {
        assertEquals("", resolver.resolveAll("{{node:order:g555.0:tag9999}}", ctxWithLegs()));
    }

    @Test
    void outOfRangeIndexFailsLoudlyWithContext() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolveAll("{{node:order:g555.5:tag600}}", ctxWithLegs()));
        assertTrue(ex.getMessage().contains("555"));
        assertTrue(ex.getMessage().contains("order"));
    }

    @Test
    void unknownNodeFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolveAll("{{node:nope:g555.0:tag600}}", ctxWithLegs()));
    }

    @Test
    void offsetVariantShiftsTheDate() {
        assertEquals("2026-08-28T00:00:00Z",
                resolver.resolveAll("{{node:order:g555.0:tag588:offset:+2d}}", ctxWithLegs()));
    }

    @Test
    void plainNodeTagPlaceholderStillWorks() {
        assertEquals("AB", resolver.resolveAll("{{node:order:tag35}}", ctxWithLegs()));
    }
}
