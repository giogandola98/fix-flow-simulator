package com.fixflow.core.ports;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PortsTest {

    @Test
    void eventPublisherDefaultMethodsAreNoOpAndPublishIsInvoked() {
        AtomicReference<ExecutionEvent> published = new AtomicReference<>();
        EventPublisherPort port = published::set; // only abstract publish implemented

        ExecutionEvent ev = ExecutionEvent.of(UUID.randomUUID(), ExecutionEventType.NODE_ENTERED, "n1", "d");
        port.publish(ev);
        assertThat(published.get()).isSameAs(ev);

        FIXMessage msg = new FIXMessage(UUID.randomUUID(), UUID.randomUUID(),
                Direction.OUTBOUND, "raw", Map.of(35, "D"), Instant.now());

        // default methods must not throw
        assertThatCode(() -> port.publishMessage(UUID.randomUUID(), msg)).doesNotThrowAnyException();
        assertThatCode(() -> port.publishSessionStatus(UUID.randomUUID(), "UP")).doesNotThrowAnyException();
    }

    @Test
    void inboundMessageListenerReceivesCallback() {
        AtomicReference<String> session = new AtomicReference<>();
        AtomicReference<Map<Integer, String>> fields = new AtomicReference<>();
        InboundMessageListener listener = (s, f) -> { session.set(s); fields.set(f); };

        listener.onMessage("SESSION-1", Map.of(35, "D", 11, "order-1"));

        assertThat(session.get()).isEqualTo("SESSION-1");
        assertThat(fields.get()).containsEntry(35, "D").containsEntry(11, "order-1");
    }
}
