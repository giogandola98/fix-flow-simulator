package com.fixflow.adapters.quickfixj;

import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QuickFIXApplicationAdapterTest {

    @Test
    void fromAppParsesFieldsAndDelegatesToListener() throws Exception {
        AtomicReference<String> capturedSession = new AtomicReference<>();
        AtomicReference<Map<Integer, String>> capturedFields = new AtomicReference<>();

        InboundMessageListener listener = (sid, fields) -> {
            capturedSession.set(sid);
            capturedFields.set(fields);
        };
        EventPublisherPort publisher = ev -> { /* no-op */ };

        QuickFIXApplicationAdapter adapter = new QuickFIXApplicationAdapter(listener, publisher);

        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, "D");
        msg.setString(11, "CL-1");
        msg.setString(55, "AAPL");

        SessionID sid = new SessionID("FIX.4.4", "SENDER", "TARGET");
        adapter.fromApp(msg, sid);

        assertThat(capturedSession.get()).isEqualTo(sid.toString());
        assertThat(capturedFields.get()).containsEntry(11, "CL-1").containsEntry(55, "AAPL");
        assertThat(capturedFields.get()).containsEntry(MsgType.FIELD, "D");
    }

    @Test
    void onLogonEmitsSessionUpEvent() {
        AtomicReference<UUID> capturedId = new AtomicReference<>();
        AtomicReference<String> capturedStatus = new AtomicReference<>();
        EventPublisherPort publisher = new EventPublisherPort() {
            @Override public void publish(com.fixflow.core.domain.execution.ExecutionEvent e) {}
            @Override public void publishSessionStatus(UUID sessionId, String status) {
                capturedId.set(sessionId);
                capturedStatus.set(status);
            }
        };
        InboundMessageListener noop = (s, f) -> {};

        UUID uuid = UUID.randomUUID();
        SessionID sid = new SessionID("FIX.4.4", "SENDER", "TARGET");
        QuickFIXApplicationAdapter adapter = new QuickFIXApplicationAdapter(noop, publisher);
        adapter.registerSession(sid, uuid);
        adapter.onLogon(sid);

        assertThat(capturedId.get()).isEqualTo(uuid);
        assertThat(capturedStatus.get()).isEqualTo("UP");
    }

    @Test
    void onLogonWithoutRegisteredSessionDoesNotPublish() {
        AtomicReference<String> capturedStatus = new AtomicReference<>();
        EventPublisherPort publisher = new EventPublisherPort() {
            @Override public void publish(com.fixflow.core.domain.execution.ExecutionEvent e) {}
            @Override public void publishSessionStatus(UUID sessionId, String status) {
                capturedStatus.set(status);
            }
        };
        InboundMessageListener noop = (s, f) -> {};

        QuickFIXApplicationAdapter adapter = new QuickFIXApplicationAdapter(noop, publisher);
        adapter.onLogon(new SessionID("FIX.4.4", "SENDER", "TARGET"));

        assertThat(capturedStatus.get()).isNull();
    }
}
