package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SendFIXHandler implements NodeHandler {

    private static final Set<Integer> SESSION_TAGS = Set.of(8, 9, 10, 34, 49, 52, 56);

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

        Map<Integer, String> outFields = resolveFields(cfg.get("fields"), ctx);

        Object msgType = cfg.get("msgType");
        if (msgType != null) {
            Map<Integer, String> withType = new LinkedHashMap<>();
            withType.put(35, variableResolver.resolveAll(String.valueOf(msgType), ctx));
            withType.putAll(outFields);
            outFields = withType;
        }

        FIXMessageData message = new FIXMessageData(outFields, resolveGroups(cfg.get("groups"), ctx));

        port.sendMessage(ctx.sessionId(), message);
        ctx.storeNodeMessage(node.id(), message);
        return NodeHandlerResult.success(node.onSuccess());
    }

    /** Accepts both the map form ({tag: value}) and the list form ([{tag, value}]). */
    private Map<Integer, String> resolveFields(Object fields, ExecutionContext ctx) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (fields instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                put(out, String.valueOf(e.getKey()), e.getValue(), ctx);
            }
        } else if (fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    Object tagObj = row.get("tag");
                    Object valObj = row.get("value");
                    if (tagObj != null && valObj != null) {
                        put(out, String.valueOf(tagObj), valObj, ctx);
                    }
                }
            }
        }
        return out;
    }

    private void put(Map<Integer, String> out, String rawTag, Object value, ExecutionContext ctx) {
        int tag = Integer.parseInt(rawTag.trim());
        if (SESSION_TAGS.contains(tag)) return;
        out.put(tag, variableResolver.resolveAll(String.valueOf(value), ctx));
    }

    private Map<Integer, List<FIXMessageData>> resolveGroups(Object groups, ExecutionContext ctx) {
        Map<Integer, List<FIXMessageData>> out = new LinkedHashMap<>();
        if (!(groups instanceof List<?> list)) return out;

        for (Object g : list) {
            if (!(g instanceof Map<?, ?> group)) continue;
            Object counterObj = group.get("counterTag");
            if (counterObj == null) continue;
            int counterTag = Integer.parseInt(String.valueOf(counterObj).trim());

            List<FIXMessageData> entries = new ArrayList<>();
            if (group.get("entries") instanceof List<?> rawEntries) {
                for (Object e : rawEntries) {
                    if (!(e instanceof Map<?, ?> entry)) continue;
                    Map<Integer, String> entryFields = resolveFields(entry.get("fields"), ctx);
                    if (entryFields.isEmpty()) continue;
                    entries.add(new FIXMessageData(entryFields, resolveGroups(entry.get("groups"), ctx)));
                }
            }
            // An empty group is dropped: QuickFIX/J would otherwise emit a zero counter.
            if (!entries.isEmpty()) out.put(counterTag, entries);
        }
        return out;
    }
}
