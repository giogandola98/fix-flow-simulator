package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@EnableAutoConfiguration
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
class ScenarioPersistenceTest {

    @Configuration
    static class TestConfig {}

    @Autowired
    JpaScenarioRepository repo;

    @Test
    void savesAndRetrievesScenarioEntity() {
        ScenarioEntity e = new ScenarioEntity();
        e.setId(UUID.randomUUID());
        e.setName("demo");
        e.setVersion("1.0");
        e.setYamlDsl("name: demo\n");
        repo.save(e);

        var loaded = repo.findById(e.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("demo");
        assertThat(loaded.getYamlDsl()).contains("demo");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }
}
