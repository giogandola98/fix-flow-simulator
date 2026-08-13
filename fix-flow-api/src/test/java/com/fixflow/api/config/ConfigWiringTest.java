package com.fixflow.api.config;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the @PostConstruct wiring components. They live in the same package
 * so the package-private lifecycle methods can be invoked directly without booting Spring.
 */
class ConfigWiringTest {

    private Scenario scenario(String name) {
        return new Scenario(UUID.randomUUID(), name, null, "1", null,
            null, null, null, null, null, null, null);
    }

    @Test
    void scenarioRegistryInitializerRegistersAllFromRepo() {
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        Scenario a = scenario("a");
        Scenario b = scenario("b");
        when(repo.findAll()).thenReturn(List.of(a, b));

        new ScenarioRegistryInitializer(repo, registry).populate();

        verify(registry).register(a);
        verify(registry).register(b);
    }

    @Test
    void scenarioRegistryInitializerWithEmptyRepoRegistersNothing() {
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        when(repo.findAll()).thenReturn(List.of());

        new ScenarioRegistryInitializer(repo, registry).populate();

        verifyNoInteractions(registry);
    }

    @Test
    void inboundWiringSetsRouterAsInboundListener() {
        FIXSessionPort port = mock(FIXSessionPort.class);
        MessageRouter router = mock(MessageRouter.class);

        new InboundWiring(port, router).wire();

        verify(port).setInboundListener(router);
    }
}
