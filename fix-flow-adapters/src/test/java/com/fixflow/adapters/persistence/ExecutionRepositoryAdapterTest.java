package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.ExecutionEntity;
import com.fixflow.adapters.persistence.entity.FIXMessageEntity;
import com.fixflow.adapters.persistence.jpa.JpaExecutionRepository;
import com.fixflow.adapters.persistence.jpa.JpaFIXMessageRepository;
import com.fixflow.core.domain.execution.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExecutionRepositoryAdapterTest {

    @Autowired ExecutionRepositoryAdapter adapter;
    @Autowired JpaExecutionRepository executionRepo;
    @Autowired JpaFIXMessageRepository messageRepo;

    private Execution newExecution(UUID id, Map<String, String> vars) {
        return new Execution(id, UUID.randomUUID(), "1.0", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, "start",
                vars, null, null);
    }

    @Test
    void savePersistsExecutionAndFindByIdReloadsIt() {
        UUID id = UUID.randomUUID();
        Execution e = newExecution(id, Map.of("k", "v", "n", "42"));

        adapter.save(e);
        Execution loaded = adapter.findById(id).orElseThrow();

        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(loaded.currentNodeId()).isEqualTo("start");
        assertThat(loaded.variables()).containsEntry("k", "v").containsEntry("n", "42");
    }

    @Test
    void saveIsUpsertUpdatingExistingRow() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        Execution updated = new Execution(id, UUID.randomUUID(), "1.0", UUID.randomUUID(),
                ExecutionStatus.PASSED, Instant.now(), Instant.now(), "end", Map.of(), null, null);
        adapter.save(updated);

        assertThat(executionRepo.count()).isEqualTo(1);
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void addEventsAreLoadedOrderedByTimestamp() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        adapter.addEvent(id, new ExecutionEvent(UUID.randomUUID(), id,
                ExecutionEventType.NODE_ENTERED, "n1", t0.plusSeconds(2), "second", null));
        adapter.addEvent(id, new ExecutionEvent(UUID.randomUUID(), id,
                ExecutionEventType.EXECUTION_STARTED, null, t0, "first", null));

        Execution loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.events()).hasSize(2);
        assertThat(loaded.events().get(0).detail()).isEqualTo("first");
        assertThat(loaded.events().get(1).detail()).isEqualTo("second");
    }

    @Test
    void addEventGeneratesIdWhenNull() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        adapter.addEvent(id, new ExecutionEvent(null, id,
                ExecutionEventType.ERROR, "n1", Instant.now(), "boom", "8=FIX"));

        Execution loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.events()).hasSize(1);
        assertThat(loaded.events().get(0).id()).isNotNull();
        assertThat(loaded.events().get(0).rawFix()).isEqualTo("8=FIX");
    }

    @Test
    void addNodeResultsAreLoadedOrderedByStartTime() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        adapter.addNodeResult(id, new NodeResult(UUID.randomUUID(), id, "n2", "PASSED",
                t0.plusSeconds(5), t0.plusSeconds(6), null));
        adapter.addNodeResult(id, new NodeResult(UUID.randomUUID(), id, "n1", "FAILED",
                t0, t0.plusSeconds(1), "err"));

        Execution loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.nodeResults()).hasSize(2);
        assertThat(loaded.nodeResults().get(0).nodeId()).isEqualTo("n1");
        assertThat(loaded.nodeResults().get(0).error()).isEqualTo("err");
        assertThat(loaded.nodeResults().get(1).nodeId()).isEqualTo("n2");
    }

    @Test
    void addNodeResultGeneratesIdWhenNull() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        adapter.addNodeResult(id, new NodeResult(null, id, "n1", "PASSED",
                Instant.now(), Instant.now(), null));

        assertThat(adapter.findById(id).orElseThrow().nodeResults().get(0).id()).isNotNull();
    }

    @Test
    void addMessagesAreLoadedOrderedWithParsedIntKeyedFields() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        adapter.addMessage(id, new FIXMessage(UUID.randomUUID(), id, Direction.OUTBOUND,
                "8=FIX.4.4|35=D", Map.of(35, "D", 11, "CL-1"), t0.plusSeconds(1)));
        adapter.addMessage(id, new FIXMessage(UUID.randomUUID(), id, Direction.INBOUND,
                "8=FIX.4.4|35=8", Map.of(35, "8"), t0));

        var messages = adapter.findMessages(id);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).direction()).isEqualTo(Direction.INBOUND);
        assertThat(messages.get(1).direction()).isEqualTo(Direction.OUTBOUND);
        assertThat(messages.get(1).fields()).containsEntry(35, "D").containsEntry(11, "CL-1");
    }

    @Test
    void addMessageGeneratesIdWhenNull() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        adapter.addMessage(id, new FIXMessage(null, id, Direction.INBOUND,
                "raw", Map.of(35, "0"), Instant.now()));

        assertThat(adapter.findMessages(id).get(0).id()).isNotNull();
    }

    @Test
    void findMessagesEmptyWhenNone() {
        assertThat(adapter.findMessages(UUID.randomUUID())).isEmpty();
    }

    // ---------- graceful JSON handling ----------

    @Test
    void corruptVariablesJsonYieldsEmptyMapInsteadOfThrowing() {
        UUID id = UUID.randomUUID();
        ExecutionEntity e = new ExecutionEntity();
        e.setId(id);
        e.setScenarioId(UUID.randomUUID());
        e.setStatus(ExecutionStatus.RUNNING);
        e.setStartTime(Instant.now());
        e.setVariablesJson("{ this is not valid json");
        executionRepo.save(e);

        Execution loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.variables()).isEmpty();
    }

    @Test
    void nullVariablesJsonYieldsEmptyMap() {
        UUID id = UUID.randomUUID();
        ExecutionEntity e = new ExecutionEntity();
        e.setId(id);
        e.setScenarioId(UUID.randomUUID());
        e.setStatus(ExecutionStatus.RUNNING);
        e.setStartTime(Instant.now());
        e.setVariablesJson(null);
        executionRepo.save(e);

        assertThat(adapter.findById(id).orElseThrow().variables()).isEmpty();
    }

    @Test
    void corruptFieldsJsonYieldsEmptyFieldMapInsteadOfThrowing() {
        UUID id = UUID.randomUUID();
        adapter.save(newExecution(id, Map.of()));

        FIXMessageEntity e = new FIXMessageEntity();
        e.setId(UUID.randomUUID());
        e.setExecutionId(id);
        e.setDirection(Direction.INBOUND);
        e.setRawFix("raw");
        e.setFieldsJson("<<<not json>>>");
        e.setReceivedAt(Instant.now());
        messageRepo.save(e);

        var messages = adapter.findMessages(id);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).fields()).isEmpty();
    }

    @Test
    void saveWithNullVariablesWritesEmptyJsonMap() {
        UUID id = UUID.randomUUID();
        Execution e = new Execution(id, UUID.randomUUID(), "1.0", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, "start", null, null, null);

        adapter.save(e);
        assertThat(adapter.findById(id).orElseThrow().variables()).isEmpty();
    }
}
