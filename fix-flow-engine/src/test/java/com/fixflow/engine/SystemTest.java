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
import com.fixflow.engine.variable.VariableResolver;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * System test: 3 parallel scenario executions on a single FIX session.
 * Inbound FIX responses are injected in reverse order (C, B, A) to verify
 * that the correlation engine correctly routes each message to the right execution.
 */
class SystemTest {

    @Test
    void threeParallelExecutions_outOfOrderMessages_allPass() {
        // --- Infrastructure wiring ---
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(128, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake, new VariableResolver()),
                new ExpectFIXHandler(correlation, router),
                new EndHandler(),
                new EndFailHandler()
        ));

        // --- Session setup ---
        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(
                sessionId, "sys-session", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "SYS", "TARGET", "localhost", 9001, 30, true, false));

        // --- Build 3 scenarios, each correlating on tag 131 (QuoteReqID) ---
        Scenario scenA = buildScenario("REQ-A");
        Scenario scenB = buildScenario("REQ-B");
        Scenario scenC = buildScenario("REQ-C");

        registry.register(scenA);
        registry.register(scenB);
        registry.register(scenC);

        // --- Start all 3 executions ---
        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execA = mgr.start(scenA.id(), sessionId);
        UUID execB = mgr.start(scenB.id(), sessionId);
        UUID execC = mgr.start(scenC.id(), sessionId);

        // --- Wait until all 3 EXPECT_FIX nodes are registered with the correlation engine ---
        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 3);

        // --- Inject responses OUT OF ORDER: C, then A, then B ---
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-C"));
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-A"));
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-B"));

        // --- Assert all three executions reach PASSED ---
        await().atMost(5, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execA) == ExecutionStatus.PASSED
                         && mgr.getStatus(execB) == ExecutionStatus.PASSED
                         && mgr.getStatus(execC) == ExecutionStatus.PASSED);

        assertThat(mgr.getStatus(execA)).isEqualTo(ExecutionStatus.PASSED);
        assertThat(mgr.getStatus(execB)).isEqualTo(ExecutionStatus.PASSED);
        assertThat(mgr.getStatus(execC)).isEqualTo(ExecutionStatus.PASSED);

        // Each scenario sends exactly one message → 3 total outbound messages
        assertThat(fake.getSentMessages()).hasSize(3);

        // Verify each expected request ID was sent
        assertThat(fake.getSentMessages()).anyMatch(m -> "REQ-A".equals(m.get(131)));
        assertThat(fake.getSentMessages()).anyMatch(m -> "REQ-B".equals(m.get(131)));
        assertThat(fake.getSentMessages()).anyMatch(m -> "REQ-C".equals(m.get(131)));
    }

    @Test
    void threeParallelExecutions_firstResponseArrivesBefireOthers_onlyMatchingPasses() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(128, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake, new VariableResolver()),
                new ExpectFIXHandler(correlation, router),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(
                sessionId, "s2", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "SYS", "TGT", "localhost", 9002, 30, true, false));

        // Use very short timeouts so the "missing" responses time out fast
        Scenario scenX = buildScenarioWithTimeout("REQ-X", 200);
        Scenario scenY = buildScenarioWithTimeout("REQ-Y", 200);
        Scenario scenZ = buildScenarioWithTimeout("REQ-Z", 5000); // long timeout — will pass

        registry.register(scenX);
        registry.register(scenY);
        registry.register(scenZ);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execX = mgr.start(scenX.id(), sessionId);
        UUID execY = mgr.start(scenY.id(), sessionId);
        UUID execZ = mgr.start(scenZ.id(), sessionId);

        // Wait for all 3 to be waiting
        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 3);

        // Only inject response for Z
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-Z"));

        // Z should PASS; X and Y should FAIL (timeout)
        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execZ) == ExecutionStatus.PASSED
                         && mgr.getStatus(execX) != ExecutionStatus.RUNNING
                         && mgr.getStatus(execY) != ExecutionStatus.RUNNING);

        assertThat(mgr.getStatus(execZ)).isEqualTo(ExecutionStatus.PASSED);
        assertThat(mgr.getStatus(execX)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(mgr.getStatus(execY)).isEqualTo(ExecutionStatus.FAILED);
    }

    // --- helpers ---

    private Scenario buildScenario(String reqId) {
        return buildScenarioWithTimeout(reqId, 5000);
    }

    private Scenario buildScenarioWithTimeout(String reqId, long timeoutMs) {
        UUID id = UUID.randomUUID();
        return new Scenario(
            id, "sys-" + reqId, "", "1.0", "sess",
            RuntimePolicy.PARALLEL,
            List.of(),
            List.of(new CorrelationRule(131, "n3", 131, timeoutMs)),
            List.of(
                new ScenarioNode("n1", "start", NodeType.START,
                    Map.of(), null, null, "n2", null, null),
                new ScenarioNode("n2", "send", NodeType.SEND_FIX,
                    Map.of("msgType", "R", "fields", Map.of("131", reqId)),
                    null, null, "n3", null, null),
                new ScenarioNode("n3", "expect", NodeType.EXPECT_FIX,
                    Map.of("correlationTag", 131, "expectedValue", reqId),
                    new TimeoutConfig(timeoutMs, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
                    null, "n4", "nf", null),
                new ScenarioNode("n4", "done", NodeType.END_PASS,
                    Map.of(), null, null, null, null, null),
                new ScenarioNode("nf", "fail", NodeType.END_FAIL,
                    Map.of(), null, null, null, null, null)
            ),
            List.of(),
            Map.of(), null
        );
    }
}
