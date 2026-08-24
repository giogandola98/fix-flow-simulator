package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;
import quickfix.Message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuickFIXMessageBuilderTest {

    private Map<Integer, String> ordered(Object... pairs) {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((Integer) pairs[i], (String) pairs[i + 1]);
        return m;
    }

    @Test
    void msgTypeGoesToTheHeader() throws Exception {
        Message msg = QuickFIXAdapter.buildMessage(
                FIXMessageData.ofFields(ordered(35, "D", 11, "ORD-1")));
        assertEquals("D", msg.getHeader().getString(35));
        assertEquals("ORD-1", msg.getString(11));
    }

    @Test
    void twoLegGroupSerialisesWithCounterAndBothEntries() throws Exception {
        FIXMessageData near = FIXMessageData.ofFields(ordered(600, "EUR/USD", 624, "1", 587, "0"));
        FIXMessageData far  = FIXMessageData.ofFields(ordered(600, "EUR/USD", 624, "2", 587, "6"));
        FIXMessageData data = new FIXMessageData(ordered(35, "AB", 11, "ORD-1"), Map.of(555, List.of(near, far)));

        Message msg = QuickFIXAdapter.buildMessage(data);
        String raw = msg.toString().replace('\u0001', '|');

        assertEquals(2, msg.getGroupCount(555), "NoLegs counter must be maintained by addGroup");
        assertTrue(raw.contains("555=2|"), raw);
        assertTrue(raw.contains("600=EUR/USD|587=0|624=1|"), raw);
        assertTrue(raw.contains("600=EUR/USD|587=6|624=2|"), raw);
    }

    @Test
    void nestedGroupIsSerialisedInsideItsParentEntry() throws Exception {
        FIXMessageData event = FIXMessageData.ofFields(ordered(865, "13", 866, "20260826"));
        FIXMessageData leg = new FIXMessageData(ordered(600, "EUR/USD"), Map.of(864, List.of(event)));
        FIXMessageData data = new FIXMessageData(ordered(35, "AB"), Map.of(555, List.of(leg)));

        String raw = QuickFIXAdapter.buildMessage(data).toString().replace('\u0001', '|');

        assertTrue(raw.contains("864=1|"), raw);
        assertTrue(raw.contains("865=13|"), raw);
    }

    @Test
    void messageWithNoGroupsIsUnchangedFromLegacyBehaviour() throws Exception {
        Message msg = QuickFIXAdapter.buildMessage(
                FIXMessageData.ofFields(ordered(35, "8", 150, "0", 39, "0")));
        assertEquals("0", msg.getString(150));
        assertEquals(0, msg.getGroupCount(555));
    }
}
