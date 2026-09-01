// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api.config;

import com.fixflow.api.exception.DatabaseInUseException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns {@link DatabaseInUseException} — raised by {@link DatabaseLockPreflight} — into an
 * actionable startup message instead of a wall of stack trace.
 *
 * <p>The datasource no longer sets {@code AUTO_SERVER=TRUE} (issue #103): allowing a second JVM
 * to open the same store is what made it possible for the file channel to be pulled out from
 * under a running instance, after which every statement failed until the database was deleted.
 * With the flag gone, a second instance fails cleanly at startup — and this analyzer makes that
 * failure say what actually happened and what to do about it.
 *
 * <p>Registered through {@code META-INF/spring.factories}; failure analyzers are created before
 * the context exists, so this class must not be a bean and must have a no-arg constructor.
 */
public class DatabaseInUseFailureAnalyzer extends AbstractFailureAnalyzer<DatabaseInUseException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, DatabaseInUseException cause) {
        return new FailureAnalysis(
            "The FIX Flow Simulator database (" + cause.jdbcUrl()
                + ") is already open by another process.",
            "Only one simulator instance can use a database at a time. Stop the instance that is "
                + "already running, or start this one with a different port and database, for example:\n"
                + "    -Dserver.port=9999 -Dspring.datasource.url=jdbc:h2:file:./data/fixflow-9999\n"
                + "If no other instance is running, a previous run was killed and left a stale "
                + "lock: delete the matching .lock.db file and start again.",
            cause);
    }
}
