// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api.rest;

import com.fixflow.api.config.DatabaseAvailability;
import com.fixflow.api.rest.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Liveness probe for the persistence layer (issue #103).
 *
 * <p>Before this existed the only way to discover that the H2 store had died was to watch every
 * endpoint start answering 500. A harness can now poll this one cheap endpoint and stop a run
 * with a clear reason instead of reporting an unexplained mass failure.
 *
 * <p>It is deliberately a real round-trip to the database rather than a cached flag: a store
 * that has broken but has not yet been touched by any request would otherwise still report UP.
 * The latched {@link DatabaseAvailability} flag is checked first only as a short-circuit, since
 * once the store is gone it never comes back.
 */
@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final DataSource dataSource;
    private final DatabaseAvailability database;

    HealthController(DataSource dataSource, DatabaseAvailability database) {
        this.dataSource = dataSource;
        this.database = database;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        if (!database.isUp()) {
            return ResponseEntity.status(503).body(HealthResponse.down(database.failureReason()));
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            if (!rs.next()) {
                return ResponseEntity.status(503).body(HealthResponse.down("probe query returned no row"));
            }
        } catch (Exception e) {
            database.recordIfFatal(e);
            String reason = database.isUp() ? e.getClass().getSimpleName() + ": " + e.getMessage()
                                            : database.failureReason();
            return ResponseEntity.status(503).body(HealthResponse.down(reason));
        }
        return ResponseEntity.ok(HealthResponse.up());
    }
}
