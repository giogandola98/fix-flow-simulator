package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.session.FIXSessionConfig;

import java.util.Map;
import java.util.UUID;

public interface FIXSessionPort {
    void connect(FIXSessionConfig config);
    void disconnect(UUID sessionId);

    void sendMessage(UUID sessionId, FIXMessageData message);

    /** Legacy no-group form. Kept so existing callers and fakes compile unchanged. */
    default void sendMessage(UUID sessionId, Map<Integer, String> fields) {
        sendMessage(sessionId, FIXMessageData.ofFields(fields));
    }

    boolean isConnected(UUID sessionId);
    void setInboundListener(InboundMessageListener listener);
}
