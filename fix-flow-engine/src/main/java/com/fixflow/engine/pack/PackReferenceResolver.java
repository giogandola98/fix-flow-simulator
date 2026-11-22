package com.fixflow.engine.pack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the references inside pack files between the names a pack uses and the ids the
 * engine stores.
 *
 * <p>Scenario files refer by name: {@code sessionRef: <session name>}, {@code datasetRef:
 * <data set name>} and, in a node's config, {@code targetScenario: <scenario name>} (the
 * {@code CALL_SCENARIO} and {@code PARALLEL} target). On import each becomes the id
 * ({@code targetScenario} turns into {@code targetScenarioId}); on export the ids go back to names.
 * The YAML is handled as a tree, so every other key and its order is kept as written.
 *
 * <p>Session files carry {@code ${NAME}} placeholders, replaced from the pack's values inside
 * string scalars only, so a value can never change the YAML structure.
 */
public final class PackReferenceResolver {

    private static final ObjectMapper YAML = PackManifestCodec.yamlMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private PackReferenceResolver() {}

    /** Pack names a scenario may refer to, with the ids they stand for. */
    public record Names(Map<String, UUID> scenarios, Map<String, UUID> datasets, Map<String, UUID> sessions) {}

    /** Ids of pack objects, with the names they are exported as. */
    public record Ids(Map<UUID, String> scenarios, Map<UUID, String> datasets, Map<UUID, String> sessions) {}

    static ObjectNode readObject(String file, String yaml) {
        JsonNode n;
        try {
            n = YAML.readTree(yaml);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(file + ": invalid YAML: " + firstLine(e.getOriginalMessage()), e);
        }
        if (!(n instanceof ObjectNode o)) throw new IllegalArgumentException(file + ": not a YAML mapping");
        return o;
    }

    static String write(Object n) {
        try {
            return YAML.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The scenario's {@code name}, required in a pack. */
    static String scenarioName(String file, ObjectNode tree) {
        JsonNode name = tree.get("name");
        if (name == null || !name.isTextual() || name.asText().isBlank()) {
            throw new IllegalArgumentException(file + ": scenario name is required");
        }
        return name.asText().trim();
    }

    /**
     * The stored form of a pack scenario: {@code id} first, names replaced by ids. An unknown
     * name fails with the file and the reference. {@code warnings} gets a line for a node that
     * still carries a raw {@code targetScenarioId}, which will not survive a move to another engine.
     */
    static String toStored(String file, ObjectNode tree, UUID id, Names names, List<String> warnings) {
        ObjectNode out = YAML.createObjectNode();
        out.put("id", id.toString());
        Iterator<Map.Entry<String, JsonNode>> it = tree.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (key.equals("id")) continue;
            JsonNode value = e.getValue().deepCopy();
            switch (key) {
                case "sessionRef" -> value = resolve(file, key, value, names.sessions(), "session");
                case "datasetRef" -> value = resolve(file, key, value, names.datasets(), "data set");
                case "nodes" -> resolveNodes(file, value, names.scenarios(), warnings);
                default -> { }
            }
            out.set(key, value);
        }
        return write(out);
    }

    private static JsonNode resolve(String file, String key, JsonNode value, Map<String, UUID> ids, String kind) {
        if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) return value;
        String name = value.asText().trim();
        UUID id = ids.get(name);
        if (id == null) {
            throw new IllegalArgumentException(file + ": " + key + " '" + name + "' is not a " + kind
                    + " of the pack (known: " + String.join(", ", ids.keySet()) + ")");
        }
        return TextNode.valueOf(id.toString());
    }

    private static void resolveNodes(String file, JsonNode nodes, Map<String, UUID> scenarios, List<String> warnings) {
        if (nodes == null || !nodes.isArray()) return;
        for (JsonNode node : nodes) {
            if (!(node.get("config") instanceof ObjectNode config)) continue;
            String nodeId = node.path("id").asText("?");
            if (config.has("targetScenario")) {
                String target = config.get("targetScenario").asText().trim();
                UUID id = scenarios.get(target);
                if (id == null) {
                    throw new IllegalArgumentException(file + ": node '" + nodeId + "' targetScenario '" + target
                            + "' is not a scenario of the pack");
                }
                renameKey(config, "targetScenario", "targetScenarioId", TextNode.valueOf(id.toString()));
            } else if (config.has("targetScenarioId")) {
                warnings.add(file + ": node '" + nodeId + "' uses targetScenarioId; write targetScenario: <name> "
                        + "so the pack works on another engine");
            }
        }
    }

    /** The pack form of a stored scenario: no {@code id}, ids of pack objects replaced by their names. */
    static String toPack(String yaml, Ids ids) {
        ObjectNode tree = readObject("scenario", yaml);
        ObjectNode out = YAML.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> it = tree.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (key.equals("id")) continue;
            JsonNode value = e.getValue();
            switch (key) {
                case "sessionRef" -> value = name(value, ids.sessions());
                case "datasetRef" -> value = name(value, ids.datasets());
                case "nodes" -> {
                    if (value.isArray()) {
                        for (JsonNode node : value) {
                            if (node.get("config") instanceof ObjectNode config && config.has("targetScenarioId")) {
                                UUID id = asUuid(config.get("targetScenarioId"));
                                String n = id == null ? null : ids.scenarios().get(id);
                                if (n != null) renameKey(config, "targetScenarioId", "targetScenario", TextNode.valueOf(n));
                            }
                        }
                    }
                }
                default -> { }
            }
            out.set(key, value);
        }
        return write(out);
    }

    private static JsonNode name(JsonNode value, Map<UUID, String> names) {
        UUID id = asUuid(value);
        String n = id == null ? null : names.get(id);
        return n == null ? value : TextNode.valueOf(n);
    }

    private static UUID asUuid(JsonNode v) {
        if (v == null || !v.isTextual()) return null;
        try {
            return UUID.fromString(v.asText().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Replaces {@code from} by {@code to} at the same position among the object's keys. */
    private static void renameKey(ObjectNode obj, String from, String to, JsonNode value) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        obj.fields().forEachRemaining(entries::add);
        obj.removeAll();
        for (Map.Entry<String, JsonNode> e : entries) {
            if (e.getKey().equals(from)) obj.set(to, value);
            else if (!e.getKey().equals(to)) obj.set(e.getKey(), e.getValue());
        }
    }

    /**
     * Reads a session file, replacing {@code ${NAME}} in string values from {@code values}. A
     * placeholder without a value fails, naming the file.
     */
    static PackSessionTemplate session(String file, String yaml, Map<String, String> values) {
        ObjectNode tree = readObject(file, yaml);
        substitute(file, tree, values);
        try {
            return YAML.treeToValue(tree, PackSessionTemplate.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(file + ": " + firstLine(e.getOriginalMessage()), e);
        }
    }

    private static void substitute(String file, JsonNode node, Map<String, String> values) {
        if (node instanceof ObjectNode obj) {
            List<String> keys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);
            for (String k : keys) {
                JsonNode child = obj.get(k);
                if (child.isTextual()) obj.set(k, TextNode.valueOf(fill(file, child.asText(), values)));
                else substitute(file, child, values);
            }
        } else if (node instanceof ArrayNode arr) {
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isTextual()) arr.set(i, TextNode.valueOf(fill(file, child.asText(), values)));
                else substitute(file, child, values);
            }
        }
    }

    private static String fill(String file, String s, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null) {
                throw new IllegalArgumentException(file + ": ${" + m.group(1) + "} is not a value of the pack");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String firstLine(String s) {
        return s == null ? "" : s.split("\\R", 2)[0].trim();
    }
}
