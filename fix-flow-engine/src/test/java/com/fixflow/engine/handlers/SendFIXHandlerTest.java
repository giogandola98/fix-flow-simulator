package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.FakeFixAdapter;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendFIXHandlerTest {

    @Test
    void sendsCustomPrivateTagsAbove9999() {
        FakeFixAdapter fake = new FakeFixAdapter();
        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, true, false));

        SendFIXHandler handler = new SendFIXHandler(fake, new VariableResolver());

        // Tag 500006 is a private/proprietary tag (above FIX user-defined range 5000-9999)
        ScenarioNode node = new ScenarioNode("n1", "send", NodeType.SEND_FIX,
                Map.of("msgType", "D", "fields", Map.of("11", "CL-1", "55", "AAPL", "500006", "custom-val")),
                null, null, "n2", null, null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, sessionId);

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isTrue();
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0))
                .containsEntry(35, "D")
                .containsEntry(500006, "custom-val");
    }

    @Test
    void sendsResolvedFieldsViaPortAndReturnsOnSuccess() {
        FakeFixAdapter fake = new FakeFixAdapter();
        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, true, false));

        SendFIXHandler handler = new SendFIXHandler(fake, new VariableResolver());

        ScenarioNode node = new ScenarioNode("n2", "send", NodeType.SEND_FIX,
                Map.of("msgType", "D", "fields", Map.of("11", "CL-1", "55", "AAPL")),
                null, null, "n3", null, null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, sessionId);

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.nextNodeId()).isEqualTo("n3");
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0))
                .containsEntry(35, "D")
                .containsEntry(11, "CL-1")
                .containsEntry(55, "AAPL");
        assertThat(ctx.getNodeMessage("n2")).isNotNull();
    }
}
