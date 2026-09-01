// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api.config;

import com.fixflow.api.exception.DatabaseInUseException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens and immediately closes one connection to the configured H2 <em>file</em> database
 * before anything else starts, so that "another process already has this database" is reported
 * as itself.
 *
 * <p>Why this exists rather than just a {@code FailureAnalyzer} on {@code SQLException}: when
 * the store is locked, Hibernate's {@code JdbcEnvironmentInitiator} catches the driver's
 * {@code SQLException} and rethrows {@code HibernateException: Unable to determine Dialect
 * without JDBC metadata} <em>without</em> chaining it. By the time the failure reaches Spring
 * Boot the real reason is gone from the exception entirely — no analyzer downstream can
 * recover it. Probing first is the only place the driver's own error is still visible.
 *
 * <p>Only H2 file URLs are probed. In-memory URLs (tests) are skipped: they cannot be held by
 * another process, and connecting to one here would needlessly create it.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} — after the environment is populated, so
 * {@code -Dspring.datasource.url} overrides are honoured, and before any bean is created.
 * Registered in {@code META-INF/spring.factories}; must keep a public no-arg constructor.
 */
public class DatabaseLockPreflight implements EnvironmentPostProcessor {

    /** H2: "Database may be already in use". */
    private static final int DATABASE_ALREADY_IN_USE = 90020;

    private static final String H2_FILE_URL_PREFIX = "jdbc:h2:file:";

    /** org.h2.mvstore text for a store file another process has locked. */
    private static final String MVSTORE_FILE_LOCKED = "The file is locked";

    /** Guard against a cyclic cause chain while scanning. */
    private static final int MAX_CAUSE_DEPTH = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith(H2_FILE_URL_PREFIX)) {
            return;
        }
        String user = environment.getProperty("spring.datasource.username", "sa");
        String password = environment.getProperty("spring.datasource.password", "");
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            // Reachable — release it again and let the real pool take over.
        } catch (SQLException e) {
            if (meansAlreadyOpen(e)) {
                throw new DatabaseInUseException(url, e);
            }
            // Anything else is not ours to interpret: let normal startup surface it.
        }
    }

    /**
     * H2 2.2.224 raises 90020 for a store another process holds, but the underlying MVStore
     * failure also shows up as a plain "General error" (50000) wrapping the same message — the
     * H2 trace file records exactly that form. Match either, so the friendly report does not
     * quietly stop working on a version or platform that takes the other path.
     */
    static boolean meansAlreadyOpen(SQLException e) {
        if (e.getErrorCode() == DATABASE_ALREADY_IN_USE) {
            return true;
        }
        Throwable cur = e;
        for (int depth = 0; cur != null && depth < MAX_CAUSE_DEPTH; depth++) {
            String message = cur.getMessage();
            if (message != null && message.contains(MVSTORE_FILE_LOCKED)) {
                return true;
            }
            Throwable next = cur.getCause();
            cur = next == cur ? null : next;
        }
        return false;
    }
}
