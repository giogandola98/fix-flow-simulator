package com.fixflow.api.config;

import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.engine.fix.FIXSessionManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionAutoConnectorTest {

    private final FIXSessionRepositoryAdapter repo = mock(FIXSessionRepositoryAdapter.class);
    private final FIXSessionManager manager = mock(FIXSessionManager.class);
    private final SessionAutoConnector connector = new SessionAutoConnector(repo, manager);

    private static FIXSessionConfig session(String name, FIXMode mode, boolean auto) {
        return new FIXSessionConfig(UUID.randomUUID(), name, mode, FIXVersion.FIX_44, null,
            "S", "T", "localhost", 9000, 30, true, false, null, null, auto, null);
    }

    @Test
    void acceptors_first_then_initiators() {
        var i1 = session("i1", FIXMode.INITIATOR, true);
        var a1 = session("a1", FIXMode.ACCEPTOR, true);
        var i2 = session("i2", FIXMode.INITIATOR, true);
        var a2 = session("a2", FIXMode.ACCEPTOR, true);
        when(repo.findAll()).thenReturn(List.of(i1, a1, i2, a2));

        connector.connectAll();

        InOrder order = inOrder(manager);
        order.verify(manager).connect(a1);
        order.verify(manager).connect(a2);
        order.verify(manager).connect(i1);
        order.verify(manager).connect(i2);
    }

    @Test
    void failure_of_one_does_not_stop_others() {
        var a1 = session("a1", FIXMode.ACCEPTOR, true);
        var i1 = session("i1", FIXMode.INITIATOR, true);
        when(repo.findAll()).thenReturn(List.of(a1, i1));
        doThrow(new IllegalStateException("port busy")).when(manager).connect(a1);

        connector.connectAll();

        verify(manager).connect(a1);
        verify(manager).connect(i1);
    }

    @Test
    void sessions_without_flag_are_not_connected() {
        var off = session("off", FIXMode.ACCEPTOR, false);
        var on = session("on", FIXMode.INITIATOR, true);
        when(repo.findAll()).thenReturn(List.of(off, on));

        connector.connectAll();

        verify(manager, never()).connect(off);
        verify(manager).connect(on);
        verify(manager, times(1)).connect(any());
    }
}
