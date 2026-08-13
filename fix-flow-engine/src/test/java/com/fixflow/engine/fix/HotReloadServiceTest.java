package com.fixflow.engine.fix;

import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.fixflow.engine.support.Fixtures.endPass;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class HotReloadServiceTest {

    private ScenarioRegistry registry;
    private MessageBuffer buffer;
    private ScenarioRepositoryPort repo;
    private HotReloadService service;

    @BeforeEach
    void setUp() {
        registry = new ScenarioRegistry();
        buffer = new MessageBuffer();
        repo = Mockito.mock(ScenarioRepositoryPort.class);
        service = new HotReloadService(registry, buffer, repo);
    }

    @Test
    void reloadPausesBufferFetchesLatestRegistersAndResumes() {
        UUID id = UUID.randomUUID();
        Scenario latest = scenario(id, "s", java.util.List.of(), start("end"), endPass("end"));
        AtomicBoolean pausedDuringFetch = new AtomicBoolean();
        when(repo.findById(id)).thenAnswer(inv -> {
            pausedDuringFetch.set(buffer.isPaused());
            return Optional.of(latest);
        });

        service.reload(id);

        assertThat(pausedDuringFetch).isTrue();               // paused while reloading
        assertThat(buffer.isPaused()).isFalse();              // resumed afterwards
        assertThat(registry.getById(id)).contains(latest);    // registry updated
    }

    @Test
    void reloadUnknownScenarioThrowsAndStillResumesBuffer() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reload(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.isPaused()).isFalse(); // finally-block resumed
    }
}
