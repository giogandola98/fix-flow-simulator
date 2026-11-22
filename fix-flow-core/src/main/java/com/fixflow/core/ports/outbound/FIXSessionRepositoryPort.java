package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.session.FIXSessionConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FIXSessionRepositoryPort {
    FIXSessionConfig save(FIXSessionConfig config);
    Optional<FIXSessionConfig> findById(UUID id);
    List<FIXSessionConfig> findAll();
    void delete(UUID id);
}
