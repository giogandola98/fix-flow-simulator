package com.fixflow.engine.operator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorGatewayTest {

    private final OperatorGateway gateway = new OperatorGateway();

    @Test
    void parent_lists_the_oldest_child_prompt_first() {
        UUID parent = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        gateway.open(first, List.of(parent), "op", "one", null, null);
        gateway.open(second, List.of(parent), "op", "two", null, null);
        assertThat(gateway.open(parent).get().executionId()).isEqualTo(first);
        gateway.answer(first, "op", true, "");
        assertThat(gateway.open(parent).get().executionId()).isEqualTo(second);
    }

    @Test
    void closing_a_displaced_prompt_leaves_the_newer_one() {
        UUID id = UUID.randomUUID();
        CompletableFuture<OperatorGateway.OperatorAnswer> old = gateway.open(id, "op", "old");
        CompletableFuture<OperatorGateway.OperatorAnswer> newer = gateway.open(id, "op", "new");
        gateway.close(id, "op", old);
        assertThat(newer.isCancelled()).isFalse();
        assertThat(gateway.open(id).get().instruction()).isEqualTo("new");
        assertThat(gateway.answer(id, "op", true, "")).isTrue();
    }
}
