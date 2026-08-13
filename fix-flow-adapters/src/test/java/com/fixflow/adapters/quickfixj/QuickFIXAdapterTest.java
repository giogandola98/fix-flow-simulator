package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import quickfix.Connector;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.MsgType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QuickFIXAdapterTest {

    private QuickFIXApplicationAdapter newApplication() {
        InboundMessageListener noop = (s, f) -> {};
        EventPublisherPort pub = new EventPublisherPort() {
            @Override public void publish(com.fixflow.core.domain.execution.ExecutionEvent e) {}
        };
        return new QuickFIXApplicationAdapter(noop, pub);
    }

    private FIXSessionConfig initiatorConfig(FIXVersion version) {
        return new FIXSessionConfig(UUID.randomUUID(), "init", FIXMode.INITIATOR, version,
                null, "SENDER", "TARGET", "localhost", 9001, 30, true, false);
    }

    private SessionSettings buildSettings(QuickFIXAdapter adapter, FIXSessionConfig cfg) throws Exception {
        Method m = QuickFIXAdapter.class.getDeclaredMethod("buildSettings", FIXSessionConfig.class);
        m.setAccessible(true);
        return (SessionSettings) m.invoke(adapter, cfg);
    }

    private SessionID sidFor(String beginString) {
        return new SessionID(beginString, "SENDER", "TARGET");
    }

    // ---------- buildSettings ----------

    @Test
    void buildSettingsUsesYNForBooleanConverterGotcha() throws Exception {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        FIXSessionConfig cfg = new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "SENDER", "TARGET", "localhost", 9001, 30, true, false);

        SessionSettings settings = buildSettings(adapter, cfg);

        // Raw string form must be Y / N, never true/false (QuickFIX/J BooleanConverter gotcha).
        assertThat(settings.getString("ResetOnLogon")).isEqualTo("Y");
        assertThat(settings.getString("ResetOnLogout")).isEqualTo("N");
        // And QuickFIX/J's own BooleanConverter parses them without error.
        assertThat(settings.getBool("ResetOnLogon")).isTrue();
        assertThat(settings.getBool("ResetOnLogout")).isFalse();
    }

    @Test
    void buildSettingsInitiatorPopulatesConnectHostAndPort() throws Exception {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        SessionSettings settings = buildSettings(adapter, initiatorConfig(FIXVersion.FIX_44));

        assertThat(settings.getString("ConnectionType")).isEqualTo("initiator");
        assertThat(settings.getLong("HeartBtInt")).isEqualTo(30);
        assertThat(settings.getString("SocketConnectHost")).isEqualTo("localhost");
        assertThat(settings.getLong("SocketConnectPort")).isEqualTo(9001);
        assertThat(settings.getString("ValidateIncomingMessage")).isEqualTo("N");
        assertThat(settings.getString("ValidateUserDefinedFields")).isEqualTo("N");

        SessionID sid = sidFor("FIX.4.4");
        assertThat(settings.getString(sid, "BeginString")).isEqualTo("FIX.4.4");
        assertThat(settings.getString(sid, "SenderCompID")).isEqualTo("SENDER");
        assertThat(settings.getString(sid, "TargetCompID")).isEqualTo("TARGET");
        assertThat(settings.getString(sid, "DataDictionary")).isEqualTo("FIX44.xml");
    }

    @Test
    void buildSettingsAcceptorUsesAcceptPortAndNoConnectHost() throws Exception {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        FIXSessionConfig cfg = new FIXSessionConfig(UUID.randomUUID(), "acc", FIXMode.ACCEPTOR,
                FIXVersion.FIX_42, null, "SENDER", "TARGET", "localhost", 9001, 30, false, false);

        SessionSettings settings = buildSettings(adapter, cfg);

        assertThat(settings.getString("ConnectionType")).isEqualTo("acceptor");
        assertThat(settings.getLong("SocketAcceptPort")).isEqualTo(9001);
        SessionID sid = sidFor("FIX.4.2");
        assertThat(settings.getString(sid, "BeginString")).isEqualTo("FIX.4.2");
        assertThat(settings.getString(sid, "DataDictionary")).isEqualTo("FIX42.xml");
    }

    @Test
    void buildSettingsFixt11UsesDictionariesAndDefaultApplVerId() throws Exception {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        FIXSessionConfig cfg = new FIXSessionConfig(UUID.randomUUID(), "t11", FIXMode.INITIATOR,
                FIXVersion.FIXT_11, null, "SENDER", "TARGET", "localhost", 9001, 30, false, false);

        SessionSettings settings = buildSettings(adapter, cfg);
        SessionID sid = sidFor("FIXT.1.1");

        assertThat(settings.getString(sid, "BeginString")).isEqualTo("FIXT.1.1");
        assertThat(settings.getString(sid, "DefaultApplVerID")).isEqualTo("9"); // null -> default "9"
        assertThat(settings.getString(sid, "AppDataDictionary")).isEqualTo("FIX50SP2.xml");
        assertThat(settings.getString(sid, "TransportDataDictionary")).isEqualTo("FIXT11.xml");
    }

    @Test
    void buildSettingsFixt11HonoursExplicitDefaultApplVerId() throws Exception {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        FIXSessionConfig cfg = new FIXSessionConfig(UUID.randomUUID(), "t11", FIXMode.INITIATOR,
                FIXVersion.FIXT_11, "7", "SENDER", "TARGET", "localhost", 9001, 30, false, false);

        SessionSettings settings = buildSettings(adapter, cfg);
        assertThat(settings.getString(sidFor("FIXT.1.1"), "DefaultApplVerID")).isEqualTo("7");
    }

    // ---------- unknown-session guards ----------

    @Test
    void sendMessageToUnknownSessionThrows() {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        assertThatThrownBy(() -> adapter.sendMessage(UUID.randomUUID(), Map.of(35, "D")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown session");
    }

    @Test
    void isConnectedForUnknownSessionReturnsFalse() {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        assertThat(adapter.isConnected(UUID.randomUUID())).isFalse();
    }

    @Test
    void disconnectUnknownSessionIsNoOp() {
        QuickFIXAdapter adapter = new QuickFIXAdapter(newApplication());
        assertThatCode(() -> adapter.disconnect(UUID.randomUUID())).doesNotThrowAnyException();
    }

    // ---------- setInboundListener delegation ----------

    @Test
    void setInboundListenerDelegatesToApplication() throws Exception {
        QuickFIXApplicationAdapter app = newApplication();
        QuickFIXAdapter adapter = new QuickFIXAdapter(app);

        AtomicReference<String> captured = new AtomicReference<>();
        adapter.setInboundListener((sid, fields) -> captured.set(sid));

        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, "D");
        SessionID sid = sidFor("FIX.4.4");
        app.fromApp(msg, sid);

        assertThat(captured.get()).isEqualTo(sid.toString());
    }

    // ---------- @PreDestroy shutdown ----------

    @Test
    @SuppressWarnings("unchecked")
    void shutdownStopsConnectorsUnregistersSessionsAndClearsMaps() throws Exception {
        QuickFIXApplicationAdapter app = newApplication();
        QuickFIXAdapter adapter = new QuickFIXAdapter(app);

        UUID id = UUID.randomUUID();
        SessionID sid = sidFor("FIX.4.4");
        Connector connector = Mockito.mock(Connector.class);

        Map<UUID, Connector> connectors =
                (Map<UUID, Connector>) readField(adapter, "connectors");
        Map<UUID, SessionID> sessions =
                (Map<UUID, SessionID>) readField(adapter, "sessions");
        connectors.put(id, connector);
        sessions.put(id, sid);
        app.registerSession(sid, id);

        Method shutdown = QuickFIXAdapter.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(adapter);

        verify(connector, times(1)).stop(true);
        assertThat(connectors).isEmpty();
        assertThat(sessions).isEmpty();

        // Session was unregistered: onLogon now publishes nothing.
        AtomicReference<String> status = new AtomicReference<>();
        QuickFIXApplicationAdapter probe = new QuickFIXApplicationAdapter((s, f) -> {},
                new EventPublisherPort() {
                    @Override public void publish(com.fixflow.core.domain.execution.ExecutionEvent e) {}
                    @Override public void publishSessionStatus(UUID sessionId, String st) { status.set(st); }
                });
        app.onLogon(sid); // app's own map is empty now
        assertThat(status.get()).isNull();
    }

    // ---------- real acceptor connect/disconnect (binds a local port, no live peer) ----------

    @Test
    void connectAcceptorBindsThenReconnectThenDisconnect() throws Exception {
        int port = freePort();
        QuickFIXApplicationAdapter app = newApplication();
        QuickFIXAdapter adapter = new QuickFIXAdapter(app);
        UUID id = UUID.randomUUID();
        FIXSessionConfig cfg = new FIXSessionConfig(id, "acc", FIXMode.ACCEPTOR,
                FIXVersion.FIX_44, null, "SERVER", "CLIENT", "localhost", port, 30, false, false);
        try {
            adapter.connect(cfg);
            // No counterparty logged on, so isConnected is false but the session is registered.
            assertThat(adapter.isConnected(id)).isFalse();

            // Reconnect exercises the stop-existing branch without orphaning the old connector.
            adapter.connect(cfg);
            assertThat(adapter.isConnected(id)).isFalse();
        } finally {
            adapter.disconnect(id);
        }
        assertThat(adapter.isConnected(id)).isFalse();
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static Object readField(Object target, String name) throws Exception {
        Field f = QuickFIXAdapter.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
