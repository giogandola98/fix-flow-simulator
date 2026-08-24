package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.*;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.FakeFixAdapter;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExecutionManagerPersistenceTest {

    static final class FakeRepo implements ExecutionRepositoryPort {
        final List<Execution> saved = new CopyOnWriteArrayList<>();
        final List<ExecutionEvent> events = new CopyOnWriteArrayList<>();
        final List<FIXMessage> messages = new CopyOnWriteArrayList<>();
        final List<NodeResult> nodeResults = new CopyOnWriteArrayList<>();
        public Execution save(Execution e) { saved.add(e); return e; }
        public Optional<Execution> findById(UUID id) { return Optional.empty(); }
        public void addEvent(UUID id, ExecutionEvent e) { events.add(e); }
        public void addMessage(UUID id, FIXMessage m) { messages.add(m); }
        public void addNodeResult(UUID id, NodeResult r) { nodeResults.add(r); }
        public List<FIXMessage> findMessages(UUID id) { return List.copyOf(messages); }
    }

    static final class FakePublisher implements EventPublisherPort {
        final List<ExecutionEvent> published = new CopyOnWriteArrayList<>();
        final List<FIXMessage> publishedMessages = new CopyOnWriteArrayList<>();
        public void publish(ExecutionEvent e) { published.add(e); }
        @Override public void publishMessage(UUID id, FIXMessage m) { publishedMessages.add(m); }
    }

    private final ScenarioRegistry registry = new ScenarioRegistry();

    @Test
    void persistsEventsNodeResultsAndBothMessageDirections() {
        FakeRepo repo = new FakeRepo();
        FakePublisher publisher = new FakePublisher();
        FakeFixAdapter adapter = new FakeFixAdapter();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, new MessageBuffer());

        NodeDispatcher d = new NodeDispatcher(List.of(
                new StartHandler(), new EndHandler(),
                new SendFIXHandler(adapter, new VariableResolver()),
                new ExpectFIXHandler(correlation, router)));
        NodeWalker walker = new NodeWalker(d);
        ExecutionManager mgr = new ExecutionManager(registry, walker, repo, publisher);

        Scenario s = scenario(UUID.randomUUID(), "persist", List.of(),
                start("send"),
                node("send", NodeType.SEND_FIX).cfg("msgType", "D").cfg("fields", Map.of("11", "ORD1"))
                        .onSuccess("expect").build(),
                node("expect", NodeType.EXPECT_FIX).cfg("msgType", "8").onSuccess("end").build(),
                Fixtures.endPass("end"));
        registry.register(s);

        UUID session = UUID.randomUUID();
        UUID execId = mgr.start(s.id(), session);
        await().atMost(Duration.ofSeconds(3)).until(() -> correlation.pendingCount() > 0);
        correlation.onMessage(session.toString(), Fixtures.fields(35, "8", 11, "ORD1"));
        await().atMost(Duration.ofSeconds(3))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.PASSED);

        // outbound (SEND) + inbound (EXPECT) both persisted
        assertThat(repo.messages).extracting(FIXMessage::direction)
                .contains(Direction.OUTBOUND, Direction.INBOUND);
        assertThat(publisher.publishedMessages).isNotEmpty();
        // lifecycle + node events
        assertThat(repo.events).extracting(ExecutionEvent::type)
                .contains(ExecutionEventType.EXECUTION_STARTED, ExecutionEventType.NODE_ENTERED,
                        ExecutionEventType.NODE_EXITED, ExecutionEventType.EXECUTION_FINISHED);
        assertThat(publisher.published).isNotEmpty();
        // node results all PASSED
        assertThat(repo.nodeResults).isNotEmpty();
        assertThat(repo.nodeResults).allMatch(r -> r.status().equals("PASSED"));
        // final persisted status
        assertThat(repo.saved).isNotEmpty();
        assertThat(repo.saved.get(repo.saved.size() - 1).status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void persistedRawFixIncludesRepeatingGroupEntries() {
        FakeRepo repo = new FakeRepo();
        FakePublisher publisher = new FakePublisher();
        FakeFixAdapter adapter = new FakeFixAdapter();

        NodeDispatcher d = new NodeDispatcher(List.of(
                new StartHandler(), new EndHandler(),
                new SendFIXHandler(adapter, new VariableResolver())));
        NodeWalker walker = new NodeWalker(d);
        ExecutionManager mgr = new ExecutionManager(registry, walker, repo, publisher);

        Map<String, Object> nearLeg = Map.of("fields", List.of(
                Map.of("tag", 600, "value", "EUR/USD"),
                Map.of("tag", 609, "value", "FXSPOT")));
        Map<String, Object> farLeg = Map.of("fields", List.of(
                Map.of("tag", 600, "value", "GBP/USD"),
                Map.of("tag", 609, "value", "FXFWD")));
        Map<String, Object> noLegs = Map.of("counterTag", 555, "entries", List.of(nearLeg, farLeg));

        Scenario s = scenario(UUID.randomUUID(), "persist-groups", List.of(),
                start("send"),
                node("send", NodeType.SEND_FIX).cfg("msgType", "D")
                        .cfg("fields", Map.of("11", "ORD-1"))
                        .cfg("groups", List.of(noLegs))
                        .onSuccess("end").build(),
                Fixtures.endPass("end"));
        registry.register(s);

        UUID execId = mgr.start(s.id(), UUID.randomUUID());
        await().atMost(Duration.ofSeconds(3))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.PASSED);

        assertThat(repo.messages).isNotEmpty();
        FIXMessage sent = repo.messages.get(0);
        assertThat(sent.rawFix()).contains("555=2");
        assertThat(sent.rawFix()).contains("600=EUR/USD|609=FXSPOT");
        assertThat(sent.rawFix()).contains("600=GBP/USD|609=FXFWD");
        // the flat fields map stays a top-level-only projection for backward compatibility
        assertThat(sent.fields()).doesNotContainKeys(555, 600, 609);
    }

    @Test
    void persistsErrorEventAndFailedNodeResultOnFailureBranch() {
        FakeRepo repo = new FakeRepo();
        FakePublisher publisher = new FakePublisher();
        NodeDispatcher d = new NodeDispatcher(List.of(
                new StartHandler(), new EndHandler(), new EndFailHandler(),
                new DecisionHandler(new VariableResolver())));
        ExecutionManager mgr = new ExecutionManager(registry, new NodeWalker(d), repo, publisher);

        Scenario s = scenario(UUID.randomUUID(), "fail", List.of(),
                start("decide"),
                node("decide", NodeType.DECISION).cfg("condition", "a == b")
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"),
                Fixtures.endFail("fail"));
        registry.register(s);

        UUID execId = mgr.start(s.id(), UUID.randomUUID());
        await().atMost(Duration.ofSeconds(3))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.FAILED);

        assertThat(repo.events).extracting(ExecutionEvent::type).contains(ExecutionEventType.ERROR);
        assertThat(repo.nodeResults).anyMatch(r -> r.status().equals("FAILED"));
        assertThat(repo.saved.get(repo.saved.size() - 1).status()).isEqualTo(ExecutionStatus.FAILED);
    }
}
