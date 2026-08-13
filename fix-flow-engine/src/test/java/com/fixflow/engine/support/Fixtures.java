package com.fixflow.engine.support;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Concise builders for scenario graphs used across the engine tests. */
public final class Fixtures {

    private Fixtures() {}

    public static NodeBuilder node(String id, NodeType type) { return new NodeBuilder(id, type); }
    public static ScenarioNode start(String onSuccess) { return node("start", NodeType.START).onSuccess(onSuccess).build(); }
    public static ScenarioNode endPass(String id) { return node(id, NodeType.END_PASS).build(); }
    public static ScenarioNode endFail(String id) { return node(id, NodeType.END_FAIL).build(); }

    public static Scenario scenario(String name, ScenarioNode... nodes) {
        return scenario(UUID.randomUUID(), name, List.of(), nodes);
    }

    public static Scenario scenario(UUID id, String name, List<ScenarioEdge> edges, ScenarioNode... nodes) {
        return new Scenario(id, name, "desc", "1", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                Arrays.asList(nodes), edges, Map.of(), null);
    }

    public static ExecutionContext ctx(Scenario s) {
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    public static ExecutionContext ctx(Scenario s, UUID sessionId) {
        return new ExecutionContext(UUID.randomUUID(), s, sessionId);
    }

    public static TimeoutConfig timeout(long ms, TimeoutAction action, String jumpTo) {
        return new TimeoutConfig(ms, TimeUnit.MILLISECONDS, action, jumpTo);
    }

    public static final class NodeBuilder {
        private final String id;
        private String name;
        private final NodeType type;
        private final Map<String, Object> config = new HashMap<>();
        private TimeoutConfig timeout;
        private RetryPolicy retry;
        private String onSuccess;
        private String onFailure;
        private String onTimeout;

        NodeBuilder(String id, NodeType type) { this.id = id; this.type = type; this.name = id; }

        public NodeBuilder name(String n) { this.name = n; return this; }
        public NodeBuilder cfg(String k, Object v) { this.config.put(k, v); return this; }
        public NodeBuilder config(Map<String, Object> c) { this.config.putAll(c); return this; }
        public NodeBuilder timeout(TimeoutConfig t) { this.timeout = t; return this; }
        public NodeBuilder retry(RetryPolicy r) { this.retry = r; return this; }
        public NodeBuilder onSuccess(String s) { this.onSuccess = s; return this; }
        public NodeBuilder onFailure(String f) { this.onFailure = f; return this; }
        public NodeBuilder onTimeout(String t) { this.onTimeout = t; return this; }

        public ScenarioNode build() {
            return new ScenarioNode(id, name, type, new HashMap<>(config), timeout, retry, onSuccess, onFailure, onTimeout);
        }
    }

    public static Map<Integer, String> fields(Object... kv) {
        Map<Integer, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Integer) kv[i], String.valueOf(kv[i + 1]));
        return m;
    }

    /** A configurable {@link com.fixflow.engine.handlers.NodeHandler} for driving walker/loop/retry tests. */
    public static List<ScenarioEdge> noEdges() { return new ArrayList<>(); }
}
