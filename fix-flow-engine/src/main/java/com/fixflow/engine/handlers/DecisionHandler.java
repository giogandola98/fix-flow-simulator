package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        List<Branch> branches = branches(node);
        return branches.isEmpty() ? handleSingleCondition(node, ctx) : handleBranches(node, ctx, branches);
    }

    /**
     * Multi-branch form: branches are tested in order, a branch is taken when <em>all</em> of its
     * conditions hold, and a branch with no conditions is a catch-all default — the same contract
     * ROUTE_FIX rules have, over conditions instead of tag matchers (issue #86).
     *
     * <p>With no branch matched and no default, the node fails to {@code onFailure}, which is what
     * a false condition has always done.
     */
    private NodeHandlerResult handleBranches(ScenarioNode node, ExecutionContext ctx, List<Branch> branches) {
        Branch defaultBranch = null;
        for (Branch branch : branches) {
            if (branch.conditions().isEmpty()) {
                // First default wins; later ones are unreachable, exactly like ROUTE_FIX.
                if (defaultBranch == null) defaultBranch = branch;
                continue;
            }
            if (branch.conditions().stream().allMatch(c -> evaluate(resolver.resolveAll(c, ctx)))) {
                return takeBranch(node, ctx, branch);
            }
        }
        if (defaultBranch != null) return takeBranch(node, ctx, defaultBranch);
        return NodeHandlerResult.failure(node.onFailure(), "no branch matched");
    }

    private NodeHandlerResult takeBranch(ScenarioNode node, ExecutionContext ctx, Branch branch) {
        ctx.setVariable("node:" + node.id() + ":matchedBranchId", branch.branchId());
        ctx.setVariable("node:" + node.id() + ":matchedBranchLabel",
                branch.label().isEmpty() ? branch.branchId() : branch.label());
        String target = branch.targetNodeId().isEmpty() ? node.onSuccess() : branch.targetNodeId();
        return NodeHandlerResult.success(target);
    }

    /** Legacy binary form: one {@code condition}, true to {@code onSuccess}, false to {@code onFailure}. */
    private NodeHandlerResult handleSingleCondition(ScenarioNode node, ExecutionContext ctx) {
        String condition = (String) node.config().get("condition");
        if (condition == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing condition");
        }
        return evaluate(resolver.resolveAll(condition, ctx))
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "condition false");
    }

    @SuppressWarnings("unchecked")
    private List<Branch> branches(ScenarioNode node) {
        Object raw = node.config().get("branches");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Branch> branches = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Map<String, Object> m = (Map<String, Object>) map;
            List<String> conditions = new ArrayList<>();
            if (m.get("conditions") instanceof List<?> raws) {
                for (Object c : raws) {
                    String condition = c == null ? "" : String.valueOf(c).trim();
                    // A blank row in the editor is not a condition; treating it as one would make
                    // an in-progress branch either always fail or silently become the default.
                    if (!condition.isEmpty()) conditions.add(condition);
                }
            }
            branches.add(new Branch(
                    Objects.toString(m.get("branchId"), "branch-" + branches.size()),
                    Objects.toString(m.getOrDefault("label", ""), ""),
                    List.copyOf(conditions),
                    Objects.toString(m.get("targetNodeId"), "")));
        }
        return branches;
    }

    private record Branch(String branchId, String label, List<String> conditions, String targetNodeId) {}

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
