package com.fixflow.engine.execution;

import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.NodeHandlerResult;

import java.time.Instant;

/**
 * Per-node hooks invoked by {@link NodeWalker} as it traverses a scenario. The top-level run
 * installs an implementation that emits events and persists node results/messages, so nodes
 * executed inside a LOOP sub-block are recorded exactly like top-level nodes. Sub-scenario walks
 * that need no persistence simply leave the context's listener at {@link #NOOP}.
 */
public interface StepListener {

    StepListener NOOP = new StepListener() {};

    /** Called just before a node is dispatched. */
    default void entered(ScenarioNode node, ExecutionContext ctx) {}

    /** Called immediately after a node is dispatched, with its result and timing. */
    default void completed(ScenarioNode node, NodeHandlerResult result,
                           Instant start, Instant end, ExecutionContext ctx) {}
}
