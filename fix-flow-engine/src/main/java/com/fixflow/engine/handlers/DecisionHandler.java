package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DecisionHandler implements NodeHandler {

    private static final Pattern COND = Pattern.compile(
        "^\\s*(.+?)\\s*(==|!=|contains)\\s*(.+?)\\s*$"
    );

    private final VariableResolver resolver;

    public DecisionHandler(VariableResolver resolver) { this.resolver = resolver; }

    @Override
    public NodeType getSupportedType() { return NodeType.DECISION; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        String condition = (String) node.config().get("condition");
        if (condition == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing condition");
        }
        String resolvedCond = resolver.resolveAll(condition, ctx);
        boolean result = evaluate(resolvedCond);
        return result
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "condition false");
    }

    private boolean evaluate(String expr) {
        Matcher m = COND.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported condition: " + expr);
        }
        String left = unquote(m.group(1).trim());
        String op = m.group(2);
        String right = unquote(m.group(3).trim());
        return switch (op) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "contains" -> left.contains(right);
            default -> throw new IllegalArgumentException("Unknown operator: " + op);
        };
    }

    private String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
