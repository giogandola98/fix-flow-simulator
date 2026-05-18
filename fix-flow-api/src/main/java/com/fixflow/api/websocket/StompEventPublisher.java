package com.fixflow.api.websocket;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class StompEventPublisher implements EventPublisherPort {

    private final SimpMessagingTemplate messaging;

    public StompEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(ExecutionEvent event) {
        messaging.convertAndSend(
            "/topic/executions/" + event.executionId() + "/events",
            event
        );
    }

    @Override
    public void publishMessage(UUID executionId, FIXMessage msg) {
        messaging.convertAndSend(
            "/topic/executions/" + executionId + "/messages",
            msg
        );
    }

    @Override
    public void publishSessionStatus(UUID sessionId, String status) {
        messaging.convertAndSend(
            "/topic/sessions/" + sessionId + "/status",
            Map.of("sessionId", sessionId, "status", status)
        );
    }
}
