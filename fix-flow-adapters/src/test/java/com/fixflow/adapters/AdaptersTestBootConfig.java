package com.fixflow.adapters;

import com.fixflow.adapters.persistence.ExecutionRepositoryAdapter;
import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.adapters.persistence.ScenarioRepositoryAdapter;
import com.fixflow.adapters.persistence.jpa.*;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot configuration for persistence-slice tests. Not the full application
 * context: only JPA auto-config, the adapter entities/repositories, and the port adapters.
 * Discovered automatically by {@code @DataJpaTest} classes in subpackages.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
public class AdaptersTestBootConfig {

    @Bean
    ScenarioDslParser scenarioDslParser() {
        return new ScenarioDslParser();
    }

    @Bean
    ScenarioRepositoryAdapter scenarioRepositoryAdapter(JpaScenarioRepository r,
                                                        JpaScenarioVersionRepository v,
                                                        ScenarioDslParser p) {
        return new ScenarioRepositoryAdapter(r, v, p);
    }

    @Bean
    ExecutionRepositoryAdapter executionRepositoryAdapter(JpaExecutionRepository e,
                                                          JpaExecutionEventRepository ev,
                                                          JpaFIXMessageRepository m,
                                                          JpaNodeResultRepository n) {
        return new ExecutionRepositoryAdapter(e, ev, m, n);
    }

    @Bean
    FIXSessionRepositoryAdapter fixSessionRepositoryAdapter(JpaFIXSessionRepository r) {
        return new FIXSessionRepositoryAdapter(r);
    }
}
