package com.fixflow.adapters.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.persistence.entity.PackEntity;
import com.fixflow.adapters.persistence.jpa.JpaPackRepository;
import com.fixflow.core.domain.pack.InstalledPack;
import com.fixflow.core.ports.outbound.PackRepositoryPort;
import com.fixflow.engine.pack.PackManifestCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PackRepositoryAdapter implements PackRepositoryPort {

    private final JpaPackRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public PackRepositoryAdapter(JpaPackRepository repo) { this.repo = repo; }

    @Override
    @Transactional
    public InstalledPack save(InstalledPack p) {
        PackEntity e = repo.findById(p.id()).orElseGet(PackEntity::new);
        e.setId(p.id());
        e.setName(p.manifest().name());
        e.setVersion(p.manifest().version());
        e.setProvider(p.manifest().provider());
        e.setManifestYaml(PackManifestCodec.write(p.manifest(), Map.of()));
        try {
            e.setValuesJson(json.writeValueAsString(new LinkedHashMap<>(p.values())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialise values of pack " + p.id(), ex);
        }
        e.setInstalledAt(p.installedAt());
        e.setUpdatedAt(p.updatedAt());
        repo.save(e);
        return p;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstalledPack> findById(String id) { return repo.findById(id).map(this::toDomain); }

    @Override
    @Transactional(readOnly = true)
    public List<InstalledPack> findAll() {
        return repo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(String id) { repo.deleteById(id); }

    private InstalledPack toDomain(PackEntity e) {
        Map<String, String> values;
        try {
            values = e.getValuesJson() == null ? Map.of()
                : json.readValue(e.getValuesJson(), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot read values of pack " + e.getId(), ex);
        }
        return new InstalledPack(PackManifestCodec.parse(e.getManifestYaml()), values,
            e.getInstalledAt(), e.getUpdatedAt());
    }
}
