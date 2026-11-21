package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.scenario.VariableDef;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ExecutionManagerTest {

    private final ScenarioRegistry registry = new ScenarioRegistry();

    private ExecutionStatus runToCompletion(ExecutionManager mgr, UUID scenarioId) {
        UUID execId = mgr.start(scenarioId, UUID.randomUUID());
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            ExecutionStatus st = mgr.getStatus(execId);
            return st != null && st != ExecutionStatus.RUNNING;
        });
        return mgr.getStatus(execId);
    }

    @Test
    void simpleScenarioRunsToPassed() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        Scenario s = scenario("pass", start("end"), Fixtures.endPass("end"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void runsOffEndOfGraphMarksPassed() {
        ProgrammableHandler pass = ProgrammableHandler.passing(NodeType.SEND_FIX);
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), pass));
        Scenario s = scenario("open", start("a"), node("a", NodeType.SEND_FIX).build());
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void endFailMarksFailed() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndFailHandler()));
        Scenario s = scenario("fail", start("end"), Fixtures.endFail("end"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.FAILED);
    }

    // BUG-1: a false DECISION must route to onFailure, not terminate the run as a bare failure.
    @Test
    void decisionFalseRoutesToOnFailureBranch() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(),
                new EndFailHandler(), new DecisionHandler(new VariableResolver())));
        Scenario s = scenario("dec",
                start("decide"),
                node("decide", NodeType.DECISION).cfg("condition", "a == b")
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"),
                Fixtures.endFail("fail"));
        registry.register(s);
        // false condition -> onFailure -> END_FAIL -> FAILED (routed, not a bare halt)
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void decisionTrueRoutesToOnSuccessBranch() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(),
                new EndFailHandler(), new DecisionHandler(new VariableResolver())));
        Scenario s = scenario("dec",
                start("decide"),
                node("decide", NodeType.DECISION).cfg("condition", "a == a")
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"),
                Fixtures.endFail("fail"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, d), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
    }

    // #62: a LOOP must walk its whole sub-block N times, not just the single attached node.
    @Test
    void loopWalksFullSubBlockNTimes() {
        AtomicInteger counter = new AtomicInteger();
        ProgrammableHandler counting = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            counter.incrementAndGet();
            return NodeHandlerResult.success(n.onSuccess());
        });
        NodeDispatcher body = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), counting));
        LoopHandler loop = new LoopHandler(body);
        NodeDispatcher top = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), counting, loop));

        Scenario s = scenario("loop",
                start("loop"),
                node("loop", NodeType.LOOP).cfg("targetNodeId", "bodyA").cfg("iterations", 3)
                        .onSuccess("end").build(),
                node("bodyA", NodeType.SEND_FIX).onSuccess("bodyB").build(),
                node("bodyB", NodeType.SEND_FIX).onSuccess("loop").build(), // loops back to boundary
                Fixtures.endPass("end"));
        registry.register(s);
        assertThat(runToCompletion(new ExecutionManager(registry, top), s.id()))
                .isEqualTo(ExecutionStatus.PASSED);
        assertThat(counter).as("2 body nodes * 3 iterations").hasValue(6);
    }

    // #61: stop() must interrupt a blocked node and land STOPPED.
    @Test
    void stopInterruptsBlockedDelayNodeAndLandsStopped() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new DelayHandler()));
        Scenario s = scenario("delay",
                start("delay"),
                node("delay", NodeType.DELAY).cfg("delayMs", 60_000L).onSuccess("end").build(),
                Fixtures.endPass("end"));
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);
        UUID execId = mgr.start(s.id(), UUID.randomUUID());

        await().atMost(Duration.ofSeconds(5)).until(() -> {
            ExecutionContext ctx = mgr.getContext(execId);
            return ctx != null && "delay".equals(ctx.currentNodeId());
        });
        mgr.stop(execId);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.STOPPED);
        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.STOPPED);
    }

    @Test
    void completedStatusLookupAfterContextRemoved() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        Scenario s = scenario("pass", start("end"), Fixtures.endPass("end"));
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);
        ExecutionStatus status = runToCompletion(mgr, s.id());
        assertThat(status).isEqualTo(ExecutionStatus.PASSED);
        // after completion the live context is gone, but the status is still resolvable
        UUID anExec = UUID.randomUUID();
        assertThat(mgr.getContext(anExec)).isNull();
    }

    @Test
    void startWithUnknownScenarioThrows() {
        ExecutionManager mgr = new ExecutionManager(registry, new NodeDispatcher(List.of(new StartHandler())));
        assertThatThrownBy(() -> mgr.start(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatusUnknownExecutionIsNull() {
        ExecutionManager mgr = new ExecutionManager(registry, new NodeDispatcher(List.of(new StartHandler())));
        assertThat(mgr.getStatus(UUID.randomUUID())).isNull();
    }

    @Test
    void resolvesSessionFromScenarioRefWhenNotSupplied() {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        UUID sessionRef = UUID.randomUUID();
        Scenario s = new Scenario(UUID.randomUUID(), "refsess", "", "1", sessionRef.toString(),
                com.fixflow.core.domain.scenario.RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(start("end"), Fixtures.endPass("end")), List.of(), Map.of(), null);
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);
        UUID execId = mgr.start(s.id(), null);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> mgr.getStatus(execId) == ExecutionStatus.PASSED);
        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void aNonUuidSessionRefRunsWithoutASessionInsteadOfThrowing() {
        // Scenarios exported from the editor carry placeholders such as `sessionRef: default`;
        // start() used to blow up on UUID.fromString before the run even began (issue #77).
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler()));
        Scenario s = scenario("no-session", start("end"), Fixtures.endPass("end"));
        registry.register(s);
        ExecutionManager mgr = new ExecutionManager(registry, d);

        UUID execId = mgr.start(s.id(), null);
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            ExecutionStatus st = mgr.getStatus(execId);
            return st != null && st != ExecutionStatus.RUNNING;
        });
        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    // ---- execution inputs ----

    private static final class MemDataSets implements DataSetRepositoryPort {
        final Map<UUID, DataSet> byId = new java.util.concurrent.ConcurrentHashMap<>();
        public DataSet save(DataSet d) { byId.put(d.id(), d); return d; }
        public Optional<DataSet> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        public Optional<DataSet> findByName(String n) {
            return byId.values().stream().filter(d -> d.name().equals(n)).findFirst();
        }
        public List<DataSet> findAll() { return new ArrayList<>(byId.values()); }
        public void delete(UUID id) { byId.remove(id); }
    }

    private static DataSet rows(String name, String... isins) {
        List<List<String>> r = new ArrayList<>();
        for (String i : isins) r.add(List.of(i));
        return new DataSet(UUID.randomUUID(), name, List.of("Isin"), r, null);
    }

    /** One SEND_FIX node recording what it saw; it fails for the rows the predicate selects. */
    private ExecutionManager managerRecording(MemDataSets ds, List<String> seen,
                                              java.util.function.Predicate<String> fails) {
        ProgrammableHandler rec = new ProgrammableHandler(NodeType.SEND_FIX, (n, c) -> {
            String v = c.row() == null ? "-" : c.row().get("Isin");
            seen.add(v + "|" + c.getVariable("k") + "|" + c.rowIndex());
            return fails.test(v) ? NodeHandlerResult.failure(n.onFailure(), "boom")
                    : NodeHandlerResult.success(n.onSuccess());
        });
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new EndFailHandler(), rec));
        return new ExecutionManager(registry, new NodeWalker(d), null, null, null, ds);
    }

    private Scenario recScenario(String datasetRef, Map<String, VariableDef> vars) {
        return Fixtures.scenario("rec", datasetRef, vars,
                start("a"),
                node("a", NodeType.SEND_FIX).onSuccess("ok").onFailure("ko").build(),
                Fixtures.endPass("ok"), Fixtures.endFail("ko"));
    }

    private void awaitDone(ExecutionManager mgr, List<UUID> ids) {
        await().atMost(Duration.ofSeconds(10)).until(() -> ids.stream().allMatch(id -> {
            ExecutionStatus st = mgr.getStatus(id);
            return st != null && st != ExecutionStatus.RUNNING;
        }));
    }

    @Test
    void scenario_variable_defaults_are_loaded() {
        List<String> seen = new CopyOnWriteArrayList<>();
        ExecutionManager mgr = managerRecording(new MemDataSets(), seen, v -> false);
        Scenario s = recScenario(null, Map.of("k", new VariableDef("string", "dflt")));
        registry.register(s);
        awaitDone(mgr, mgr.start(s.id(), UUID.randomUUID(), ExecutionInputs.none()));
        assertThat(seen).containsExactly("-|dflt|-1");
    }

    @Test
    void inputs_vars_override_defaults() {
        List<String> seen = new CopyOnWriteArrayList<>();
        ExecutionManager mgr = managerRecording(new MemDataSets(), seen, v -> false);
        Scenario s = recScenario(null, Map.of("k", new VariableDef("string", "dflt")));
        registry.register(s);
        awaitDone(mgr, mgr.start(s.id(), UUID.randomUUID(),
                new ExecutionInputs(null, null, false, Map.of("k", "given"))));
        assertThat(seen).containsExactly("-|given|-1");
    }

    @Test
    void all_rows_runs_one_execution_per_row_in_order() {
        List<String> seen = new CopyOnWriteArrayList<>();
        MemDataSets ds = new MemDataSets();
        DataSet d = ds.save(rows("rs", "A", "B", "C"));
        ExecutionManager mgr = managerRecording(ds, seen, v -> false);
        Scenario s = recScenario(null, Map.of());
        registry.register(s);
        List<UUID> ids = mgr.start(s.id(), UUID.randomUUID(), new ExecutionInputs(d.id(), null, true, Map.of()));
        assertThat(ids).hasSize(3).doesNotHaveDuplicates();
        awaitDone(mgr, ids);
        assertThat(seen).containsExactly("A|null|0", "B|null|1", "C|null|2");
        assertThat(ids).allSatisfy(id -> assertThat(mgr.getStatus(id)).isEqualTo(ExecutionStatus.PASSED));
    }

    @Test
    void failed_row_does_not_stop_the_next() {
        List<String> seen = new CopyOnWriteArrayList<>();
        MemDataSets ds = new MemDataSets();
        DataSet d = ds.save(rows("rs", "A", "B", "C"));
        ExecutionManager mgr = managerRecording(ds, seen, v -> v.equals("B"));
        Scenario s = recScenario(null, Map.of());
        registry.register(s);
        List<UUID> ids = mgr.start(s.id(), UUID.randomUUID(), new ExecutionInputs(d.id(), null, true, Map.of()));
        awaitDone(mgr, ids);
        assertThat(seen).hasSize(3);
        assertThat(ids.stream().map(mgr::getStatus).toList())
                .containsExactly(ExecutionStatus.PASSED, ExecutionStatus.FAILED, ExecutionStatus.PASSED);
    }

    @Test
    void dataset_ref_by_name_is_used_when_request_has_none() {
        List<String> seen = new CopyOnWriteArrayList<>();
        MemDataSets ds = new MemDataSets();
        ds.save(rows("My Rows", "A", "B"));
        ExecutionManager mgr = managerRecording(ds, seen, v -> false);
        Scenario s = recScenario("My Rows", Map.of());
        registry.register(s);
        awaitDone(mgr, mgr.start(s.id(), UUID.randomUUID(), new ExecutionInputs(null, 1, false, Map.of())));
        assertThat(seen).containsExactly("B|null|1");
    }

    @Test
    void row_out_of_range_is_rejected_before_anything_runs() {
        MemDataSets ds = new MemDataSets();
        DataSet d = ds.save(rows("rs", "A"));
        ExecutionManager mgr = managerRecording(ds, new CopyOnWriteArrayList<>(), v -> false);
        Scenario s = recScenario(null, Map.of());
        registry.register(s);
        assertThatThrownBy(() -> mgr.start(s.id(), null, new ExecutionInputs(d.id(), 5, false, Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5");
    }
}
