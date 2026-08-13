package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.FakeFixAdapter;
import com.fixflow.engine.support.Fixtures;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;

class SendFIXHandlerTest {

    private FakeFixAdapter port;
    private SendFIXHandler handler;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        port = new FakeFixAdapter();
        handler = new SendFIXHandler(port, new VariableResolver());
        sessionId = UUID.randomUUID();
    }

    private ExecutionContext ctx(Scenario s) {
        return new ExecutionContext(UUID.randomUUID(), s, sessionId);
    }

    @Test
    void supportsSendFix() {
        assertThat(handler.getSupportedType()).isEqualTo(NodeType.SEND_FIX);
    }

    @Test
    void sendsMsgTypeAndMapFieldsFilteringSessionTags() throws Exception {
        Map<String, Object> fields = Map.of(
                "11", "ORD1",
                "44", "10.5",
                "8", "FIX.4.4",   // session tag - filtered
                "34", "5",         // session tag - filtered
                "49", "SENDER");   // session tag - filtered
        Scenario s = scenario("s", start("send"),
                node("send", NodeType.SEND_FIX).cfg("msgType", "D").cfg("fields", fields)
                        .onSuccess("next").build());
        NodeHandlerResult r = handler.handle(s.findNode("send").get(), ctx(s));

        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("next");
        Map<Integer, String> sent = port.lastSent();
        assertThat(sent).containsEntry(35, "D").containsEntry(11, "ORD1").containsEntry(44, "10.5");
        assertThat(sent).doesNotContainKeys(8, 9, 10, 34, 49, 52, 56);
    }

    @Test
    void filtersAllEngineTags() throws Exception {
        Map<String, Object> fields = new java.util.HashMap<>();
        for (int tag : new int[]{8, 9, 10, 34, 49, 52, 56}) fields.put(String.valueOf(tag), "x");
        fields.put("55", "AAPL");
        Scenario s = scenario("s", start("send"),
                node("send", NodeType.SEND_FIX).cfg("fields", fields).onSuccess("n").build());
        handler.handle(s.findNode("send").get(), ctx(s));
        assertThat(port.lastSent()).containsOnlyKeys(55);
    }

    @Test
    void sendsListFormFields() throws Exception {
        List<Map<String, Object>> list = List.of(
                Map.of("tag", 44, "value", "10"),
                Map.of("tag", 54, "value", "1"),
                Map.of("tag", 8, "value", "FIX.4.4"), // filtered
                Map.of("nope", "x"));                  // missing tag/value -> skipped
        Scenario s = scenario("s", start("send"),
                node("send", NodeType.SEND_FIX).cfg("fields", list).onSuccess("n").build());
        handler.handle(s.findNode("send").get(), ctx(s));
        assertThat(port.lastSent()).containsEntry(44, "10").containsEntry(54, "1").doesNotContainKey(8);
    }

    @Test
    void resolvesVariablesInValues() throws Exception {
        Scenario s = scenario("s", start("send"),
                node("send", NodeType.SEND_FIX)
                        .cfg("fields", Map.of("44", "{{var:price}}"))
                        .onSuccess("n").build());
        ExecutionContext ctx = ctx(s);
        ctx.setVariable("price", "99.9");
        handler.handle(s.findNode("send").get(), ctx);
        assertThat(port.lastSent()).containsEntry(44, "99.9");
    }

    @Test
    void storesSentMessageOnContext() throws Exception {
        Scenario s = scenario("s", start("send"),
                node("send", NodeType.SEND_FIX).cfg("msgType", "D")
                        .cfg("fields", Map.of("11", "ORD1")).onSuccess("n").build());
        ExecutionContext ctx = ctx(s);
        handler.handle(s.findNode("send").get(), ctx);
        assertThat(ctx.getNodeMessage("send")).containsEntry(35, "D").containsEntry(11, "ORD1");
    }
}
