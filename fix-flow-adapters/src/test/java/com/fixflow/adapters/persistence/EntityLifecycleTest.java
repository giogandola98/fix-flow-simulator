package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.*;
import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.ExecutionEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the JPA lifecycle callbacks (@PrePersist / @PreUpdate) on the entities. */
@DataJpaTest
class EntityLifecycleTest {

    @Autowired TestEntityManager em;

    @Test
    void scenarioEntitySetsCreatedAndUpdatedOnInsertThenBumpsUpdatedOnUpdate() {
        ScenarioEntity e = new ScenarioEntity();
        e.setId(UUID.randomUUID());
        e.setName("demo");
        e.setVersion("1.0");
        e.setYamlDsl("name: demo");

        em.persistAndFlush(e);
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
        var created = e.getCreatedAt();
        var firstUpdated = e.getUpdatedAt();

        e.setName("demo2");
        em.flush();
        assertThat(e.getCreatedAt()).isEqualTo(created);
        assertThat(e.getUpdatedAt()).isAfterOrEqualTo(firstUpdated);
    }

    @Test
    void executionEventEntityDefaultsTimestampOnInsert() {
        ExecutionEventEntity e = new ExecutionEventEntity();
        e.setId(UUID.randomUUID());
        e.setExecutionId(UUID.randomUUID());
        e.setType(ExecutionEventType.NODE_ENTERED);
        // timestamp intentionally left null

        em.persistAndFlush(e);
        assertThat(e.getTimestamp()).isNotNull();
    }

    @Test
    void fixMessageEntityDefaultsReceivedAtOnInsert() {
        FIXMessageEntity e = new FIXMessageEntity();
        e.setId(UUID.randomUUID());
        e.setExecutionId(UUID.randomUUID());
        e.setDirection(Direction.INBOUND);
        // receivedAt intentionally left null

        em.persistAndFlush(e);
        assertThat(e.getReceivedAt()).isNotNull();
    }

    @Test
    void scenarioVersionEntitySetsSavedAtAndGeneratesId() {
        ScenarioVersionEntity v = new ScenarioVersionEntity();
        v.setScenarioId(UUID.randomUUID());
        v.setVersion("1.0");
        v.setYamlDsl("name: demo");

        em.persistAndFlush(v);
        assertThat(v.getId()).isNotNull();
        assertThat(v.getSavedAt()).isNotNull();
    }
}
