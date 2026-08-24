package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SendFIXHandlerGroupTest {

    private final List<FIXMessageData> sent = new ArrayList<>();

    private final FIXSessionPort port = new FIXSessionPort() {
        public void connect(FIXSessionConfig config) {}
        public void disconnect(UUID sessionId) {}
        public void sendMessage(UUID sessionId, FIXMessageData message) { sent.add(message); }
        public boolean isConnected(UUID sessionId) { return true; }
        public void setInboundListener(InboundMessageListener listener) {}
    };

    private ExecutionContext ctx() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref",
                null, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    private ScenarioNode node(Map<String, Object> config) {
        return new ScenarioNode("send", "Send", NodeType.SEND_FIX, config, null, null, "next", null, null);
    }

    private Map<String, Object> field(int tag, String value) {
        return Map.of("tag", tag, "value", value);
    }

    @Test
    void emitsTwoLegEntriesInOrder() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("fields", List.of(field(11, "ORD-1")));
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(
                        Map.of("fields", List.of(field(600, "EUR/USD"), field(624, "1"))),
                        Map.of("fields", List.of(field(600, "EUR/USD"), field(624, "2")))
                ))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        FIXMessageData m = sent.get(0);
        assertEquals("AB", m.flatFields().get(35));
        assertEquals("ORD-1", m.flatFields().get(11));
        assertEquals(2, m.group(555).size());
        assertEquals("1", m.groupValue(555, 0, 624).orElseThrow());
        assertEquals("2", m.groupValue(555, 1, 624).orElseThrow());
        assertIterableEquals(List.of(600, 624), m.group(555).get(0).fields().keySet());
    }

    @Test
    void counterTagIsNeverWrittenAsAPlainField() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(600, "EUR/USD")))))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertNull(sent.get(0).flatFields().get(555));
    }

    @Test
    void placeholdersAreResolvedInsideGroupEntries() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(654, "{{seq:leg}}")))))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertEquals("1", sent.get(0).groupValue(555, 0, 654).orElseThrow());
    }

    @Test
    void sessionTagsAreFilteredInsideGroupEntriesToo() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("fields", List.of(field(49, "SENDER"), field(11, "ORD-1")));
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(52, "x"), field(600, "EUR/USD")))))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertNull(sent.get(0).flatFields().get(49));
        assertNull(sent.get(0).groupValue(555, 0, 52).orElse(null));
        assertEquals("EUR/USD", sent.get(0).groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void emptyEntryListDropsTheGroup() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "D");
        cfg.put("groups", List.of(Map.of("counterTag", 555, "entries", List.of())));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertTrue(sent.get(0).groups().isEmpty());
    }

    @Test
    void storedNodeMessageKeepsGroupsForLaterPlaceholders() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(600, "EUR/USD")))))));

        ExecutionContext c = ctx();
        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), c);

        assertEquals("EUR/USD", c.getNodeMessageData("send").groupValue(555, 0, 600).orElseThrow());
    }
}
