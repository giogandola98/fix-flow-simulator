package com.fixflow.engine;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.fix.FakeFixAdapter;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultiScenarioIntegrationTest {

    @Test
    void twoScenariosOnSameSessionResolveCorrelatedMessages() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(64, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake),
                new ExpectFIXHandler(correlation),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, 5, true, false));

        Scenario a = scenario("REQ-A");
        Scenario b = scenario("REQ-B");
        registry.register(a);
        registry.register(b);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execA = mgr.start(a.id(), sessionId);
        UUID execB = mgr.start(b.id(), sessionId);

        // Wait for both executions to register their correlation waiters.
        await().atMost(1, java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> correlation.pendingCount() == 2);

        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-B"));
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-A"));

        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> mgr.getStatus(execA) == ExecutionStatus.PASSED
                          && mgr.getStatus(execB) == ExecutionStatus.PASSED);

        assertThat(fake.getSentMessages()).hasSize(2);
    }

    private Scenario scenario(String reqId) {
        UUID id = UUID.randomUUID();
        return new Scenario(id, "demo-" + reqId, "", "1.0", "sess",
                RuntimePolicy.PARALLEL,
                List.of(),  // routingRules
                List.of(new CorrelationRule(131, "n3", 131, 5000)),  // correlationRules
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,
                                Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "send", NodeType.SEND_FIX,
                                Map.of("msgType", "D", "fields", Map.of("11", reqId, "131", reqId)),
                                null, null, "n3", null, null),
                        new ScenarioNode("n3", "expect", NodeType.EXPECT_FIX,
                                Map.of("correlationTag", 131, "expectedValue", reqId),
                                new TimeoutConfig(3, TimeUnit.SECONDS, TimeoutAction.FAIL, null),
                                null, "n4", "nf", null),
                        new ScenarioNode("n4", "done", NodeType.END_PASS,
                                Map.of(), null, null, null, null, null),
                        new ScenarioNode("nf", "fail", NodeType.END_FAIL,
                                Map.of(), null, null, null, null, null)
                ),
                List.of(),  // edges
                Map.of());  // variables
    }
}
