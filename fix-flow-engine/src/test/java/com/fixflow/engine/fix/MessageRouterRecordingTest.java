package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.NodeResult;
import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.SessionExecutionRegistry;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #77, second half: a FIX message that no block was waiting for used to leave no trace at
 * all — no event, no row in the FIX Messages tab — which is why the GUI showed nothing.
 */
class MessageRouterRecordingTest {

    static final class Repo implements ExecutionRepositoryPort {
        final List<ExecutionEvent> events = new CopyOnWriteArrayList<>();
        final List<FIXMessage> messages = new CopyOnWriteArrayList<>();
        public Execution save(Execution e) { return e; }
        public Optional<Execution> findById(UUID id) { return Optional.empty(); }
        public void addEvent(UUID id, ExecutionEvent e) { events.add(e); }
        public void addMessage(UUID id, FIXMessage m) { messages.add(m); }
        public void addNodeResult(UUID id, NodeResult r) { }
        public List<FIXMessage> findMessages(UUID id) { return List.copyOf(messages); }
    }

    static final class Publisher implements EventPublisherPort {
        final List<ExecutionEvent> published = new CopyOnWriteArrayList<>();
        final List<FIXMessage> publishedMessages = new CopyOnWriteArrayList<>();
        public void publish(ExecutionEvent e) { published.add(e); }
        @Override public void publishMessage(UUID id, FIXMessage m) { publishedMessages.add(m); }
    }

    private static final String SESSION = "sess-1";

    private CorrelationEngine correlation;
    private MessageBuffer buffer;
    private SessionExecutionRegistry sessions;
    private Repo repo;
    private Publisher publisher;
    private MessageRouter router;
    private UUID execution;

    @BeforeEach
    void setUp() {
        correlation = new CorrelationEngine();
        buffer = new MessageBuffer();
        sessions = new SessionExecutionRegistry();
        repo = new Repo();
        publisher = new Publisher();
        router = new MessageRouter(correlation, buffer, sessions, publisher, repo);
        execution = UUID.randomUUID();
    }

    @Test
    void unmatchedMessageIsStillLoggedAgainstTheRunningExecution() {
        sessions.register(SESSION, execution);

        router.onMessage(SESSION, Fixtures.fields(35, "D", 11, "ORD-1"));

        assertThat(repo.messages).hasSize(1);
        assertThat(repo.messages.get(0).direction()).isEqualTo(Direction.INBOUND);
        assertThat(repo.messages.get(0).rawFix()).contains("11=ORD-1");
        assertThat(publisher.publishedMessages).hasSize(1);
        assertThat(repo.events).extracting(ExecutionEvent::type)
                .containsExactly(ExecutionEventType.MESSAGE_RECEIVED);
        assertThat(repo.events.get(0).detail()).contains("no block was waiting");
        // still parked, so a block registering later can pick it up
        assertThat(buffer.size(SESSION)).isEqualTo(1);
    }

    @Test
    void matchedMessageIsLoggedOnceAndMarkedAsMatched() {
        sessions.register(SESSION, execution);
        correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD-1");

        router.onMessage(SESSION, Fixtures.fields(35, "D", 11, "ORD-1"));

        assertThat(repo.messages).hasSize(1);
        assertThat(repo.events).hasSize(1);
        assertThat(repo.events.get(0).detail()).contains("matched by a waiting block");
    }

    @Test
    void aMessageThatArrivedBeforeTheExecutionStartedIsLoggedWhenItIsDrained() {
        // nothing running on the session yet: nothing to attribute the message to
        router.onMessage(SESSION, Fixtures.fields(35, "D", 11, "ORD-1"));
        assertThat(repo.messages).isEmpty();

        sessions.register(SESSION, execution);
        correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD-1");
        router.drain(SESSION);

        assertThat(repo.messages).hasSize(1);
        assertThat(repo.events.get(0).detail()).contains("it had been buffered");
    }

    @Test
    void aMessageLoggedOnArrivalIsNotLoggedAgainWhenDrained() {
        sessions.register(SESSION, execution);
        router.onMessage(SESSION, Fixtures.fields(35, "D", 11, "ORD-1"));
        assertThat(repo.messages).hasSize(1);

        correlation.register("exec-1", SESSION, new CorrelationRule(11, "n", 11, 0), "ORD-1");
        router.drain(SESSION);

        assertThat(repo.messages).hasSize(1);
    }

    @Test
    void everyExecutionOnTheSessionSeesTheMessage() {
        UUID other = UUID.randomUUID();
        sessions.register(SESSION, execution);
        sessions.register(SESSION, other);

        router.onMessage(SESSION, Fixtures.fields(35, "D"));

        assertThat(repo.messages).hasSize(2);
        assertThat(repo.messages).extracting(FIXMessage::executionId)
                .containsExactlyInAnyOrder(execution, other);
    }

    @Test
    void anExecutionOnAnotherSessionIsNotLogged() {
        sessions.register("other-session", execution);
        router.onMessage(SESSION, Fixtures.fields(35, "D"));
        assertThat(repo.messages).isEmpty();
    }
}
