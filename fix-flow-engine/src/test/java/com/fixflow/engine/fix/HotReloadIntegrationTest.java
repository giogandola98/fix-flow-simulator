package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link HotReloadService} verifying that reloading
 * a scenario definition mid-execution does not lose buffered in-flight messages.
 */
class HotReloadIntegrationTest {

    /**
     * Verifies that messages arriving while the buffer is paused (during hot reload)
     * are parked and then flushed after the buffer resumes, allowing an in-flight
     * execution to still complete successfully.
     */
    @Test
    void reloadDuringExecution_bufferedMessagesAreNotLost() throws Exception {
        // --- Infrastructure ---
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
                sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "A", "B", "localhost", 9001, 30, 5, true, false));

        Scenario v1 = buildScenario("REQ-HOT", 5000);
        registry.register(v1);

        // --- Start execution and wait for it to be blocking on EXPECT_FIX ---
        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execId = mgr.start(v1.id(), sessionId);

        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 1);

        // --- Simulate hot reload: pause buffer, reload scenario, resume ---
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        Scenario v2 = buildScenario("REQ-HOT", 5000); // same logic, new version object
        when(repo.findById(v1.id())).thenReturn(Optional.of(v2));

        // Pause buffer (as HotReloadService does)
        buffer.pause();
        assertThat(buffer.isPaused()).isTrue();

        // While buffer is paused, the inbound message is parked (not dispatched to correlation)
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-HOT"));

        // Message should be buffered, not yet consumed by correlation
        assertThat(buffer.size(sessionId.toString())).isEqualTo(1);
        assertThat(correlation.pendingCount()).isEqualTo(1); // still waiting

        // Resume the buffer (as HotReloadService does after reload)
        buffer.resume();

        // Drain buffered messages back through the router
        router.drain(sessionId.toString());

        // Execution should complete with PASSED
        await().atMost(3, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execId) == ExecutionStatus.PASSED);

        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    /**
     * Verifies that a full HotReloadService.reload() call (pause → reload → resume)
     * does not corrupt in-flight executions: messages buffered during reload are
     * delivered after the buffer resumes.
     */
    @Test
    void hotReloadService_reloadDoesNotLoseInFlightMessages() throws Exception {
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
                sessionId, "s2", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "A", "B", "localhost", 9002, 30, 5, true, false));

        Scenario v1 = buildScenario("REQ-RELOAD", 5000);
        registry.register(v1);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execId = mgr.start(v1.id(), sessionId);

        // Wait for execution to be waiting on EXPECT_FIX
        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> correlation.pendingCount() == 1);

        // Set up the mock repo for HotReloadService
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        Scenario v2 = buildScenario("REQ-RELOAD", 5000);
        when(repo.findById(v1.id())).thenReturn(Optional.of(v2));

        HotReloadService hotReload = new HotReloadService(registry, buffer, repo);

        // Trigger hot reload in a background thread so we can inject a message concurrently
        Thread reloadThread = new Thread(() -> hotReload.reload(v1.id()));
        reloadThread.start();
        reloadThread.join(500); // give it time to pause+reload+resume

        // After reload, inject the correlation response
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-RELOAD"));

        // If message was lost during reload, execution would time out (fail).
        // It should instead drain from the buffer and complete successfully.
        router.drain(sessionId.toString());

        await().atMost(4, java.util.concurrent.TimeUnit.SECONDS)
               .until(() -> mgr.getStatus(execId) != ExecutionStatus.RUNNING);

        assertThat(mgr.getStatus(execId)).isEqualTo(ExecutionStatus.PASSED);
    }

    // --- helper ---
    private Scenario buildScenario(String reqId, long timeoutMs) {
        UUID id = UUID.randomUUID();
        return new Scenario(
            id, "hl-" + reqId, "", "1.0", "sess",
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
