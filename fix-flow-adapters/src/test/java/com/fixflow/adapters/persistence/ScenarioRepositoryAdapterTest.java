package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.adapters.persistence.jpa.JpaScenarioVersionRepository;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableAutoConfiguration
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
class ScenarioRepositoryAdapterTest {

    @Configuration
    static class TestConfig {
        @Bean ScenarioDslParser parser() { return new ScenarioDslParser(); }
        @Bean ScenarioRepositoryAdapter adapter(JpaScenarioRepository r,
                                                JpaScenarioVersionRepository v,
                                                ScenarioDslParser p) {
            return new ScenarioRepositoryAdapter(r, v, p);
        }
    }

    @Autowired ScenarioRepositoryAdapter adapter;

    @Test
    void savesAndRetrievesScenarioDomainObject() {
        UUID id = UUID.randomUUID();
        Scenario s = new Scenario(id, "demo", "desc", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(new ScenarioNode("n1", "Start", NodeType.START, Map.of(), null, null, null, null, null)),
                List.of(), Map.of(), null);

        adapter.save(s);
        Scenario loaded = adapter.findById(id).orElseThrow();

        assertThat(loaded.name()).isEqualTo("demo");
        assertThat(loaded.nodes()).hasSize(1);
        assertThat(loaded.nodes().get(0).type()).isEqualTo(NodeType.START);
    }
}
