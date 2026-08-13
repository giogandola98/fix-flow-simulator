package com.fixflow.core.domain.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionDomainTest {

    // ---------- Execution ----------

    @Test
    void executionKeepsAllAccessors() {
        UUID id = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-01T00:01:00Z");
        NodeResult nr = new NodeResult(UUID.randomUUID(), id, "n1", "PASS", start, end, null);
        ExecutionEvent ev = ExecutionEvent.of(id, ExecutionEventType.NODE_ENTERED, "n1", "detail");

        Execution exec = new Execution(id, scenarioId, "1.0", sessionId,
                ExecutionStatus.RUNNING, start, end, "n1",
                Map.of("k", "v"), List.of(nr), List.of(ev));

        assertThat(exec.id()).isEqualTo(id);
        assertThat(exec.scenarioId()).isEqualTo(scenarioId);
        assertThat(exec.scenarioVersion()).isEqualTo("1.0");
        assertThat(exec.sessionId()).isEqualTo(sessionId);
        assertThat(exec.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(exec.startTime()).isEqualTo(start);
        assertThat(exec.endTime()).isEqualTo(end);
        assertThat(exec.currentNodeId()).isEqualTo("n1");
        assertThat(exec.variables()).containsEntry("k", "v");
        assertThat(exec.nodeResults()).containsExactly(nr);
        assertThat(exec.events()).containsExactly(ev);
    }

    @Test
    void executionDefaultsNullCollectionsToEmpty() {
        Execution exec = new Execution(UUID.randomUUID(), UUID.randomUUID(), "1.0",
                UUID.randomUUID(), ExecutionStatus.PASSED, Instant.now(), null, null,
                null, null, null);

        assertThat(exec.variables()).isEmpty();
        assertThat(exec.nodeResults()).isEmpty();
        assertThat(exec.events()).isEmpty();
    }

    @Test
    void executionDefensivelyCopiesCollections() {
        Map<String, String> vars = new HashMap<>(Map.of("a", "1"));
        List<NodeResult> results = new ArrayList<>();
        List<ExecutionEvent> events = new ArrayList<>();

        Execution exec = new Execution(UUID.randomUUID(), UUID.randomUUID(), "1.0",
                UUID.randomUUID(), ExecutionStatus.RUNNING, Instant.now(), null, null,
                vars, results, events);

        // mutating the originals must not leak into the record
        vars.put("b", "2");
        results.add(new NodeResult(UUID.randomUUID(), exec.id(), "x", "PASS", Instant.now(), null, null));

        assertThat(exec.variables()).containsOnlyKeys("a");
        assertThat(exec.nodeResults()).isEmpty();

        // returned collections are immutable
        assertThatThrownBy(() -> exec.variables().put("z", "z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exec.events().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- ExecutionEvent ----------

    @Test
    void executionEventOfFactoryPopulatesDefaults() {
        UUID execId = UUID.randomUUID();
        Instant before = Instant.now();
        ExecutionEvent ev = ExecutionEvent.of(execId, ExecutionEventType.ERROR, "node-7", "boom");
        Instant after = Instant.now();

        assertThat(ev.id()).isNotNull();
        assertThat(ev.executionId()).isEqualTo(execId);
        assertThat(ev.type()).isEqualTo(ExecutionEventType.ERROR);
        assertThat(ev.nodeId()).isEqualTo("node-7");
        assertThat(ev.detail()).isEqualTo("boom");
        assertThat(ev.rawFix()).isNull();
        assertThat(ev.timestamp()).isBetween(before, after);
    }

    @Test
    void executionEventOfGeneratesUniqueIds() {
        UUID execId = UUID.randomUUID();
        ExecutionEvent a = ExecutionEvent.of(execId, ExecutionEventType.TIMEOUT, "n", "d");
        ExecutionEvent b = ExecutionEvent.of(execId, ExecutionEventType.TIMEOUT, "n", "d");
        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void executionEventCanonicalConstructorKeepsRawFix() {
        UUID id = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        Instant ts = Instant.now();
        ExecutionEvent ev = new ExecutionEvent(id, execId, ExecutionEventType.MESSAGE_RECEIVED,
                "n1", ts, "detail", "8=FIX.4.49=535=A");

        assertThat(ev.id()).isEqualTo(id);
        assertThat(ev.rawFix()).contains("35=A");
        assertThat(ev.timestamp()).isEqualTo(ts);
    }

    // ---------- FIXMessage ----------

    @Test
    void fixMessageKeepsAccessors() {
        UUID id = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        Instant at = Instant.now();
        FIXMessage msg = new FIXMessage(id, execId, Direction.OUTBOUND,
                "8=FIX.4.4", Map.of(35, "D"), at);

        assertThat(msg.id()).isEqualTo(id);
        assertThat(msg.executionId()).isEqualTo(execId);
        assertThat(msg.direction()).isEqualTo(Direction.OUTBOUND);
        assertThat(msg.rawFix()).isEqualTo("8=FIX.4.4");
        assertThat(msg.fields()).containsEntry(35, "D");
        assertThat(msg.receivedAt()).isEqualTo(at);
    }

    @Test
    void fixMessageDefaultsNullFieldsToEmpty() {
        FIXMessage msg = new FIXMessage(UUID.randomUUID(), UUID.randomUUID(),
                Direction.INBOUND, "raw", null, Instant.now());
        assertThat(msg.fields()).isEmpty();
    }

    @Test
    void fixMessageDefensivelyCopiesFields() {
        Map<Integer, String> fields = new HashMap<>(Map.of(35, "D"));
        FIXMessage msg = new FIXMessage(UUID.randomUUID(), UUID.randomUUID(),
                Direction.INBOUND, "raw", fields, Instant.now());
        fields.put(11, "order-1");

        assertThat(msg.fields()).containsOnlyKeys(35);
        assertThatThrownBy(() -> msg.fields().put(1, "x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- NodeResult ----------

    @Test
    void nodeResultKeepsAccessors() {
        UUID id = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(1);
        NodeResult nr = new NodeResult(id, execId, "n1", "FAIL", start, end, "err");

        assertThat(nr.id()).isEqualTo(id);
        assertThat(nr.executionId()).isEqualTo(execId);
        assertThat(nr.nodeId()).isEqualTo("n1");
        assertThat(nr.status()).isEqualTo("FAIL");
        assertThat(nr.startTime()).isEqualTo(start);
        assertThat(nr.endTime()).isEqualTo(end);
        assertThat(nr.error()).isEqualTo("err");
    }

    @Test
    void recordsHonourEqualityAndToString() {
        UUID id = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        Instant t = Instant.now();
        NodeResult a = new NodeResult(id, execId, "n1", "PASS", t, t, null);
        NodeResult b = new NodeResult(id, execId, "n1", "PASS", t, t, null);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toString()).contains("n1");
    }

    // ---------- enums ----------

    @Test
    void directionEnumValues() {
        assertThat(Direction.values()).containsExactly(Direction.INBOUND, Direction.OUTBOUND);
        assertThat(Direction.valueOf("INBOUND")).isEqualTo(Direction.INBOUND);
    }

    @Test
    void executionStatusEnumValues() {
        assertThat(ExecutionStatus.values()).containsExactly(
                ExecutionStatus.RUNNING, ExecutionStatus.PASSED,
                ExecutionStatus.FAILED, ExecutionStatus.STOPPED);
        assertThat(ExecutionStatus.valueOf("FAILED")).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void executionEventTypeEnumValues() {
        assertThat(ExecutionEventType.values()).containsExactly(
                ExecutionEventType.EXECUTION_STARTED, ExecutionEventType.EXECUTION_FINISHED,
                ExecutionEventType.NODE_ENTERED, ExecutionEventType.NODE_EXITED,
                ExecutionEventType.MESSAGE_SENT, ExecutionEventType.MESSAGE_RECEIVED,
                ExecutionEventType.TIMEOUT, ExecutionEventType.ERROR,
                ExecutionEventType.SESSION_UP, ExecutionEventType.SESSION_DOWN);
        assertThat(ExecutionEventType.valueOf("SESSION_UP")).isEqualTo(ExecutionEventType.SESSION_UP);
    }
}
