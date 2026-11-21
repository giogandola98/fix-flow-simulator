package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.operator.OperatorGateway;
import com.fixflow.engine.operator.OperatorGateway.OperatorAnswer;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OPERATOR: shows the (placeholder-resolved) {@code instruction} to a person who acts in another
 * system and answers in the UI. A confirm follows {@code onSuccess}, a fail {@code onFailure}; the
 * note is kept as variable {@code operator:<nodeId>:note}. The node timeout (default 10 minutes)
 * applies the usual {@code onTimeout} action.
 */
@Component
public class OperatorHandler implements NodeHandler {

    static final long DEFAULT_TIMEOUT_MS = 10 * 60_000L;

    private final OperatorGateway gateway;
    private final VariableResolver resolver;

    @Autowired
    public OperatorHandler(OperatorGateway gateway, VariableResolver resolver) {
        this.gateway = gateway;
        this.resolver = resolver;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.OPERATOR; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        String instruction = resolver.resolveAll(text(cfg.get("instruction")), ctx);
        CompletableFuture<OperatorAnswer> future = gateway.open(ctx.executionId(), ctx.ancestors(), node.id(),
                instruction, blankToNull(cfg.get("confirmLabel")), blankToNull(cfg.get("failLabel")));
        ctx.emitNodeEvent(ExecutionEventType.OPERATOR_PROMPT, node.id(), instruction);
        long timeoutMs = node.timeout() == null ? DEFAULT_TIMEOUT_MS : node.timeout().toMillis();
        try {
            OperatorAnswer answer = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            String note = answer.note() == null ? "" : answer.note();
            ctx.setVariable("operator:" + node.id() + ":note", note);
            ctx.emitNodeEvent(ExecutionEventType.OPERATOR_ANSWERED, node.id(),
                    (answer.ok() ? "ok" : "fail") + ": " + note);
            return answer.ok() ? NodeHandlerResult.success(node.onSuccess())
                    : NodeHandlerResult.failure(node.onFailure(), "operator failed the step"
                            + (note.isEmpty() ? "" : ": " + note));
        } catch (TimeoutException timeout) {
            return onTimeout(node);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (CancellationException cancelled) {
            return NodeHandlerResult.failure(node.onFailure(), "operator prompt cancelled");
        } catch (Exception other) {
            return NodeHandlerResult.failure(node.onFailure(), other.getMessage());
        } finally {
            gateway.close(ctx.executionId(), node.id());
        }
    }

    private NodeHandlerResult onTimeout(ScenarioNode node) {
        TimeoutAction action = node.timeout() == null ? TimeoutAction.FAIL : node.timeout().onTimeout();
        return switch (action) {
            case FAIL     -> NodeHandlerResult.failure(node.onFailure(), "timeout");
            case CONTINUE -> NodeHandlerResult.success(node.onSuccess());
            case RETRY    -> NodeHandlerResult.failure(node.onFailure(), "timeout-retry-exhausted");
            case JUMP     -> NodeHandlerResult.success(node.timeout().jumpTo());
        };
    }

    private static String text(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String blankToNull(Object o) {
        String s = text(o).trim();
        return s.isEmpty() ? null : s;
    }
}
