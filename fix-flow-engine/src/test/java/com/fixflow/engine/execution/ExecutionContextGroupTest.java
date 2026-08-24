package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextGroupTest {

    private ExecutionContext ctx() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref",
                null, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void storingFlatMapStillReadableAsFlatMap() {
        ExecutionContext c = ctx();
        c.storeNodeMessage("n1", Map.of(35, "D", 11, "ORD-1"));
        assertEquals("ORD-1", c.getNodeMessage("n1").get(11));
        assertTrue(c.getNodeMessageData("n1").groups().isEmpty());
    }

    @Test
    void storingMessageDataExposesBothViews() {
        ExecutionContext c = ctx();
        FIXMessageData leg = FIXMessageData.ofFields(Map.of(600, "EUR/USD"));
        c.storeNodeMessage("n1", new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(leg))));

        assertEquals("AB", c.getNodeMessage("n1").get(35));
        assertEquals("EUR/USD", c.getNodeMessageData("n1").groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void unknownNodeReturnsNullForBothViews() {
        ExecutionContext c = ctx();
        assertNull(c.getNodeMessage("nope"));
        assertNull(c.getNodeMessageData("nope"));
    }
}
