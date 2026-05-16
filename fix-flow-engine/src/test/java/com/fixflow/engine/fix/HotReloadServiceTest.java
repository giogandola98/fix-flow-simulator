package com.fixflow.engine.fix;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HotReloadServiceTest {

    @Test
    void pausesBufferReloadsRegistryThenResumes() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        MessageBuffer buffer = mock(MessageBuffer.class);
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        UUID scenarioId = UUID.randomUUID();
        Scenario latest = mock(Scenario.class);
        when(repo.findById(scenarioId)).thenReturn(Optional.of(latest));

        HotReloadService svc = new HotReloadService(registry, buffer, repo);
        svc.reload(scenarioId);

        var inOrder = inOrder(buffer, registry);
        inOrder.verify(buffer).pause();
        inOrder.verify(registry).reload(latest);
        inOrder.verify(buffer).resume();
    }

    @Test
    void resumesBufferEvenIfReloadThrows() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        MessageBuffer buffer = mock(MessageBuffer.class);
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        UUID scenarioId = UUID.randomUUID();
        Scenario latest = mock(Scenario.class);
        when(repo.findById(scenarioId)).thenReturn(Optional.of(latest));
        doThrow(new RuntimeException("boom")).when(registry).reload(any());

        HotReloadService svc = new HotReloadService(registry, buffer, repo);
        try { svc.reload(scenarioId); } catch (RuntimeException ignored) {}

        verify(buffer).pause();
        verify(buffer).resume();
    }
}
