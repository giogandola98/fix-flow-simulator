package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.FIXSessionEntity;
import com.fixflow.adapters.persistence.jpa.JpaFIXSessionRepository;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FIXSessionRepositoryAdapter implements FIXSessionRepositoryPort {

    private final JpaFIXSessionRepository repo;

    public FIXSessionRepositoryAdapter(JpaFIXSessionRepository repo) { this.repo = repo; }

    @Override
    @Transactional
    public FIXSessionConfig save(FIXSessionConfig cfg) {
        FIXSessionEntity e = repo.findById(cfg.id()).orElseGet(FIXSessionEntity::new);
        e.setId(cfg.id());
        e.setName(cfg.name());
        e.setMode(cfg.mode());
        e.setFixVersion(cfg.fixVersion());
        e.setDefaultApplVerID(cfg.defaultApplVerID());
        e.setSenderCompID(cfg.senderCompID());
        e.setTargetCompID(cfg.targetCompID());
        e.setHost(cfg.host());
        e.setPort(cfg.port());
        e.setHeartbeatInterval(cfg.heartbeatInterval());
        e.setResetOnLogon(cfg.resetOnLogon());
        e.setResetOnLogout(cfg.resetOnLogout());
        e.setAppDataDictionary(cfg.appDataDictionary());
        e.setTransportDataDictionary(cfg.transportDataDictionary());
        e.setAutoConnect(cfg.autoConnect());
        e.setPackId(cfg.packId());
        repo.save(e);
        return cfg;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FIXSessionConfig> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FIXSessionConfig> findAll() {
        return repo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) { repo.deleteById(id); }

    private FIXSessionConfig toDomain(FIXSessionEntity e) {
        return new FIXSessionConfig(
                e.getId(), e.getName(), e.getMode(), e.getFixVersion(),
                e.getDefaultApplVerID(), e.getSenderCompID(), e.getTargetCompID(),
                e.getHost(), e.getPort(), e.getHeartbeatInterval(),
                e.isResetOnLogon(), e.isResetOnLogout(),
                e.getAppDataDictionary(), e.getTransportDataDictionary(),
                e.isAutoConnect(), e.getPackId());
    }
}
