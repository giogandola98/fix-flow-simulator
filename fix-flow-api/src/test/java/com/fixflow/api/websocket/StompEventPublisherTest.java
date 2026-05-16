package com.fixflow.api.websocket;

import com.fixflow.core.domain.execution.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StompEventPublisherTest {

    @Test
    void publishesEventToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID execId = UUID.randomUUID();
        ExecutionEvent event = new ExecutionEvent(
            UUID.randomUUID(), execId, ExecutionEventType.NODE_ENTERED,
            "n1", Instant.now(), "info", null
        );

        pub.publish(event);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/events", event);
    }

    @Test
    void publishesFIXMessageToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID execId = UUID.randomUUID();
        FIXMessage msg = new FIXMessage(UUID.randomUUID(), execId, Direction.INBOUND, "8=FIX.4.2", Map.of(35, "D"), Instant.now());

        pub.publishFIXMessage(execId, msg);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/messages", msg);
    }

    @Test
    void publishesSessionStatusToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID sessionId = UUID.randomUUID();
        pub.publishSessionStatus(sessionId, "CONNECTED");

        verify(messaging).convertAndSend(
            eq("/topic/sessions/" + sessionId + "/status"),
            (Object) argThat((Object o) -> o instanceof Map<?,?> m
                && "CONNECTED".equals(m.get("status"))
                && sessionId.equals(m.get("sessionId")))
        );
    }
}
