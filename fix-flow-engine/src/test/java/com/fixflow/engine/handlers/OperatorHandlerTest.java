package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.operator.OperatorGateway;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static com.fixflow.engine.support.Fixtures.timeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OperatorHandlerTest {

    private OperatorGateway gateway;
    private OperatorHandler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<String> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        gateway = new OperatorGateway();
        handler = new OperatorHandler(gateway, new VariableResolver());
    }

    @AfterEach
    void tearDown() { pool.shutdownNow(); }

    private ExecutionContext ctx(Scenario s) {
        ExecutionContext c = Fixtures.ctx(s);
        c.setNodeDetailEmitter((type, nodeId, detail) -> events.add(type + "|" + nodeId + "|" + detail));
        return c;
    }

    private ScenarioNode op() {
        return node("op", NodeType.OPERATOR).cfg("instruction", "Send {{var:who}} a request")
                .onSuccess("done").onFailure("fail").build();
    }

    @Test
    void confirm_goes_on_success() throws Exception {
        ScenarioNode op = op();
        ExecutionContext ctx = ctx(scenario("s", start("op"), op));
        ctx.setVariable("who", "Bob");
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(op, ctx));
        await().atMost(Duration.ofSeconds(3)).until(() -> gateway.open(ctx.executionId()).isPresent());
        assertThat(gateway.open(ctx.executionId()).get().instruction()).isEqualTo("Send Bob a request");
        assertThat(gateway.answer(ctx.executionId(), "op", true, "done it")).isTrue();
        NodeHandlerResult r = f.get(3, TimeUnit.SECONDS);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("done");
        assertThat(ctx.getVariable("operator:op:note")).isEqualTo("done it");
        assertThat(events).containsExactly(
                ExecutionEventType.OPERATOR_PROMPT + "|op|Send Bob a request",
                ExecutionEventType.OPERATOR_ANSWERED + "|op|ok: done it");
        assertThat(gateway.open(ctx.executionId())).isEmpty();
    }

    @Test
    void fail_goes_on_failure_with_note() throws Exception {
        ScenarioNode op = op();
        ExecutionContext ctx = ctx(scenario("s", start("op"), op));
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(op, ctx));
        await().atMost(Duration.ofSeconds(3)).until(() -> gateway.open(ctx.executionId()).isPresent());
        gateway.answer(ctx.executionId(), "op", false, "bank refused");
        NodeHandlerResult r = f.get(3, TimeUnit.SECONDS);
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
        assertThat(r.errorMessage()).contains("bank refused");
        assertThat(ctx.getVariable("operator:op:note")).isEqualTo("bank refused");
        assertThat(events).contains(ExecutionEventType.OPERATOR_ANSWERED + "|op|fail: bank refused");
    }

    @Test
    void timeout_follows_on_timeout() throws Exception {
        ScenarioNode jump = node("op", NodeType.OPERATOR).cfg("instruction", "x")
                .timeout(timeout(100, TimeoutAction.JUMP, "later")).onSuccess("done").onFailure("fail").build();
        ExecutionContext ctx = ctx(scenario("s", start("op"), jump));
        NodeHandlerResult r = handler.handle(jump, ctx);
        assertThat(r.nextNodeId()).isEqualTo("later");
        assertThat(gateway.open(ctx.executionId())).isEmpty();

        ScenarioNode failing = node("op", NodeType.OPERATOR).cfg("instruction", "x")
                .timeout(timeout(100, TimeoutAction.FAIL, null)).onSuccess("done").onFailure("fail").build();
        NodeHandlerResult f = handler.handle(failing, ctx(scenario("s", start("op"), failing)));
        assertThat(f.success()).isFalse();
        assertThat(f.nextNodeId()).isEqualTo("fail");
        assertThat(f.errorMessage()).isEqualTo("timeout");
    }

    @Test
    void stop_cancels_the_prompt() throws Exception {
        ScenarioNode op = op();
        ExecutionContext ctx = ctx(scenario("s", start("op"), op));
        Thread[] worker = new Thread[1];
        Future<Object> f = pool.submit(() -> {
            worker[0] = Thread.currentThread();
            try {
                handler.handle(op, ctx);
                return "returned";
            } catch (InterruptedException e) {
                return "interrupted";
            }
        });
        await().atMost(Duration.ofSeconds(3)).until(() -> gateway.open(ctx.executionId()).isPresent());
        worker[0].interrupt();
        assertThat(f.get(3, TimeUnit.SECONDS)).isEqualTo("interrupted");
        assertThat(gateway.open(ctx.executionId())).isEmpty();
    }

    @Test
    void cancel_fails_the_node_and_drops_the_prompt() throws Exception {
        ScenarioNode op = op();
        ExecutionContext ctx = ctx(scenario("s", start("op"), op));
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(op, ctx));
        await().atMost(Duration.ofSeconds(3)).until(() -> gateway.open(ctx.executionId()).isPresent());
        gateway.cancel(ctx.executionId());
        NodeHandlerResult r = f.get(3, TimeUnit.SECONDS);
        assertThat(r.success()).isFalse();
        assertThat(gateway.answer(ctx.executionId(), "op", true, "")).isFalse();
    }

    @Test
    void parent_sees_the_prompt_of_a_child() throws Exception {
        ScenarioNode op = op();
        ExecutionContext parent = ctx(scenario("p", start("op")));
        ExecutionContext child = ctx(scenario("c", start("op"), op));
        child.setAncestors(List.of(parent.executionId()));
        Future<NodeHandlerResult> f = pool.submit(() -> handler.handle(op, child));
        await().atMost(Duration.ofSeconds(3)).until(() -> gateway.open(parent.executionId()).isPresent());
        assertThat(gateway.open(parent.executionId()).get().executionId()).isEqualTo(child.executionId());
        gateway.cancel(parent.executionId());
        f.get(3, TimeUnit.SECONDS);
        assertThat(gateway.open(parent.executionId())).isEmpty();
    }
}
