// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api.exception;

/**
 * Thrown at startup when the configured H2 file database is already open by another process.
 * Carries no behaviour of its own — it exists so
 * {@code DatabaseInUseFailureAnalyzer} has an unambiguous type to match on.
 */
public class DatabaseInUseException extends RuntimeException {

    private final String jdbcUrl;

    public DatabaseInUseException(String jdbcUrl, Throwable cause) {
        super("Database already in use: " + jdbcUrl, cause);
        this.jdbcUrl = jdbcUrl;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }
}
