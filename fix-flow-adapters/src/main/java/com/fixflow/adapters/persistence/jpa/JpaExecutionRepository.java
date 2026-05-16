package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaExecutionRepository extends JpaRepository<ExecutionEntity, UUID> { }
