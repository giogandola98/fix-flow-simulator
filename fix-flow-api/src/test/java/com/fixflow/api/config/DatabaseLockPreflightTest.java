package com.fixflow.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Issue #103. The 90020 path itself needs a genuinely separate JVM — H2's file lock is held per
 * process, so two connections from this one would both succeed — and is covered by
 * {@link DatabaseInUseFailureAnalyzerTest} plus a manual two-instance run. What is verified here
 * is everything around it: the probe must be silent when it has nothing to say, and must never
 * turn an unrelated driver error into a bogus "already in use".
 */
class DatabaseLockPreflightTest {

    private final DatabaseLockPreflight preflight = new DatabaseLockPreflight();

    private void run(String url) {
        MockEnvironment environment = new MockEnvironment();
        if (url != null) {
            environment.setProperty("spring.datasource.url", url);
        }
        preflight.postProcessEnvironment(environment, null);
    }

    @Test
    void probesAReachableFileDatabaseAndLeavesItUsable(@TempDir Path dir) {
        String url = "jdbc:h2:file:" + dir.resolve("probe").toString().replace('\\', '/');

        assertThatCode(() -> run(url)).doesNotThrowAnyException();

        // The probe really connected — H2 created the store — and released it again.
        assertThat(new File(dir.toFile(), "probe.mv.db")).exists();
        assertThatCode(() -> run(url)).doesNotThrowAnyException();
    }

    @Test
    void skipsInMemoryUrls() {
        // Tests run on jdbc:h2:mem; no other process can hold one, and connecting would create it.
        assertThatCode(() -> run("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")).doesNotThrowAnyException();
    }

    @Test
    void skipsWhenNoDatasourceUrlIsConfigured() {
        assertThatCode(() -> run(null)).doesNotThrowAnyException();
    }

    @Test
    void recognisesBothShapesOfTheAlreadyOpenError() {
        // H2 2.2.224 throws 90020 through JDBC...
        assertThat(DatabaseLockPreflight.meansAlreadyOpen(
            new SQLException("Database may be already in use", "90020", 90020))).isTrue();

        // ...while the same underlying failure is also seen as a plain "General error" (50000)
        // wrapping the MVStore message; the H2 trace file records exactly that form.
        SQLException generalError = new SQLException(
            "General error: \"org.h2.mvstore.MVStoreException: The file is locked: "
                + "./data/fixflow.mv.db [2.2.224/7]\"", "50000", 50000);
        assertThat(DatabaseLockPreflight.meansAlreadyOpen(generalError)).isTrue();
    }

    @Test
    void doesNotMistakeAnOrdinaryErrorForAnAlreadyOpenStore() {
        assertThat(DatabaseLockPreflight.meansAlreadyOpen(
            new SQLException("Syntax error in SQL statement", "42000", 42000))).isFalse();
    }

    @Test
    void doesNotReinterpretUnrelatedDriverErrors(@TempDir Path dir) {
        // IFEXISTS on a database that does not exist fails with 90146, not 90020. The probe must
        // stay out of the way and let normal startup report it.
        String url = "jdbc:h2:file:" + dir.resolve("absent").toString().replace('\\', '/')
            + ";IFEXISTS=TRUE";

        assertThatCode(() -> run(url)).doesNotThrowAnyException();
    }
}
