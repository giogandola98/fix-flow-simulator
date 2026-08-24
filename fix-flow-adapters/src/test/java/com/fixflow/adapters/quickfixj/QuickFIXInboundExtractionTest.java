package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;
import quickfix.Group;
import quickfix.Message;

import static org.junit.jupiter.api.Assertions.*;

class QuickFIXInboundExtractionTest {

    private Group leg(String symbol, String side) {
        Group g = new Group(555, 600);
        g.setString(600, symbol);
        g.setString(624, side);
        return g;
    }

    @Test
    void topLevelFieldsAreExtracted() {
        Message msg = new Message();
        msg.getHeader().setString(35, "D");
        msg.setString(11, "ORD-1");
        msg.setString(55, "EUR/USD");

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertEquals("D", data.flatFields().get(35));
        assertEquals("ORD-1", data.flatFields().get(11));
        assertFalse(data.hasGroups());
    }

    @Test
    void repeatingGroupEntriesAreExtractedInOrder() {
        Message msg = new Message();
        msg.getHeader().setString(35, "AB");
        msg.setString(11, "ORD-1");
        msg.addGroup(leg("EUR/USD", "1"));
        msg.addGroup(leg("EUR/USD", "2"));

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertEquals(2, data.group(555).size());
        assertEquals("1", data.groupValue(555, 0, 624).orElseThrow());
        assertEquals("2", data.groupValue(555, 1, 624).orElseThrow());
        assertEquals("EUR/USD", data.groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void counterTagIsNotLeakedIntoTopLevelFields() {
        Message msg = new Message();
        msg.getHeader().setString(35, "AB");
        msg.addGroup(leg("EUR/USD", "1"));

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertNull(data.flatFields().get(555),
                "the NoLegs counter is structure, not a field the scenario should match on");
    }

    @Test
    void nestedGroupsAreExtracted() {
        Group event = new Group(864, 865);
        event.setString(865, "13");
        event.setString(866, "20260826");
        Group legWithEvent = leg("EUR/USD", "1");
        legWithEvent.addGroup(event);

        Message msg = new Message();
        msg.getHeader().setString(35, "AB");
        msg.addGroup(legWithEvent);

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertEquals("13", data.group(555).get(0).groupValue(864, 0, 865).orElseThrow());
    }

    @Test
    void roundTripsThroughTheOutboundBuilder() {
        FIXMessageData original = new FIXMessageData(
                java.util.Map.of(35, "AB"),
                java.util.Map.of(555, java.util.List.of(
                        FIXMessageData.ofFields(new java.util.LinkedHashMap<>(java.util.Map.of(600, "EUR/USD"))))));

        FIXMessageData back = QuickFIXApplicationAdapter.extractMessage(
                QuickFIXAdapter.buildMessage(original));

        assertEquals("EUR/USD", back.groupValue(555, 0, 600).orElseThrow());
    }
}
