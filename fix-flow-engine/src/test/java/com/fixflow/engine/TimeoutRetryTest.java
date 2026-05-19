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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TimeoutRetryTest {

    @Test
    void timeoutFailMarksExecutionFailed() {
        FakeFixAdapter fake = new FakeFixAdapter();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(32, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(), new ExpectFIXHandler(correlation, router),
                new EndHandler(), new EndFailHandler()));

        UUID sessionId = UUID.randomUUID();
        ScenarioRegistry reg = new ScenarioRegistry();
        Scenario s = scenarioExpectOnly(TimeoutAction.FAIL);
        reg.register(s);

        ExecutionManager mgr = new ExecutionManager(reg, dispatcher);
        UUID exec = mgr.start(s.id(), sessionId);

        await().atMost(2, TimeUnit.SECONDS).until(() -> mgr.getStatus(exec) == ExecutionStatus.FAILED);
        assertThat(mgr.getStatus(exec)).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void timeoutContinueSkipsToOnSuccessAndPasses() {
        FakeFixAdapter fake = new FakeFixAdapter();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(32, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(), new ExpectFIXHandler(correlation, router),
                new EndHandler(), new EndFailHandler()));

        ScenarioRegistry reg = new ScenarioRegistry();
        Scenario s = scenarioExpectOnly(TimeoutAction.CONTINUE);
        reg.register(s);

        ExecutionManager mgr = new ExecutionManager(reg, dispatcher);
        UUID exec = mgr.start(s.id(), UUID.randomUUID());

        await().atMost(2, TimeUnit.SECONDS).until(() -> mgr.getStatus(exec) == ExecutionStatus.PASSED);
    }

    private Scenario scenarioExpectOnly(TimeoutAction action) {
        UUID id = UUID.randomUUID();
        return new Scenario(id, "to-" + action, "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n2", 131, 100)),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,
                                Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "expect", NodeType.EXPECT_FIX,
                                Map.of("correlationTag", 131, "expectedValue", "NEVER-COMES"),
                                new TimeoutConfig(100, com.fixflow.core.domain.scenario.TimeUnit.MILLISECONDS,
                                        action, null),
                                new RetryPolicy(2, 10),
                                "n3", "nf", null),
                        new ScenarioNode("n3", "done", NodeType.END_PASS,
                                Map.of(), null, null, null, null, null),
                        new ScenarioNode("nf", "fail", NodeType.END_FAIL,
                                Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of());
    }
}
