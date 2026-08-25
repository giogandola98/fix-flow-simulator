package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Component
public class ExpectFIXHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ExpectFIXHandler.class);

    private final CorrelationEngine correlation;
    private final MessageRouter router;

    public ExpectFIXHandler(CorrelationEngine correlation, MessageRouter router) {
        this.correlation = correlation;
        this.router = router;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.EXPECT_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        try {
            Map<Integer, String> matchers;
            try {
                matchers = buildMatchers(node, ctx);
            } catch (UnresolvableCorrelationException e) {
                return NodeHandlerResult.failure(node.onFailure(), e.getMessage());
            }

            if (matchers.isEmpty()) {
                log.warn("EXPECT_FIX node {} has neither a msgType nor a correlation: it will accept "
                        + "the first application message on the session", node.id());
            }

            String sessionIdStr = ctx.sessionId() != null ? ctx.sessionId().toString() : "";
            CompletableFuture<FIXMessageData> future =
                    correlation.registerAll(ctx.executionId().toString(), sessionIdStr, matchers);
            if (ctx.sessionId() != null) router.drain(ctx.sessionId().toString());

            long timeoutMs = node.timeout() == null ? 5_000L : node.timeout().toMillis();

            FIXMessageData received = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            ctx.storeInboundMessage(node.id(), received);
            return NodeHandlerResult.success(node.onSuccess());
        } catch (TimeoutException timeout) {
            correlation.cancel(ctx.executionId().toString());
            return onTimeout(node);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            correlation.cancel(ctx.executionId().toString());
            throw ie;
        } catch (Exception other) {
            correlation.cancel(ctx.executionId().toString());
            return NodeHandlerResult.failure(node.onFailure(), other.getMessage());
        }
    }

    /**
     * Builds the set of {@code tag -> value} conditions the inbound message must satisfy.
     *
     * <p>MsgType and correlation are ANDed, never mutually exclusive: an EXPECT_FIX that waits for
     * an execution report answering a specific order must check both tag 35 and the ClOrdID.
     *
     * <p>A {@code correlation} block that configures nothing is treated as absent. The graphical
     * editor always writes the key (as {@code correlation: {}}) even when the user only set a
     * MsgType; reading that empty map as "correlate on tag 11 against the empty string" made the
     * node wait forever on every scenario authored from the GUI (issue #77).
     */
    private Map<Integer, String> buildMatchers(ScenarioNode node, ExecutionContext ctx) {
        Map<String, Object> cfg = node.config();
        Map<Integer, String> matchers = new LinkedHashMap<>();

        String msgType = cfg.get("msgType") == null ? "" : String.valueOf(cfg.get("msgType")).trim();
        if (!msgType.isEmpty()) matchers.put(35, msgType);

        Object corrObj = cfg.get("correlation");
        if (corrObj instanceof Map<?, ?> corr && !corr.isEmpty()) {
            // A String sourceTag is a genuine config error and must surface as a node failure,
            // not as a silently ignored correlation — hence the unguarded Number cast.
            int sourceTag = corr.get("sourceTag") != null
                    ? ((Number) corr.get("sourceTag")).intValue() : 11;
            int targetTag = corr.get("targetTag") != null
                    ? ((Number) corr.get("targetTag")).intValue() : sourceTag;
            String fromNode = blankToNull(corr.get("fromNode"));

            if (fromNode != null) {
                Map<Integer, String> sent = ctx.getNodeMessage(fromNode);
                String expected = sent == null ? null : sent.get(targetTag);
                if (expected == null) {
                    throw new UnresolvableCorrelationException(
                            "correlation source node '" + fromNode + "' has no tag " + targetTag
                                    + " to correlate on");
                }
                matchers.put(sourceTag, expected);
            } else if (corr.get("expectedValue") != null) {
                matchers.put(sourceTag, String.valueOf(corr.get("expectedValue")));
            }
            // else: a correlation block with only tag numbers and no value to compare against
            // carries no condition — the msgType (if any) is the whole predicate.
        } else if (cfg.get("correlationTag") != null) {
            // Legacy flat format: correlationTag + expectedValue.
            int tag = ((Number) cfg.get("correlationTag")).intValue();
            matchers.put(tag, String.valueOf(cfg.get("expectedValue")));
        }

        return matchers;
    }

    private static String blankToNull(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    /** A correlation that names a source node whose value is not available at run time. */
    static final class UnresolvableCorrelationException extends RuntimeException {
        UnresolvableCorrelationException(String message) { super(message); }
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
}
