package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.fix.FakeFixAdapter;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.variable.VariableResolver;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExecutionManagerTest {

    @Test
    void startToEndPassFlow() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake, new VariableResolver()),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID scenarioId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, true, false));

        Scenario scenario = new Scenario(scenarioId, "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "send",  NodeType.SEND_FIX,
                                Map.of("msgType", "D", "fields", Map.of("11", "REQ-1", "55", "AAPL")),
                                null, null, "n3", null, null),
                        new ScenarioNode("n3", "done", NodeType.END_PASS, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of(), null);

        registry.register(scenario);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID executionId = mgr.start(scenarioId, sessionId);

        await().atMost(2, TimeUnit.SECONDS).until(() -> mgr.getStatus(executionId) == ExecutionStatus.PASSED);
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0)).containsEntry(11, "REQ-1");
    }
}
