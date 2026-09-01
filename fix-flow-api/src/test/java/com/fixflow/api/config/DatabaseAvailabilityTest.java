package com.fixflow.api.config;

import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.sql.SQLNonTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #103: a closed MVStore channel must be recognised as a permanent failure, and only
 * that class of failure — an ordinary bad statement must not take the whole API down.
 */
class DatabaseAvailabilityTest {

    /** Stands in for org.h2.mvstore.MVStoreException, which is matched by simple name. */
    static class MVStoreException extends RuntimeException {
        MVStoreException(String message, Throwable cause) { super(message, cause); }
    }

    @Test
    void startsUp() {
        DatabaseAvailability availability = new DatabaseAvailability();
        assertThat(availability.isUp()).isTrue();
        assertThat(availability.failureReason()).isNull();
    }

    @Test
    void ordinaryExceptionLeavesItUp() {
        DatabaseAvailability availability = new DatabaseAvailability();

        assertThat(availability.recordIfFatal(new IllegalStateException("bad row"))).isFalse();

        assertThat(availability.isUp()).isTrue();
    }

    @Test
    void closedChannelDeepInTheChainFlipsItDown() {
        DatabaseAvailability availability = new DatabaseAvailability();
        // The shape seen in the issue: Hibernate -> H2 -> MVStore -> ClosedChannelException.
        Exception ex = new RuntimeException("could not execute statement",
            new MVStoreException("Writing to FileChannelImpl failed", new ClosedChannelException()));

        assertThat(availability.recordIfFatal(ex)).isTrue();

        assertThat(availability.isUp()).isFalse();
        assertThat(availability.failureReason()).contains("MVStoreException", "FileChannelImpl");
    }

    @Test
    void connectionLossFlipsItDown() {
        DatabaseAvailability availability = new DatabaseAvailability();

        assertThat(availability.recordIfFatal(
            new SQLNonTransientConnectionException("connection is broken"))).isTrue();

        assertThat(availability.isUp()).isFalse();
    }

    @Test
    void failureLatchesSoLaterHarmlessExceptionsStillReportDown() {
        DatabaseAvailability availability = new DatabaseAvailability();
        availability.recordIfFatal(new ClosedChannelException());
        String first = availability.failureReason();

        // Once the store is gone every subsequent request is doomed too, whatever it threw.
        assertThat(availability.recordIfFatal(new IllegalStateException("anything"))).isTrue();

        assertThat(availability.failureReason()).isEqualTo(first);
    }

    @Test
    void cyclicCauseChainTerminates() {
        DatabaseAvailability availability = new DatabaseAvailability();
        Exception a = new RuntimeException("a");
        Exception b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);   // Throwable only rejects self-causation, so this cycle is buildable

        assertThat(availability.recordIfFatal(a)).isFalse();
    }

    @Test
    void reasonFallsBackToTypeNameWhenMessageIsBlank() {
        assertThat(DatabaseAvailability.describeFatal(new ClosedChannelException()))
            .isEqualTo("ClosedChannelException");
    }
}
