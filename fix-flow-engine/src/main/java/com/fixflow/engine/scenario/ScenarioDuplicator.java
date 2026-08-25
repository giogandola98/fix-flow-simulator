package com.fixflow.engine.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copies a scenario's YAML, giving the copy a fresh scenario id and fresh node ids while rewriting
 * every reference to those ids.
 *
 * <p>It works on the YAML document rather than on the {@code Scenario} domain record on purpose:
 * the raw YAML is what the editor round-trips, and it carries keys the domain model does not
 * model — node {@code position} above all. Re-serialising through {@code ScenarioDslParser} would
 * silently flatten a duplicated scenario's layout.
 *
 * <p>References are rewritten by walking the whole tree and replacing any string that <em>is</em> a
 * node id, plus any {@code {{node:<id>:...}}} placeholder that names one. That covers
 * {@code onSuccess}/{@code onFailure}/{@code onTimeout}, {@code timeout.jumpTo}, the visual
 * {@code edges}, ROUTE_FIX {@code targetNodeId}, EXPECT_FIX {@code correlation.fromNode}, VALIDATE
 * {@code sourceNodeId}, date rules' {@code sourceNode} and variable placeholders — without a
 * per-field list that would silently miss whichever reference is added next.
 */
@Component
public class ScenarioDuplicator {

    private static final String PLACEHOLDER_PREFIX = "{{node:";

    private final ObjectMapper mapper;

    public ScenarioDuplicator() {
        YAMLFactory yf = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.mapper = new ObjectMapper(yf).setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * @param yaml    the source scenario's YAML
     * @param newName the copy's name
     * @return the copy's YAML: same graph, new scenario id, new node ids, references rewritten
     */
    public String duplicate(String yaml, String newName) {
        try {
            Map<String, Object> doc = mapper.readValue(yaml, new TypeReference<LinkedHashMap<String, Object>>() {});
            Map<String, String> idMap = newNodeIds(doc.get("nodes"));

            @SuppressWarnings("unchecked")
            Map<String, Object> copy = (Map<String, Object>) rewrite(doc, idMap);
            // Set after the walk: the scenario id is not a node id, and the name is replaced
            // outright rather than rewritten.
            copy.put("id", UUID.randomUUID().toString());
            copy.put("name", newName);
            return mapper.writeValueAsString(copy);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to duplicate scenario", e);
        }
    }

    private Map<String, String> newNodeIds(Object nodes) {
        Map<String, String> idMap = new LinkedHashMap<>();
        if (!(nodes instanceof List<?> list)) return idMap;
        for (Object node : list) {
            if (node instanceof Map<?, ?> m && m.get("id") != null) {
                String id = String.valueOf(m.get("id"));
                if (!id.isBlank()) idMap.put(id, UUID.randomUUID().toString());
            }
        }
        return idMap;
    }

    private Object rewrite(Object value, Map<String, String> idMap) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), rewrite(v, idMap)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(rewrite(item, idMap));
            return out;
        }
        if (value instanceof String s) return rewriteString(s, idMap);
        return value;
    }

    private String rewriteString(String value, Map<String, String> idMap) {
        String direct = idMap.get(value);
        if (direct != null) return direct;
        if (!value.contains(PLACEHOLDER_PREFIX)) return value;
        String out = value;
        for (Map.Entry<String, String> e : idMap.entrySet()) {
            out = out.replace(PLACEHOLDER_PREFIX + e.getKey() + ":", PLACEHOLDER_PREFIX + e.getValue() + ":");
        }
        return out;
    }
}
