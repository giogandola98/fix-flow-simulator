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
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests session-failure scenarios: when a FIX session goes down (disconnected),
 * in-flight executions waiting for inbound messages should time out and be marked FAILED.
 */
class SessionFailureTest {

    /**
     * When the session disconnects while an execution is waiting for an EXPECT_FIX response,
     * the execution should eventually time out and reach FAILED status.
     */
    @Test
    void sessionDisconnect_executionTimesOutAndFails() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(64, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake, new VariableResolver()),
                new ExpectFIXHandler(correlation),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(
                sessionId, "fail-session", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "CLI", "SRV", "localhost", 9003, 30, true, false));

        assertThat(fake.isConnected(sessionId)).isTrue();

        // Scenario with a short timeout so the test runs fast
        Scenario scenario = buildScenario("SESS-FAIL-REQ", 200);
        registry.register(scenario);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execId = mgr.start(scenario.id(), sessionId);

        // Wait for execution to block on EXPECT_FIX
        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 1);

        // Simulate session going down — no more inbound messages will arrive
        fake.disconnect(sessionId);
        assertThat(fake.isConnected(sessionId)).isFalse();

        // Execution should time out and fail (no response will ever arrive)
        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execId) == ExecutionStatus.FAILED);

        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.FAILED);
        // Correlation waiter is cleaned up
        assertThat(correlation.pendingCount()).isEqualTo(0);
    }

    /**
     * When a session disconnects and reconnects, a new execution on the reconnected session
     * should complete normally once a response arrives.
     */
    @Test
    void sessionReconnect_newExecutionPassesAfterReconnect() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(64, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake, new VariableResolver()),
                new ExpectFIXHandler(correlation),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        FIXSessionConfig cfg = new FIXSessionConfig(
                sessionId, "reconnect-session", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "CLI", "SRV", "localhost", 9004, 30, true, false);
        fake.connect(cfg);

        Scenario scenario = buildScenario("RECONNECT-REQ", 5000);
        registry.register(scenario);

        // First: simulate disconnect
        fake.disconnect(sessionId);
        assertThat(fake.isConnected(sessionId)).isFalse();

        // Reconnect
        fake.connect(cfg);
        assertThat(fake.isConnected(sessionId)).isTrue();

        // Start execution on the re-established session
        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execId = mgr.start(scenario.id(), sessionId);

        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 1);

        // Inject the expected response
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "RECONNECT-REQ"));

        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execId) == ExecutionStatus.PASSED);

        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    /**
     * Multiple parallel executions: when a session disconnects after some pass,
     * the remaining ones time out and fail while the passed ones keep their PASSED status.
     */
    @Test
    void sessionDisconnect_partialFailure_completedExecutionsUnaffected() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(64, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake, new VariableResolver()),
                new ExpectFIXHandler(correlation),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(
                sessionId, "partial-session", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "CLI", "SRV", "localhost", 9005, 30, true, false));

        // One short-timeout scenario and one long-timeout scenario
        Scenario scenPassing = buildScenario("PASS-REQ", 5000);
        Scenario scenFailing = buildScenario("FAIL-REQ", 200);  // will time out
        registry.register(scenPassing);
        registry.register(scenFailing);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execPassing = mgr.start(scenPassing.id(), sessionId);
        UUID execFailing = mgr.start(scenFailing.id(), sessionId);

        // Wait for both to be waiting
        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 2);

        // Only inject response for the one that should pass
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "PASS-REQ"));

        // PASS-REQ execution should complete
        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execPassing) == ExecutionStatus.PASSED);

        assertThat(mgr.getStatus(execPassing)).isEqualTo(ExecutionStatus.PASSED);

        // Simulate session going down (FAIL-REQ never receives its response)
        fake.disconnect(sessionId);

        // FAIL-REQ should time out and fail
        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execFailing) == ExecutionStatus.FAILED);

        assertThat(mgr.getStatus(execFailing)).isEqualTo(ExecutionStatus.FAILED);
        // PASS-REQ status should remain PASSED
        assertThat(mgr.getStatus(execPassing)).isEqualTo(ExecutionStatus.PASSED);
    }

    // --- helper ---
    private Scenario buildScenario(String reqId, long timeoutMs) {
        UUID id = UUID.randomUUID();
        return new Scenario(
            id, "sf-" + reqId, "", "1.0", "sess",
            RuntimePolicy.PARALLEL,
            List.of(),
            List.of(new CorrelationRule(131, "n3", 131, timeoutMs)),
            List.of(
                new ScenarioNode("n1", "start",  NodeType.START,      Map.of(), null, null, "n2", null, null),
                new ScenarioNode("n2", "send",   NodeType.SEND_FIX,
                    Map.of("msgType", "R", "fields", Map.of("131", reqId)),
                    null, null, "n3", null, null),
                new ScenarioNode("n3", "expect", NodeType.EXPECT_FIX,
                    Map.of("correlationTag", 131, "expectedValue", reqId),
                    new TimeoutConfig(timeoutMs, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
                    null, "n4", "nf", null),
                new ScenarioNode("n4", "done",   NodeType.END_PASS,   Map.of(), null, null, null, null, null),
                new ScenarioNode("nf", "fail",   NodeType.END_FAIL,   Map.of(), null, null, null, null, null)
            ),
            List.of(), Map.of()
        );
    }
}
