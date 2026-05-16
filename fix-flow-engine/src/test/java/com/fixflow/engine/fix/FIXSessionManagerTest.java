package com.fixflow.engine.fix;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FIXSessionManagerTest {

    @Test
    void connectSendDisconnectLifecycle() {
        FakeFixAdapter fake = new FakeFixAdapter();
        FIXSessionManager mgr = new FIXSessionManager(fake);

        FIXSessionConfig cfg = new FIXSessionConfig(
                UUID.randomUUID(), "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "SENDER", "TARGET", "localhost", 9876, 30, 5, true, false);

        mgr.connect(cfg);
        assertThat(mgr.isConnected(cfg.id())).isTrue();

        mgr.send(cfg.id(), Map.of(35, "D", 11, "CL-1"));
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0)).containsEntry(35, "D");

        mgr.disconnect(cfg.id());
        assertThat(mgr.isConnected(cfg.id())).isFalse();
    }
}
