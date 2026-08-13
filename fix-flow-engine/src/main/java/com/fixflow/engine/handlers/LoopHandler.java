package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.execution.NodeWalker;
import com.fixflow.engine.execution.WalkOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class LoopHandler implements NodeHandler {

    private final NodeWalker walker;

    @Autowired
    public LoopHandler(@Lazy NodeWalker walker) { this.walker = walker; }

    /** Convenience constructor for unit tests that supply a dispatcher directly. */
    public LoopHandler(NodeDispatcher dispatcher) { this(new NodeWalker(dispatcher)); }

    @Override
    public NodeType getSupportedType() { return NodeType.LOOP; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        ScenarioNode target = ctx.scenario().findNode(targetId)
            .orElseThrow(() -> new IllegalStateException("node not found: " + targetId));

        int iterations = node.config().get("iterations") == null
            ? 1
            : ((Number) node.config().get("iterations")).intValue();

        // Each iteration walks the whole sub-block starting at the target and following the
        // onSuccess chain, stopping when it loops back to this LOOP node (the boundary). This is
        // what makes the entire section repeat, not just the single attached node.
        for (int i = 0; i < iterations; i++) {
            if (ctx.status() != ExecutionStatus.RUNNING) break;
            WalkOutcome outcome = walker.walk(target, ctx, node.id());
            if (outcome == WalkOutcome.HALTED) {
                // An END node inside the body ended the run, or a stop was requested: propagate.
                return NodeHandlerResult.terminal();
            }
            if (outcome == WalkOutcome.FAILED) {
                return NodeHandlerResult.failure(node.onFailure(),
                    "loop iteration " + i + " failed");
            }
            // BOUNDARY (looped back here) or COMPLETED (body ran to a dead end): iteration done.
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
