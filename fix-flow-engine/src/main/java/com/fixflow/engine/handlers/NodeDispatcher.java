package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NodeDispatcher {

    private final Map<NodeType, NodeHandler> registry = new EnumMap<>(NodeType.class);

    public NodeDispatcher(List<NodeHandler> handlers) {
        for (NodeHandler h : handlers) registry.put(h.getSupportedType(), h);
        // Aliases: BRANCH behaves like DECISION; TIMEOUT behaves like WAIT
        registry.putIfAbsent(NodeType.BRANCH,  registry.get(NodeType.DECISION));
        registry.putIfAbsent(NodeType.TIMEOUT, registry.get(NodeType.WAIT));
    }

    public NodeHandlerResult dispatch(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        NodeHandler h = registry.get(node.type());
        if (h == null) {
            return NodeHandlerResult.failure(null, "No handler for node type " + node.type());
        }
        return h.handle(node, ctx);
    }
}
