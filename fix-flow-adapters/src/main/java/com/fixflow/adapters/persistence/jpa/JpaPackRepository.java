package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.PackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPackRepository extends JpaRepository<PackEntity, String> {
}
