package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ExecutionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaExecutionEventRepository extends JpaRepository<ExecutionEventEntity, UUID> {
    List<ExecutionEventEntity> findByExecutionIdOrderByTimestampAsc(UUID executionId);
}
