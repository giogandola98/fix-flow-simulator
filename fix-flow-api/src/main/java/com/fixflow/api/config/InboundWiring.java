package com.fixflow.api.config;

import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.engine.fix.MessageRouter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class InboundWiring {
    private final FIXSessionPort port;
    private final MessageRouter router;

    public InboundWiring(FIXSessionPort port, MessageRouter router) {
        this.port = port;
        this.router = router;
    }

    @PostConstruct
    void wire() { port.setInboundListener(router); }
}
