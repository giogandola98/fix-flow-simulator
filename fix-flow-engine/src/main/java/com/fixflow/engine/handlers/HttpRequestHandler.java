package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpRequestHandler implements NodeHandler {

    private final VariableResolver resolver;
    private final HttpClient client;

    public HttpRequestHandler(VariableResolver resolver) {
        this.resolver = resolver;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public NodeType getSupportedType() { return NodeType.HTTP_REQUEST; }

    @Override
    @SuppressWarnings("unchecked")
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        String method = cfg.getOrDefault("method", "GET").toString().toUpperCase();
        String rawUrl  = cfg.getOrDefault("url", "").toString();
        String url     = resolver.resolveAll(rawUrl, ctx);

        long timeoutMs = node.timeout() == null ? 30_000L : node.timeout().toMillis();

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs));

        Object headersObj = cfg.get("headers");
        if (headersObj instanceof Map<?,?> hm) {
            for (Map.Entry<?,?> e : hm.entrySet()) {
                req.header(
                    resolver.resolveAll(e.getKey().toString(), ctx),
                    resolver.resolveAll(e.getValue().toString(), ctx)
                );
            }
        }

        String rawBody = cfg.containsKey("body") ? cfg.get("body").toString() : null;
        String body    = rawBody != null ? resolver.resolveAll(rawBody, ctx) : null;

        HttpRequest.BodyPublisher publisher = body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

        req.method(method, publisher);

        try {
            HttpResponse<String> response = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            String nodePrefix = "node:" + node.id() + ":";
            ctx.setVariable(nodePrefix + "responseStatus", String.valueOf(response.statusCode()));
            ctx.setVariable(nodePrefix + "responseBody",   response.body() == null ? "" : response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return NodeHandlerResult.success(node.onSuccess());
            } else {
                return NodeHandlerResult.failure(node.onFailure(),
                        "HTTP " + response.statusCode());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (Exception ex) {
            return NodeHandlerResult.failure(node.onFailure(), ex.getMessage());
        }
    }
}
