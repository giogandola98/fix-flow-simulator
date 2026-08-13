package com.fixflow.api;

import com.fixflow.api.rest.ExecutionController;
import com.fixflow.api.rest.ScenarioController;
import com.fixflow.api.rest.SessionController;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full application context loads with all real beans wired (JPA/H2 in-memory).
 * Browser auto-open is disabled via test application.yml.
 */
@SpringBootTest
class FixFlowApplicationTest {

    // Mock the STOMP publisher so no live WebSocket broker is required.
    @MockBean
    private EventPublisherPort eventPublisherPort;

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextLoads() {
        assertThat(ctx).isNotNull();
    }

    @Test
    void coreBeansArePresent() {
        assertThat(ctx.getBean(ExecutionController.class)).isNotNull();
        assertThat(ctx.getBean(ScenarioController.class)).isNotNull();
        assertThat(ctx.getBean(SessionController.class)).isNotNull();
        // StompEventPublisher is the concrete EventPublisherPort implementation; it is
        // replaced by a mock here but the bean type must still be resolvable.
        assertThat(ctx.getBeansOfType(EventPublisherPort.class)).isNotEmpty();
    }
}
