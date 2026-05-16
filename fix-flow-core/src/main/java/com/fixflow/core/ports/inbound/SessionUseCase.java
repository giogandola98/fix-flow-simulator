package com.fixflow.core.ports.inbound;

import com.fixflow.core.domain.session.FIXSessionConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionUseCase {
    FIXSessionConfig save(FIXSessionConfig config);
    Optional<FIXSessionConfig> findById(UUID id);
    List<FIXSessionConfig> findAll();
    void connect(UUID id);
    void disconnect(UUID id);
    boolean getStatus(UUID id);
}
