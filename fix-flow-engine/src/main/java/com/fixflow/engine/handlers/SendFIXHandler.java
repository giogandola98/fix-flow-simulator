package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SendFIXHandler implements NodeHandler {

    private final FIXSessionPort port;
    private final VariableResolver variableResolver;

    public SendFIXHandler(FIXSessionPort port, VariableResolver variableResolver) {
        this.port = port;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.SEND_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        Map<String, Object> cfg = node.config();
        Map<Integer, String> outFields = new HashMap<>();

        Object msgType = cfg.get("msgType");
        if (msgType != null) outFields.put(35, variableResolver.resolveAll(String.valueOf(msgType), ctx));

        Object fields = cfg.get("fields");
        if (fields instanceof Map<?, ?> raw) {
            // Map format: { 11: "value", 55: "AAPL" }
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                int tag = Integer.parseInt(String.valueOf(e.getKey()));
                outFields.put(tag, variableResolver.resolveAll(String.valueOf(e.getValue()), ctx));
            }
        } else if (fields instanceof java.util.List<?> list) {
            // List format: [{ tag: 11, value: "..." }, ...]
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    Object tagObj = row.get("tag");
                    Object valObj = row.get("value");
                    if (tagObj != null && valObj != null) {
                        int tag = Integer.parseInt(String.valueOf(tagObj));
                        outFields.put(tag, variableResolver.resolveAll(String.valueOf(valObj), ctx));
                    }
                }
            }
        }

        port.sendMessage(ctx.sessionId(), outFields);
        ctx.storeNodeMessage(node.id(), outFields);
        return NodeHandlerResult.success(node.onSuccess());
    }
}
