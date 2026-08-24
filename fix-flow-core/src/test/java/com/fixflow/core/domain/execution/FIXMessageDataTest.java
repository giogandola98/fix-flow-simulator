package com.fixflow.core.domain.execution;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FIXMessageDataTest {

    @Test
    void ofFieldsCreatesMessageWithNoGroups() {
        FIXMessageData m = FIXMessageData.ofFields(Map.of(35, "D", 11, "ORD-1"));
        assertEquals("D", m.flatFields().get(35));
        assertTrue(m.groups().isEmpty());
        assertFalse(m.hasGroups());
    }

    @Test
    void nullArgumentsBecomeEmptyMaps() {
        FIXMessageData m = new FIXMessageData(null, null);
        assertTrue(m.fields().isEmpty());
        assertTrue(m.groups().isEmpty());
    }

    @Test
    void fieldOrderIsPreserved() {
        Map<Integer, String> in = new LinkedHashMap<>();
        in.put(600, "EUR/USD");
        in.put(624, "1");
        in.put(587, "0");
        FIXMessageData m = FIXMessageData.ofFields(in);
        assertIterableEquals(List.of(600, 624, 587), m.fields().keySet());
    }

    @Test
    void groupsAreAccessibleByCounterTagAndIndex() {
        FIXMessageData near = FIXMessageData.ofFields(new LinkedHashMap<>(Map.of(600, "EUR/USD")));
        FIXMessageData far  = FIXMessageData.ofFields(new LinkedHashMap<>(Map.of(600, "EUR/GBP")));
        FIXMessageData m = new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(near, far)));

        assertEquals(2, m.group(555).size());
        assertTrue(m.hasGroups());
        assertEquals("EUR/USD", m.groupValue(555, 0, 600).orElseThrow());
        assertEquals("EUR/GBP", m.groupValue(555, 1, 600).orElseThrow());
        assertTrue(m.groupValue(555, 2, 600).isEmpty());
        assertTrue(m.groupValue(999, 0, 600).isEmpty());
        assertTrue(m.groupValue(555, 0, 9999).isEmpty());
    }

    @Test
    void nestedGroupsAreSupported() {
        FIXMessageData inner = FIXMessageData.ofFields(Map.of(865, "1"));
        FIXMessageData leg = new FIXMessageData(Map.of(600, "EUR/USD"), Map.of(864, List.of(inner)));
        FIXMessageData m = new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(leg)));
        assertEquals("1", m.group(555).get(0).groupValue(864, 0, 865).orElseThrow());
    }

    @Test
    void defensiveCopyPreventsExternalMutation() {
        Map<Integer, String> mutable = new LinkedHashMap<>();
        mutable.put(35, "D");
        FIXMessageData m = FIXMessageData.ofFields(mutable);
        mutable.put(11, "LATE");
        assertNull(m.fields().get(11));
        assertThrows(UnsupportedOperationException.class, () -> m.fields().put(99, "x"));
    }
}
