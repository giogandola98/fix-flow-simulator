package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.DataSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaDataSetRepository extends JpaRepository<DataSetEntity, UUID> {
    /**
     * First match by name, oldest first. Names are unique once {@code DataSetNameIndex} has
     * created its index; a database that held duplicates before that still answers one
     * deterministic row instead of failing on a non-unique result.
     */
    Optional<DataSetEntity> findFirstByNameOrderByCreatedAtAscIdAsc(String name);
}
