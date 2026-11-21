package com.fixflow.adapters.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.persistence.entity.DataSetEntity;
import com.fixflow.adapters.persistence.jpa.JpaDataSetRepository;
import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DataSetRepositoryAdapter implements DataSetRepositoryPort {

    private final JpaDataSetRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public DataSetRepositoryAdapter(JpaDataSetRepository repo) { this.repo = repo; }

    @Override
    @Transactional
    public DataSet save(DataSet ds) {
        UUID id = ds.id() != null ? ds.id() : UUID.randomUUID();
        DataSetEntity e = repo.findById(id).orElseGet(DataSetEntity::new);
        e.setId(id);
        e.setName(ds.name());
        e.setPackId(ds.packId());
        try {
            e.setColumnsJson(json.writeValueAsString(ds.columns()));
            e.setRowsJson(json.writeValueAsString(ds.rows()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialise data set " + ds.name(), ex);
        }
        repo.saveAndFlush(e);
        return new DataSet(id, ds.name(), ds.columns(), ds.rows(), ds.packId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DataSet> findById(UUID id) { return repo.findById(id).map(this::toDomain); }

    @Override
    @Transactional(readOnly = true)
    public Optional<DataSet> findByName(String name) { return repo.findByName(name).map(this::toDomain); }

    @Override
    @Transactional(readOnly = true)
    public List<DataSet> findAll() { return repo.findAll().stream().map(this::toDomain).toList(); }

    @Override
    @Transactional
    public void delete(UUID id) { repo.deleteById(id); }

    private DataSet toDomain(DataSetEntity e) {
        try {
            List<String> columns = json.readValue(e.getColumnsJson(), new TypeReference<>() {});
            List<List<String>> rows = json.readValue(e.getRowsJson(), new TypeReference<>() {});
            return new DataSet(e.getId(), e.getName(), columns, rows, e.getPackId());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("corrupt data set " + e.getName(), ex);
        }
    }
}
