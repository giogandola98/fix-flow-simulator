package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.dataset.DataSet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataSetRepositoryPort {
    DataSet save(DataSet dataSet);
    Optional<DataSet> findById(UUID id);
    Optional<DataSet> findByName(String name);
    List<DataSet> findAll();
    void delete(UUID id);
}
