package com.fixflow.api.config;

import com.fixflow.api.exception.DatabaseInUseException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #103: with AUTO_SERVER gone, a second instance fails at startup — that failure has to
 * say what happened rather than be buried in Hibernate's "Unable to determine Dialect".
 */
class DatabaseInUseFailureAnalyzerTest {

    private final DatabaseInUseFailureAnalyzer analyzer = new DatabaseInUseFailureAnalyzer();

    @Test
    void explainsDatabaseAlreadyInUse() {
        SQLException driverError = new SQLException("Database may be already in use", "90020", 90020);
        DatabaseInUseException cause =
            new DatabaseInUseException("jdbc:h2:file:./data/fixflow", driverError);

        FailureAnalysis analysis = analyzer.analyze(new IllegalStateException("wrapped", cause));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
            .contains("jdbc:h2:file:./data/fixflow", "already open by another process");
        assertThat(analysis.getAction()).contains("-Dserver.port=", ".lock.db");
        assertThat(analysis.getCause()).isSameAs(cause);
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertThat(analyzer.analyze(new IllegalStateException("boom", new SQLException("syntax"))))
            .isNull();
    }

    @Test
    void bothStartupHooksAreRegisteredSoSpringBootActuallyUsesThem() {
        // Boot's own analyzers that need constructor arguments cannot be built here; skipping
        // them is fine, the point is that ours are on the list and instantiable.
        SpringFactoriesLoader loader =
            SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader());
        SpringFactoriesLoader.FailureHandler ignore = (type, implementationName, failure) -> { };

        List<FailureAnalyzer> analyzers = loader.load(FailureAnalyzer.class, ignore);
        List<org.springframework.boot.env.EnvironmentPostProcessor> processors =
            loader.load(org.springframework.boot.env.EnvironmentPostProcessor.class, ignore);

        assertThat(analyzers).hasAtLeastOneElementOfType(DatabaseInUseFailureAnalyzer.class);
        assertThat(processors).hasAtLeastOneElementOfType(DatabaseLockPreflight.class);
    }
}
