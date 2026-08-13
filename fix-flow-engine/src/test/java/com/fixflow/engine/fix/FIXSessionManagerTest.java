package com.fixflow.engine.fix;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.support.FakeFixAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FIXSessionManagerTest {

    private FakeFixAdapter port;
    private FIXSessionManager manager;

    @BeforeEach
    void setUp() {
        port = new FakeFixAdapter();
        manager = new FIXSessionManager(port);
    }

    private FIXSessionConfig cfg(UUID id) {
        return new FIXSessionConfig(id, "sess", FIXMode.INITIATOR, FIXVersion.FIX_44, null,
                "SENDER", "TARGET", "localhost", 9001, 30, true, false);
    }

    @Test
    void connectDelegatesToPortAndTracksSession() {
        UUID id = UUID.randomUUID();
        manager.connect(cfg(id));
        assertThat(port.isConnected(id)).isTrue();
        assertThat(manager.isConnected(id)).isTrue();
    }

    @Test
    void disconnectDelegatesToPort() {
        UUID id = UUID.randomUUID();
        manager.connect(cfg(id));
        manager.disconnect(id);
        assertThat(manager.isConnected(id)).isFalse();
    }

    @Test
    void registerListenerWiresInboundListenerIntoPort() {
        AtomicReference<String> received = new AtomicReference<>();
        InboundMessageListener listener = (sid, fields) -> received.set(sid);
        manager.registerListener(listener);
        assertThat(port.listener()).isSameAs(listener);

        UUID id = UUID.randomUUID();
        port.injectInbound(id, java.util.Map.of(35, "8"));
        assertThat(received.get()).isEqualTo(id.toString());
    }
}
