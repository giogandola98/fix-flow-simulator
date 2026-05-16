package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.FIXMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaFIXMessageRepository extends JpaRepository<FIXMessageEntity, UUID> {
    List<FIXMessageEntity> findByExecutionIdOrderByReceivedAtAsc(UUID executionId);
}
