package com.fixflow.engine.execution;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionExecutionRegistryTest {

    private final SessionExecutionRegistry registry = new SessionExecutionRegistry();

    @Test
    void registersAndUnregistersPerSession() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        registry.register("s1", a);
        registry.register("s1", b);
        registry.register("s2", a);

        assertThat(registry.executionsFor("s1")).containsExactlyInAnyOrder(a, b);
        assertThat(registry.executionsFor("s2")).containsExactly(a);

        registry.unregister("s1", a);
        assertThat(registry.executionsFor("s1")).containsExactly(b);
        registry.unregister("s1", b);
        assertThat(registry.executionsFor("s1")).isEmpty();
    }

    @Test
    void unknownOrNullSessionYieldsAnEmptySet() {
        assertThat(registry.executionsFor("nope")).isEmpty();
        assertThat(registry.executionsFor(null)).isEmpty();
    }

    @Test
    void blankSessionOrNullExecutionIsIgnored() {
        registry.register("  ", UUID.randomUUID());
        registry.register(null, UUID.randomUUID());
        registry.register("s1", null);
        assertThat(registry.executionsFor("s1")).isEmpty();
    }
}
