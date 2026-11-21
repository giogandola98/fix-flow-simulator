package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.pack.InstalledPack;

import java.util.List;
import java.util.Optional;

public interface PackRepositoryPort {
    InstalledPack save(InstalledPack pack);
    Optional<InstalledPack> findById(String id);
    List<InstalledPack> findAll();
    void delete(String id);
}
