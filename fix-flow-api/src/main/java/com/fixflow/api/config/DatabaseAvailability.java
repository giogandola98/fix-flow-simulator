// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks whether the embedded H2 store is still usable.
 *
 * <p>Motivation (issue #103): once the MVStore file channel is closed underneath Hibernate —
 * a second JVM opening the same file, a killed process, a store-level I/O error — every
 * subsequent statement fails forever. The old behaviour was a blanket
 * {@code 500 Internal Server Error} from {@link GlobalExceptionHandler} on every endpoint,
 * with no signal that the process was permanently broken rather than momentarily unhappy.
 *
 * <p>This class recognises that class of failure by walking the cause chain, latches it
 * (the store never recovers on its own), and lets the API answer {@code 503} with the real
 * cause instead. The latch is one-way on purpose: an automated harness must be able to tell
 * "restart me" apart from "that one request failed".
 *
 * <p>Fatal types are matched by class <em>name</em> rather than by {@code instanceof} so this
 * module keeps no compile-time dependency on H2 or Hibernate internals; the store is an
 * adapter-level concern and the exact exception types differ across H2/Hibernate versions.
 */
@Component
public class DatabaseAvailability {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAvailability.class);

    /**
     * Exception types that mean the store itself is gone, not that one statement was invalid.
     * Matched against the simple name of every throwable in the cause chain.
     */
    private static final List<String> FATAL_TYPES = List.of(
        "MVStoreException",                  // org.h2.mvstore — store-level I/O failure
        "ClosedChannelException",            // java.nio.channels — file channel already closed
        "SQLNonTransientConnectionException",// java.sql — connection cannot be re-established
        "JDBCConnectionException",           // org.hibernate.exception — pool/connection lost
        "CannotGetJdbcConnectionException",  // org.springframework.jdbc
        "DataAccessResourceFailureException" // org.springframework.dao
    );

    /** Guard against a cyclic cause chain while scanning. */
    private static final int MAX_CAUSE_DEPTH = 32;

    /** Non-null once the store has been declared unusable; holds the human-readable cause. */
    private final AtomicReference<String> failure = new AtomicReference<>();

    /**
     * Records {@code t} if it indicates an unusable store.
     *
     * @return {@code true} when the store is unusable — either because {@code t} says so or
     *         because an earlier throwable already latched the failure.
     */
    public boolean recordIfFatal(Throwable t) {
        String cause = describeFatal(t);
        if (cause != null && failure.compareAndSet(null, cause)) {
            log.error("Database store is no longer usable ({}). Every request will now answer 503 "
                + "until the simulator is restarted.", cause, t);
        }
        return !isUp();
    }

    /** {@code true} while the store has never reported a fatal failure. */
    public boolean isUp() {
        return failure.get() == null;
    }

    /** Human-readable cause of the failure, or {@code null} while the store is up. */
    public String failureReason() {
        return failure.get();
    }

    /**
     * Walks the cause chain and returns a description of the first fatal link, or {@code null}
     * when nothing in the chain indicates a dead store.
     */
    static String describeFatal(Throwable t) {
        // Bounded walk: a self-referential or cyclic cause chain must not spin forever.
        Throwable cur = t;
        for (int depth = 0; cur != null && depth < MAX_CAUSE_DEPTH; depth++) {
            String simpleName = cur.getClass().getSimpleName();
            if (FATAL_TYPES.contains(simpleName)) {
                String message = cur.getMessage();
                return message == null || message.isBlank() ? simpleName : simpleName + ": " + message;
            }
            Throwable next = cur.getCause();
            cur = next == cur ? null : next;
        }
        return null;
    }
}
