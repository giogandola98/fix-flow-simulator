package com.fixflow.engine.fix;

import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRouterTest {

    @Test
    void messageMatchingActiveWaiterIsDelivered() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(10, 60_000);
        MessageRouter router = new MessageRouter(engine, buffer);

        CompletableFuture<Map<Integer, String>> f =
                engine.register("exec-1", "sess", new CorrelationRule(131, "n", 131, 1000), "REQ-1");

        router.onMessage("sess", Map.of(131, "REQ-1"));

        assertThat(f.get(200, TimeUnit.MILLISECONDS)).containsEntry(131, "REQ-1");
        assertThat(buffer.size("sess")).isZero();
    }

    @Test
    void unmatchedMessageIsParked() {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(10, 60_000);
        MessageRouter router = new MessageRouter(engine, buffer);

        router.onMessage("sess", Map.of(131, "REQ-XYZ"));

        assertThat(buffer.size("sess")).isEqualTo(1);
    }

    @Test
    void drainBufferDeliversParkedMessageToNewWaiter() {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(10, 60_000);
        MessageRouter router = new MessageRouter(engine, buffer);

        router.onMessage("sess", Map.of(131, "REQ-LATE"));
        CompletableFuture<Map<Integer, String>> f =
                engine.register("exec-2", "sess", new CorrelationRule(131, "n", 131, 1000), "REQ-LATE");

        router.drain("sess");

        assertThat(f).isCompleted();
    }
}
