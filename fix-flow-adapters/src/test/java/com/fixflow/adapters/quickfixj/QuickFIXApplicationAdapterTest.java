package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class QuickFIXApplicationAdapterTest {

    /** Records status publications. */
    static class RecordingPublisher implements EventPublisherPort {
        final List<UUID> ids = new ArrayList<>();
        final List<String> statuses = new ArrayList<>();
        final List<ExecutionEvent> events = new ArrayList<>();

        @Override public void publish(ExecutionEvent event) { events.add(event); }
        @Override public void publishSessionStatus(UUID sessionId, String status) {
            ids.add(sessionId);
            statuses.add(status);
        }
    }

    /** Records the last inbound message routed to the listener. */
    static class RecordingListener implements InboundMessageListener {
        final AtomicReference<String> session = new AtomicReference<>();
        final AtomicReference<Map<Integer, String>> fields = new AtomicReference<>();
        @Override public void onMessage(String sessionId, FIXMessageData message) {
            session.set(sessionId);
            fields.set(message.flatFields());
        }
    }

    private RecordingPublisher publisher;
    private RecordingListener listener;
    private QuickFIXApplicationAdapter adapter;
    private final SessionID sid = new SessionID("FIX.4.4", "SENDER", "TARGET");

    @BeforeEach
    void setUp() {
        publisher = new RecordingPublisher();
        listener = new RecordingListener();
        adapter = new QuickFIXApplicationAdapter(listener, publisher);
    }

    @Test
    void fromAppExtractsHeaderBodyTrailerFieldsAndUsesRawSessionIdWhenUnregistered() throws Exception {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, "D");   // header tag 35
        msg.setString(11, "CL-1");                         // body
        msg.setString(55, "AAPL");                         // body
        msg.getTrailer().setString(10, "123");             // trailer tag 10 (checksum)

        adapter.fromApp(msg, sid);

        assertThat(listener.session.get()).isEqualTo(sid.toString());
        assertThat(listener.fields.get())
                .containsEntry(MsgType.FIELD, "D")
                .containsEntry(11, "CL-1")
                .containsEntry(55, "AAPL")
                .containsEntry(10, "123");
    }

    @Test
    void fromAppTranslatesSessionIdToRegisteredUuid() throws Exception {
        UUID uuid = UUID.randomUUID();
        adapter.registerSession(sid, uuid);

        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, "0");
        adapter.fromApp(msg, sid);

        assertThat(listener.session.get()).isEqualTo(uuid.toString());
    }

    @Test
    void setInboundListenerSwapsTheActiveListener() throws Exception {
        RecordingListener replacement = new RecordingListener();
        adapter.setInboundListener(replacement);

        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, "D");
        adapter.fromApp(msg, sid);

        assertThat(replacement.session.get()).isEqualTo(sid.toString());
        assertThat(listener.session.get()).isNull();
    }

    @Test
    void onLogonPublishesUpForRegisteredSession() {
        UUID uuid = UUID.randomUUID();
        adapter.registerSession(sid, uuid);

        adapter.onLogon(sid);

        assertThat(publisher.ids).containsExactly(uuid);
        assertThat(publisher.statuses).containsExactly("UP");
    }

    @Test
    void onLogoutPublishesDownForRegisteredSession() {
        UUID uuid = UUID.randomUUID();
        adapter.registerSession(sid, uuid);

        adapter.onLogout(sid);

        assertThat(publisher.ids).containsExactly(uuid);
        assertThat(publisher.statuses).containsExactly("DOWN");
    }

    @Test
    void onLogonWithoutRegistrationPublishesNothing() {
        adapter.onLogon(sid);
        assertThat(publisher.statuses).isEmpty();
    }

    @Test
    void onLogoutWithoutRegistrationPublishesNothing() {
        adapter.onLogout(sid);
        assertThat(publisher.statuses).isEmpty();
    }

    @Test
    void unregisterSessionStopsStatusPublication() {
        UUID uuid = UUID.randomUUID();
        adapter.registerSession(sid, uuid);
        adapter.unregisterSession(sid);

        adapter.onLogon(sid);

        assertThat(publisher.statuses).isEmpty();
    }

    @Test
    void noOpCallbacksDoNotThrow() {
        Message msg = new Message();
        assertThatCode(() -> {
            adapter.onCreate(sid);
            adapter.toAdmin(msg, sid);
            adapter.fromAdmin(msg, sid);
            adapter.toApp(msg, sid);
        }).doesNotThrowAnyException();
    }
}
