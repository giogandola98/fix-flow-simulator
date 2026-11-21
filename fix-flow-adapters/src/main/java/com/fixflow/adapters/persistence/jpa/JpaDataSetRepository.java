package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.DataSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaDataSetRepository extends JpaRepository<DataSetEntity, UUID> {
    Optional<DataSetEntity> findByName(String name);
}
