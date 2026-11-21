package com.fixflow.api.config;

import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.engine.fix.FIXSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Connects every session flagged {@code autoConnect} once the application is ready. Acceptors go
 * first so a loopback initiator in the same instance finds its listener; a failing session is
 * logged and does not stop the others.
 */
@Component
public class SessionAutoConnector {

    private static final Logger log = LoggerFactory.getLogger(SessionAutoConnector.class);

    private final FIXSessionRepositoryAdapter sessionRepo;
    private final FIXSessionManager manager;

    public SessionAutoConnector(FIXSessionRepositoryAdapter sessionRepo, FIXSessionManager manager) {
        this.sessionRepo = sessionRepo;
        this.manager = manager;
    }

    @EventListener(ApplicationReadyEvent.class)
    void connectAll() {
        var sessions = sessionRepo.findAll().stream()
            .filter(FIXSessionConfig::autoConnect)
            .sorted(Comparator.comparingInt((FIXSessionConfig c) -> c.mode() == FIXMode.ACCEPTOR ? 0 : 1))
            .toList();
        for (FIXSessionConfig cfg : sessions) {
            try {
                manager.connect(cfg);
                log.info("Auto-connected session '{}'", cfg.name());
            } catch (Exception e) {
                log.warn("Auto-connect of session '{}' failed: {}", cfg.name(), e.toString(), e);
            }
        }
    }
}
