package com.fixflow.engine.handlers;

import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.ScenarioExecutor;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.support.ProgrammableHandler;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static com.fixflow.engine.support.Fixtures.timeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ParallelHandlerTest {

    private ScenarioRegistry registry;
    private ParallelHandler handler;
    private Scenario child;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() { registry = new ScenarioRegistry(); }

    @AfterEach
    void tearDown() { pool.shutdownNow(); }

    /** Child scenario: START -> step (SEND_FIX with the given behaviour) -> END_PASS / END_FAIL. */
    private void childStep(BiFunction<ScenarioNode, ExecutionContext, NodeHandlerResult> behaviour) {
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new EndFailHandler(),
                new ProgrammableHandler(NodeType.SEND_FIX, behaviour)));
        handler = new ParallelHandler(registry, new ScenarioExecutor(d), new VariableResolver());
        child = scenario("child", start("step"),
                node("step", NodeType.SEND_FIX).onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"), Fixtures.endFail("fail"));
        registry.register(child);
    }

    private static DataSet inquiries(String... outcomes) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < outcomes.length; i++) rows.add(List.of("INQ" + (i + 1), outcomes[i]));
        return new DataSet(UUID.randomUUID(), "inquiries", List.of("inquiry", "outcome"), rows, null);
    }

    private ExecutionContext parent(DataSet ds) {
        ExecutionContext ctx = Fixtures.ctx(scenario("parent", start("par")));
        ctx.setDataSet(ds);
        return ctx;
    }

    private ScenarioNode parNode(long timeoutMs, Map<String, Object> extra) {
        Fixtures.NodeBuilder b = node("par", NodeType.PARALLEL)
                .cfg("targetScenarioId", child.id().toString())
                .onSuccess("ok").onFailure("no");
        extra.forEach(b::cfg);
        if (timeoutMs > 0) b.timeout(timeout(timeoutMs, TimeoutAction.FAIL, null));
        return b.build();
    }

    private static NodeHandlerResult awaitLatch(ScenarioNode n, CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS)
                    ? NodeHandlerResult.success(n.onSuccess())
                    : NodeHandlerResult.failure(n.onFailure(), "latch not released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeHandlerResult.failure(n.onFailure(), "interrupted");
        }
    }

    @Test
    void supportsParallel() {
        childStep((n, c) -> NodeHandlerResult.success(n.onSuccess()));
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.PARALLEL);
    }

    @Test
    void runs_children_concurrently() throws Exception {
        CountDownLatch allStarted = new CountDownLatch(3);
        childStep((n, c) -> {
            allStarted.countDown();
            return awaitLatch(n, allStarted);
        });
        ExecutionContext parent = parent(inquiries("ok", "ok", "ok"));
        long t0 = System.nanoTime();
        NodeHandlerResult r = handler.handle(parNode(10_000, Map.of()), parent);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        assertThat(r.success()).as(r.errorMessage()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(ms).isLessThan(5_000);
        assertThat(parent.getVariable("parallel:par:passed")).isEqualTo("3");
        assertThat(parent.getVariable("parallel:par:failed")).isEqualTo("0");
        assertThat(parent.getVariable("parallel:par:0:status")).isEqualTo("PASSED");
    }

    @Test
    void one_failed_child_fails_the_node_and_counts() throws Exception {
        childStep((n, c) -> "fail".equals(c.row().get("outcome"))
                ? NodeHandlerResult.failure(n.onFailure(), "rejected")
                : NodeHandlerResult.success(n.onSuccess()));
        ExecutionContext parent = parent(inquiries("ok", "fail", "ok"));
        NodeHandlerResult r = handler.handle(parNode(5_000, Map.of()), parent);

        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).isEqualTo("2/3 children passed");
        assertThat(parent.getVariable("parallel:par:passed")).isEqualTo("2");
        assertThat(parent.getVariable("parallel:par:failed")).isEqualTo("1");
        assertThat(parent.getVariable("parallel:par:0:status")).isEqualTo("PASSED");
        assertThat(parent.getVariable("parallel:par:1:status")).isEqualTo("FAILED");
        assertThat(parent.getVariable("parallel:par:2:status")).isEqualTo("PASSED");
    }

    @Test
    void each_child_sees_its_row() throws Exception {
        Map<Integer, String> seen = new ConcurrentHashMap<>();
        childStep((n, c) -> {
            seen.put(c.rowIndex(), c.row().get("inquiry"));
            return NodeHandlerResult.success(n.onSuccess());
        });
        NodeHandlerResult r = handler.handle(parNode(5_000, Map.of()), parent(inquiries("ok", "ok", "ok")));
        assertThat(r.success()).isTrue();
        assertThat(seen).containsExactlyInAnyOrderEntriesOf(Map.of(0, "INQ1", 1, "INQ2", 2, "INQ3"));
    }

    @Test
    void selected_rows_only_and_input_vars_reach_each_child() throws Exception {
        Map<Integer, String> seen = new ConcurrentHashMap<>();
        childStep((n, c) -> {
            seen.put(c.rowIndex(), c.getVariable("dealer"));
            return NodeHandlerResult.success(n.onSuccess());
        });
        ExecutionContext parent = parent(inquiries("ok", "ok", "ok"));
        parent.setVariable("who", "D1");
        NodeHandlerResult r = handler.handle(parNode(5_000, Map.of(
                "rows", List.of(0, 2),
                "inputVars", List.of(Map.of("from", "var:who", "to", "dealer")))), parent);
        assertThat(r.success()).isTrue();
        assertThat(seen).containsExactlyInAnyOrderEntriesOf(Map.of(0, "D1", 2, "D1"));
        assertThat(parent.getVariable("parallel:par:1:status")).isNull();
        assertThat(parent.getVariable("parallel:par:passed")).isEqualTo("2");
    }

    @Test
    void max_concurrency_caps_running_children() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        childStep((n, c) -> {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            running.decrementAndGet();
            return NodeHandlerResult.success(n.onSuccess());
        });
        NodeHandlerResult r = handler.handle(parNode(10_000, Map.of("maxConcurrency", 2)),
                parent(inquiries("ok", "ok", "ok", "ok", "ok")));
        assertThat(r.success()).isTrue();
        assertThat(peak.get()).isLessThanOrEqualTo(2);
        assertThat(peak.get()).isEqualTo(2);
    }

    @Test
    void node_timeout_interrupts_still_running_children() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();
        childStep((n, c) -> {
            if ("fast".equals(c.row().get("outcome"))) return NodeHandlerResult.success(n.onSuccess());
            try {
                never.await();
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
                Thread.currentThread().interrupt();
            }
            return NodeHandlerResult.failure(n.onFailure(), "interrupted");
        });
        ExecutionContext parent = parent(inquiries("fast", "slow", "slow"));
        long t0 = System.nanoTime();
        NodeHandlerResult r = handler.handle(parNode(300, Map.of()), parent);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).startsWith("timeout");
        assertThat(ms).isLessThan(4_000);
        assertThat(interrupted.get()).isEqualTo(2);
        assertThat(parent.getVariable("parallel:par:0:status")).isEqualTo("PASSED");
        assertThat(parent.getVariable("parallel:par:1:status")).isEqualTo("TIMEOUT");
        assertThat(parent.getVariable("parallel:par:passed")).isEqualTo("1");
        assertThat(parent.getVariable("parallel:par:failed")).isEqualTo("2");
    }

    @Test
    void parent_interrupt_stops_children_and_propagates() throws Exception {
        AtomicInteger interrupted = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(2);
        childStep((n, c) -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
                Thread.currentThread().interrupt();
            }
            return NodeHandlerResult.failure(n.onFailure(), "interrupted");
        });
        ExecutionContext parent = parent(inquiries("ok", "ok"));
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(parNode(0, Map.of()), parent));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        f.cancel(true);
        await().atMost(Duration.ofSeconds(5)).until(() -> interrupted.get() == 2);
    }

    @Test
    void children_waiting_on_one_session_each_get_their_own_message() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, new MessageBuffer());
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new EndFailHandler(),
                new ExpectFIXHandler(correlation, router, new VariableResolver())));
        handler = new ParallelHandler(registry, new ScenarioExecutor(d), new VariableResolver());
        child = scenario("child", start("exp"),
                node("exp", NodeType.EXPECT_FIX)
                        .cfg("msgType", "S")
                        .cfg("correlation", Map.of("sourceTag", 20117, "expectedValue", "{{row:inquiry}}"))
                        .timeout(timeout(5_000, TimeoutAction.FAIL, null))
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"), Fixtures.endFail("fail"));
        registry.register(child);
        ExecutionContext parent = parent(inquiries("ok", "ok", "ok"));
        String session = parent.sessionId().toString();

        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(parNode(10_000, Map.of()), parent));
        await().atMost(Duration.ofSeconds(5)).until(() -> correlation.pendingCount() == 3);
        for (String inq : List.of("INQ3", "INQ1", "INQ2")) {
            assertThat(correlation.onMessage(session, Fixtures.fields(35, "S", 20117, inq))).isTrue();
        }
        NodeHandlerResult r = f.get(5, TimeUnit.SECONDS);
        assertThat(r.success()).as(r.errorMessage()).isTrue();
        assertThat(parent.getVariable("parallel:par:passed")).isEqualTo("3");
        assertThat(correlation.pendingCount()).isZero();
    }

    @Test
    void node_timeout_cancels_children_correlation_waiters() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, new MessageBuffer());
        NodeDispatcher d = new NodeDispatcher(List.of(new StartHandler(), new EndHandler(), new EndFailHandler(),
                new ExpectFIXHandler(correlation, router, new VariableResolver())));
        handler = new ParallelHandler(registry, new ScenarioExecutor(d), new VariableResolver());
        child = scenario("child", start("exp"),
                node("exp", NodeType.EXPECT_FIX).cfg("msgType", "S")
                        .timeout(timeout(60_000, TimeoutAction.FAIL, null))
                        .onSuccess("pass").onFailure("fail").build(),
                Fixtures.endPass("pass"), Fixtures.endFail("fail"));
        registry.register(child);
        NodeHandlerResult r = handler.handle(parNode(300, Map.of()), parent(inquiries("ok", "ok")));
        assertThat(r.success()).isFalse();
        assertThat(correlation.pendingCount()).isZero();
    }

    @Test
    void without_a_data_set_the_node_fails() throws Exception {
        childStep((n, c) -> NodeHandlerResult.success(n.onSuccess()));
        NodeHandlerResult r = handler.handle(parNode(1_000, Map.of()), parent(null));
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("data set");
    }

    @Test
    void row_out_of_range_fails_before_any_child_runs() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        childStep((n, c) -> { runs.incrementAndGet(); return NodeHandlerResult.success(n.onSuccess()); });
        NodeHandlerResult r = handler.handle(parNode(1_000, Map.of("rows", List.of(0, 7))),
                parent(inquiries("ok", "ok")));
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("row 7");
        assertThat(runs.get()).isZero();
    }

    @Test
    void unknown_target_and_max_depth_fail() throws Exception {
        childStep((n, c) -> NodeHandlerResult.success(n.onSuccess()));
        ScenarioNode unknown = node("par", NodeType.PARALLEL).cfg("targetScenarioId", UUID.randomUUID().toString())
                .onSuccess("ok").onFailure("no").build();
        assertThat(handler.handle(unknown, parent(inquiries("ok"))).errorMessage()).contains("Scenario not found");

        ExecutionContext deep = parent(inquiries("ok"));
        deep.setVariable("call:depth", "5");
        NodeHandlerResult r = handler.handle(parNode(1_000, Map.of()), deep);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("Max call depth exceeded");
    }
}
