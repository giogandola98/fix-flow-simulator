package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The single node-walk core shared by the top-level run ({@code ExecutionManager}), nested
 * sub-scenarios ({@code ScenarioExecutor}) and the LOOP handler. It dispatches nodes and follows
 * each result's {@code nextNodeId}, so a failure branch (e.g. a false DECISION routing to
 * {@code onFailure}) is honoured instead of terminating the run.
 *
 * <p>Traversal is cooperative: the status is checked before and after every dispatch, so a stop
 * request (which flips the status to STOPPED and interrupts the thread) unwinds promptly even from
 * inside a loop body.
 */
@Component
public class NodeWalker {

    private final NodeDispatcher dispatcher;

    public NodeWalker(NodeDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Walk from {@code start}, following successors until the graph ends, a node fails with no
     * branch, the status leaves RUNNING, or the next node equals {@code boundaryId}.
     *
     * @param boundaryId id of a node to stop <em>before</em> re-entering (the LOOP node, for a loop
     *                   body); pass {@code null} for an unbounded top-level walk.
     */
    public WalkOutcome walk(ScenarioNode start, ExecutionContext ctx, String boundaryId)
            throws InterruptedException {
        StepListener listener = ctx.stepListener();
        ScenarioNode current = start;
        while (current != null) {
            if (ctx.status() != ExecutionStatus.RUNNING) return WalkOutcome.HALTED;

            ctx.setCurrentNodeId(current.id());
            listener.entered(current, ctx);
            Instant startT = Instant.now();
            NodeHandlerResult result = dispatcher.dispatch(current, ctx);
            Instant endT = Instant.now();
            listener.completed(current, result, startT, endT, ctx);

            // An END node (or a concurrent stop) set a terminal status during dispatch.
            if (ctx.status() != ExecutionStatus.RUNNING) return WalkOutcome.HALTED;

            String next = result.nextNodeId();
            // A genuine failure with no failure branch: caller decides the terminal status.
            if (!result.success() && next == null) return WalkOutcome.FAILED;
            // A successful node with no successor: ran off the end of the graph.
            if (next == null) return WalkOutcome.COMPLETED;
            // Loop-back point reached: hand control back to the caller (LOOP handler).
            if (next.equals(boundaryId)) return WalkOutcome.BOUNDARY;

            current = ctx.scenario().findNode(next).orElse(null);
        }
        return WalkOutcome.COMPLETED;
    }
}
