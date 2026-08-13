package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestHandlerTest {

    private HttpServer server;
    private int port;
    private final HttpRequestHandler handler = new HttpRequestHandler(new VariableResolver());

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200, "hello"));
        server.createContext("/bad", ex -> respond(ex, 500, "err"));
        server.createContext("/echo", ex -> respond(ex, 201, ex.getRequestMethod()));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private ExecutionContext ctx() { return Fixtures.ctx(scenario("s", start("h"))); }

    @Test
    void supportsHttpRequest() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.HTTP_REQUEST);
    }

    @Test
    void twoHundredResponseSucceedsAndStoresResponseVars() throws Exception {
        ExecutionContext ctx = ctx();
        ScenarioNode n = node("h", NodeType.HTTP_REQUEST)
                .cfg("method", "GET").cfg("url", "http://127.0.0.1:" + port + "/ok")
                .onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(n, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(ctx.getVariable("node:h:responseStatus")).isEqualTo("200");
        assertThat(ctx.getVariable("node:h:responseBody")).isEqualTo("hello");
    }

    @Test
    void nonTwoHundredResponseFails() throws Exception {
        ScenarioNode n = node("h", NodeType.HTTP_REQUEST)
                .cfg("url", "http://127.0.0.1:" + port + "/bad").onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(n, ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
        assertThat(r.errorMessage()).contains("HTTP 500");
    }

    @Test
    void resolvesUrlVariableAndSendsHeadersAndBody() throws Exception {
        ExecutionContext ctx = ctx();
        ctx.setVariable("host", "127.0.0.1:" + port);
        ScenarioNode n = node("h", NodeType.HTTP_REQUEST)
                .cfg("method", "POST").cfg("url", "http://{{var:host}}/echo")
                .cfg("headers", Map.of("X-Test", "v"))
                .cfg("body", "payload").onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(n, ctx);
        assertThat(r.success()).isTrue();
        assertThat(ctx.getVariable("node:h:responseStatus")).isEqualTo("201");
        assertThat(ctx.getVariable("node:h:responseBody")).isEqualTo("POST");
    }

    @Test
    void connectionErrorRoutesOnFailure() throws Exception {
        ScenarioNode n = node("h", NodeType.HTTP_REQUEST)
                .cfg("url", "http://127.0.0.1:1/nope").onSuccess("ok").onFailure("no").build();
        NodeHandlerResult r = handler.handle(n, ctx());
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("no");
    }
}
