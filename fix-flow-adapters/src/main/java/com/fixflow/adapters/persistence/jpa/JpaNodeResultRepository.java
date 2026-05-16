package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.NodeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaNodeResultRepository extends JpaRepository<NodeResultEntity, UUID> {
    List<NodeResultEntity> findByExecutionIdOrderByStartTimeAsc(UUID executionId);
}
