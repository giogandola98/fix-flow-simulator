package com.fixflow.engine.execution;

/** Reason a {@link NodeWalker} traversal stopped. */
public enum WalkOutcome {
    /** Ran off the end of the graph (a node returned no successor) with the run still RUNNING. */
    COMPLETED,
    /** Reached the boundary node (loop-back point) supplied by the caller. */
    BOUNDARY,
    /** A node failed with no failure branch (nextNodeId == null); the caller decides the status. */
    FAILED,
    /** The context status left RUNNING mid-walk — an END node set a terminal status, or a stop was requested. */
    HALTED
}
