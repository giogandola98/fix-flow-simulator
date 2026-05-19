package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import org.springframework.stereotype.Service;

@Service
public class ScenarioExecutor {

    private final NodeDispatcher dispatcher;

    public ScenarioExecutor(NodeDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public ExecutionStatus execute(Scenario scenario, ExecutionContext ctx)
            throws InterruptedException {
        ScenarioNode current = scenario.startNode()
                .orElseThrow(() -> new IllegalStateException("Scenario has no START node: " + scenario.id()));

        while (current != null && ctx.status() == ExecutionStatus.RUNNING) {
            ctx.setCurrentNodeId(current.id());
            NodeHandlerResult result = dispatcher.dispatch(current, ctx);

            if (!result.success()) {
                ctx.setStatus(ExecutionStatus.FAILED);
                return ExecutionStatus.FAILED;
            }
            if (result.nextNodeId() == null) break;
            current = scenario.findNode(result.nextNodeId()).orElse(null);
        }

        if (ctx.status() == ExecutionStatus.RUNNING) {
            ctx.setStatus(ExecutionStatus.PASSED);
        }
        return ctx.status();
    }
}
