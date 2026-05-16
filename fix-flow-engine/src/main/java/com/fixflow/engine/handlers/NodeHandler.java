package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;

public interface NodeHandler {
    NodeType getSupportedType();
    NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException;
}
