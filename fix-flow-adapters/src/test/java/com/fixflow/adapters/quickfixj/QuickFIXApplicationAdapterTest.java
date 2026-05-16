package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.Map;
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
        AtomicReference<ExecutionEvent> captured = new AtomicReference<>();
        EventPublisherPort publisher = captured::set;
        InboundMessageListener noop = (s, f) -> {};

        QuickFIXApplicationAdapter adapter = new QuickFIXApplicationAdapter(noop, publisher);
        SessionID sid = new SessionID("FIX.4.4", "SENDER", "TARGET");
        adapter.onLogon(sid);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().detail()).contains(sid.toString());
    }
}
