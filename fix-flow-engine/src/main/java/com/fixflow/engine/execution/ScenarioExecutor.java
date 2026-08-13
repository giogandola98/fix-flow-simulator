package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.NodeDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScenarioExecutor implements ScenarioExecutorPort {

    private final NodeWalker walker;

    @Autowired
    public ScenarioExecutor(NodeWalker walker) {
        this.walker = walker;
    }

    /** Convenience constructor for unit tests that supply a dispatcher directly. */
    public ScenarioExecutor(NodeDispatcher dispatcher) {
        this(new NodeWalker(dispatcher));
    }

    public ExecutionStatus execute(Scenario scenario, ExecutionContext ctx)
            throws InterruptedException {
        ScenarioNode start = scenario.startNode()
                .orElseThrow(() -> new IllegalStateException("Scenario has no START node: " + scenario.id()));

        WalkOutcome outcome = walker.walk(start, ctx, null);

        if (outcome == WalkOutcome.FAILED) {
            ctx.setStatus(ExecutionStatus.FAILED);
        } else if (ctx.status() == ExecutionStatus.RUNNING) {
            ctx.setStatus(ExecutionStatus.PASSED);
        }
        return ctx.status();
    }
}
