package com.fixflow.api.websocket;

import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.FIXMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompEventPublisherTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final StompEventPublisher pub = new StompEventPublisher(messaging);

    @Test
    void publishSendsEventToExecutionEventsTopic() {
        UUID execId = UUID.randomUUID();
        ExecutionEvent event = new ExecutionEvent(UUID.randomUUID(), execId,
            ExecutionEventType.NODE_ENTERED, "n1", Instant.now(), "info", null);

        pub.publish(event);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/events", event);
    }

    @Test
    void publishMessageSendsToExecutionMessagesTopic() {
        UUID execId = UUID.randomUUID();
        FIXMessage msg = new FIXMessage(UUID.randomUUID(), execId, Direction.INBOUND,
            "8=FIX.4.2", Map.of(35, "D"), Instant.now());

        pub.publishMessage(execId, msg);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/messages", msg);
    }

    @Test
    void publishSessionStatusSendsToSessionStatusTopic() {
        UUID sessionId = UUID.randomUUID();

        pub.publishSessionStatus(sessionId, "CONNECTED");

        verify(messaging).convertAndSend(
            eq("/topic/sessions/" + sessionId + "/status"),
            (Object) argThat((Object o) -> o instanceof Map<?, ?> m
                && "CONNECTED".equals(m.get("status"))
                && sessionId.equals(m.get("sessionId"))));
    }
}
