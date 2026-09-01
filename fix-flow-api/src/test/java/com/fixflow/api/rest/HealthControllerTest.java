package com.fixflow.api.rest;

import com.fixflow.api.config.DatabaseAvailability;
import com.fixflow.api.rest.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.nio.channels.ClosedChannelException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #103: the store dying must be observable on one cheap endpoint instead of only by
 * watching every other endpoint start failing.
 */
class HealthControllerTest {

    private DataSource dataSourceAnswering(boolean hasRow) throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(hasRow);
        return ds;
    }

    @Test
    void reportsUpWhenTheProbeQuerySucceeds() throws Exception {
        var controller = new HealthController(dataSourceAnswering(true), new DatabaseAvailability());

        ResponseEntity<HealthResponse> r = controller.health();

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().status()).isEqualTo("UP");
        assertThat(r.getBody().reason()).isNull();
    }

    @Test
    void reportsDownWhenTheProbeQueryFails() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(
            new SQLNonTransientConnectionException("connection is broken"));
        DatabaseAvailability availability = new DatabaseAvailability();
        var controller = new HealthController(ds, availability);

        ResponseEntity<HealthResponse> r = controller.health();

        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getBody().status()).isEqualTo("DOWN");
        assertThat(r.getBody().reason()).contains("connection is broken");
        // A failure seen here must latch, exactly as one seen on a business endpoint does.
        assertThat(availability.isUp()).isFalse();
    }

    @Test
    void reportsDownWithoutProbingOnceTheStoreHasAlreadyDied() throws Exception {
        DatabaseAvailability availability = new DatabaseAvailability();
        availability.recordIfFatal(new ClosedChannelException());
        DataSource ds = mock(DataSource.class);   // getConnection() is never stubbed on purpose
        var controller = new HealthController(ds, availability);

        ResponseEntity<HealthResponse> r = controller.health();

        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getBody().reason()).contains("ClosedChannelException");
    }

    @Test
    void reportsDownWhenTheProbeReturnsNoRow() throws Exception {
        var controller = new HealthController(dataSourceAnswering(false), new DatabaseAvailability());

        assertThat(controller.health().getStatusCode().value()).isEqualTo(503);
    }
}
