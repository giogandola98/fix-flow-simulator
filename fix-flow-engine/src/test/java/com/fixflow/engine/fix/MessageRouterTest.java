package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRouterTest {

    private CorrelationEngine correlation;
    private MessageBuffer buffer;
    private MessageRouter router;
    private static final String SESSION = "sess-1";

    @BeforeEach
    void setUp() {
        correlation = new CorrelationEngine();
        buffer = new MessageBuffer();
        router = new MessageRouter(correlation, buffer);
    }

    @Test
    void matchingMessageRoutedToCorrelationNotParked() {
        CompletableFuture<FIXMessageData> f =
                correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD1");
        router.onMessage(SESSION, Fixtures.fields(11, "ORD1"));
        assertThat(f).isCompleted();
        assertThat(buffer.size(SESSION)).isZero();
    }

    @Test
    void unmatchedMessageIsParked() {
        router.onMessage(SESSION, Fixtures.fields(11, "ORD1"));
        assertThat(buffer.size(SESSION)).isEqualTo(1);
    }

    @Test
    void drainReplaysParkedMessagesToLateRegisteredWaiter() {
        // message arrives before the waiter registers -> parked
        router.onMessage(SESSION, Fixtures.fields(11, "ORD1"));
        assertThat(buffer.size(SESSION)).isEqualTo(1);

        CompletableFuture<FIXMessageData> f =
                correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD1");
        router.drain(SESSION);
        assertThat(f).isCompleted();
        assertThat(buffer.size(SESSION)).isZero();
    }

    @Test
    void drainLeavesNonMatchingMessagesParked() {
        router.onMessage(SESSION, Fixtures.fields(11, "ORD1"));
        correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "DIFFERENT");
        router.drain(SESSION);
        assertThat(buffer.size(SESSION)).isEqualTo(1);
    }

    @Test
    void whenPausedMessagesAreParkedNotRouted() {
        correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD1");
        buffer.pause();
        router.onMessage(SESSION, Fixtures.fields(11, "ORD1"));
        assertThat(buffer.size(SESSION)).isEqualTo(1);
        assertThat(correlation.pendingCount()).isEqualTo(1); // waiter still pending
    }

    @Test
    void waiter_registering_between_the_miss_and_the_park_still_gets_the_message() {
        // Reproduces the window between "no waiter matched" and "parked": a waiter registers and
        // drains in that window (its drain finds nothing yet), and nobody drains after it.
        CompletableFuture<?>[] late = new CompletableFuture<?>[1];
        MessageBuffer racing = new MessageBuffer() {
            boolean first = true;
            @Override
            public void park(String sessionId, FIXMessageData message) {
                if (first) {
                    first = false;
                    late[0] = correlation.register("exec-late", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD1");
                    router.drain(SESSION);
                }
                super.park(sessionId, message);
            }
        };
        router = new MessageRouter(correlation, racing);
        router.onMessage(SESSION, Fixtures.fields(11, "ORD1"));
        assertThat(late[0]).isCompleted();
        assertThat(racing.size(SESSION)).isZero();
        assertThat(correlation.pendingCount()).isZero();
    }
}
