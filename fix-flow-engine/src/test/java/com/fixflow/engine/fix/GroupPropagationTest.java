package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GroupPropagationTest {

    private FIXMessageData multileg() {
        FIXMessageData near = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "1"));
        FIXMessageData far  = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "2"));
        return new FIXMessageData(Map.of(35, "AB", 11, "ORD-1"), Map.of(555, List.of(near, far)));
    }

    @Test
    void correlationDeliversGroupsToTheWaiter() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CompletableFuture<FIXMessageData> future =
                engine.register("exec-1", "sess-1", new CorrelationRule(11, "n", 11, 0), "ORD-1");

        assertTrue(engine.onMessage("sess-1", multileg()));

        FIXMessageData received = future.get(1, TimeUnit.SECONDS);
        assertEquals("2", received.groupValue(555, 1, 624).orElseThrow());
    }

    @Test
    void multiRouteMatchesOnFlatFieldsAndDeliversGroups() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CompletableFuture<CorrelationEngine.RoutedResult> future = engine.registerMulti(
                "exec-2", "sess-1",
                List.of(new CorrelationEngine.RoutingRule("r1", "Multileg", Map.of(35, "AB"), "handle-ab")));

        assertTrue(engine.onMessage("sess-1", multileg()));

        CorrelationEngine.RoutedResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals("handle-ab", result.targetNodeId());
        assertEquals("EUR/USD", result.message().groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void bufferParksAndReplaysGroupsIntact() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("sess-1", multileg());

        var polled = buffer.poll("sess-1", m -> "AB".equals(m.flatFields().get(35)));

        assertTrue(polled.isPresent());
        assertEquals("1", polled.get().groupValue(555, 0, 624).orElseThrow());
    }

    @Test
    void routerParksUnmatchedMessagesThenDrainsThem() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer();
        MessageRouter router = new MessageRouter(engine, buffer);

        router.onMessage("sess-1", multileg());          // nobody waiting yet
        assertEquals(1, buffer.size("sess-1"));

        CompletableFuture<FIXMessageData> future =
                engine.register("exec-3", "sess-1", new CorrelationRule(11, "n", 11, 0), "ORD-1");
        router.drain("sess-1");

        assertEquals("EUR/USD", future.get(1, TimeUnit.SECONDS).groupValue(555, 0, 600).orElseThrow());
        assertEquals(0, buffer.size("sess-1"));
    }
}
