package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.Test;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class StartEndHandlerTest {

    private ExecutionContext ctx() {
        Scenario s = scenario("s", start("end"), Fixtures.endPass("end"));
        return Fixtures.ctx(s);
    }

    @Test
    void startFollowsOnSuccessAndSupportsStartType() throws Exception {
        StartHandler h = new StartHandler();
        assertThat(h.getSupportedType()).isEqualTo(NodeType.START);
        NodeHandlerResult r = h.handle(node("start", NodeType.START).onSuccess("next").build(), ctx());
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void endPassSetsPassedAndTerminates() throws Exception {
        EndHandler h = new EndHandler();
        assertThat(h.getSupportedType()).isEqualTo(NodeType.END_PASS);
        ExecutionContext ctx = ctx();
        NodeHandlerResult r = h.handle(node("end", NodeType.END_PASS).build(), ctx);
        assertThat(ctx.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(r.nextNodeId()).isNull();
        assertThat(r.success()).isTrue();
    }

    @Test
    void endFailSetsFailedAndTerminates() throws Exception {
        EndFailHandler h = new EndFailHandler();
        assertThat(h.getSupportedType()).isEqualTo(NodeType.END_FAIL);
        ExecutionContext ctx = ctx();
        NodeHandlerResult r = h.handle(node("end", NodeType.END_FAIL).build(), ctx);
        assertThat(ctx.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.nextNodeId()).isNull();
        assertThat(r.success()).isTrue();
    }

    @Test
    void nodeHandlerResultFactories() {
        assertThat(NodeHandlerResult.success("x")).extracting(NodeHandlerResult::success,
                NodeHandlerResult::nextNodeId).containsExactly(true, "x");
        assertThat(NodeHandlerResult.failure("y", "err")).extracting(NodeHandlerResult::success,
                NodeHandlerResult::nextNodeId, NodeHandlerResult::errorMessage).containsExactly(false, "y", "err");
        assertThat(NodeHandlerResult.terminal()).extracting(NodeHandlerResult::success,
                NodeHandlerResult::nextNodeId).containsExactly(true, null);
    }
}
