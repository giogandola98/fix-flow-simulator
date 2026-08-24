package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;
import quickfix.DataDictionary;
import quickfix.Group;
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

    /**
     * Regression for the multileg corruption: without a dictionary, {@code Group} serialises a
     * non-delimiter fields ascending by tag, not in FIX50SP2's {@code NoLegs} dictionary order
     * (600, 608, 609, 624, 556, 687, 587, 588). QuickFIX/J's receiving parser — with
     * {@code checkUnorderedGroupFields} on, the default — ends a group entry at the first field
     * whose group-order index doesn't increase, so the wrong-order wire form silently collapses
     * two legs into one and drops three of the eight fields on the survivor. This test builds the
     * message the way {@code sendMessage} now does (with the FIX50SP2 application dictionary
     * threaded through), serialises it, and parses the resulting bytes back with that same
     * dictionary exactly as a real counterparty would — the only way to catch a wire-order
     * defect, since inspecting the in-memory {@code Group} never round-trips through a parser.
     */
    @Test
    void noLegsGroupSurvivesDictionaryOrderedRoundTrip() throws Exception {
        DataDictionary sessionDictionary = new DataDictionary("FIXT11.xml");
        DataDictionary appDictionary = new DataDictionary("FIX50SP2.xml");

        Map<Integer, String> nearLeg = ordered(
                600, "EUR/USD", 608, "IFXXXP", 609, "FXSPOT", 624, "1",
                556, "EUR", 687, "1000000", 587, "0", 588, "20260826-14:19:23");
        Map<Integer, String> farLeg = ordered(
                600, "EUR/USD", 608, "JFTXFP", 609, "FXFWD", 624, "2",
                556, "EUR", 687, "1000000", 587, "6", 588, "20261124-14:19:23");
        FIXMessageData data = new FIXMessageData(
                ordered(35, "AB", 11, "ORD-1", 54, "1", 60, "20260826-14:19:23", 40, "1", 38, "2000000"),
                Map.of(555, List.of(FIXMessageData.ofFields(nearLeg), FIXMessageData.ofFields(farLeg))));

        Message msg = QuickFIXAdapter.buildMessage(data, appDictionary);
        // Session-managed header fields (8, 34, 49, 52, 56, 1128) are normally added by
        // QuickFIX/J's Session layer on send; supply them here so the standalone message parses.
        msg.getHeader().setString(8, "FIXT.1.1");
        msg.getHeader().setString(49, "PRIME");
        msg.getHeader().setString(56, "COUNTER");
        msg.getHeader().setInt(34, 1);
        msg.getHeader().setString(52, "20260826-14:19:23.000");
        msg.getHeader().setString(1128, "9");

        String wire = msg.toString();

        Message parsed = new Message();
        parsed.fromString(wire, sessionDictionary, appDictionary, true);

        assertEquals(2, parsed.getInt(555), "NoLegs counter must survive the round trip: " + wire.replace((char) 1, '|'));
        assertEquals(2, parsed.getGroupCount(555), "both leg entries must survive the round trip");

        Group leg1 = new Group(555, 600);
        parsed.getGroup(1, leg1);
        assertLegFields(nearLeg, leg1);

        Group leg2 = new Group(555, 600);
        parsed.getGroup(2, leg2);
        assertLegFields(farLeg, leg2);
    }

    private void assertLegFields(Map<Integer, String> expected, Group actual) throws Exception {
        for (Map.Entry<Integer, String> e : expected.entrySet()) {
            assertEquals(e.getValue(), actual.getString(e.getKey()), "tag " + e.getKey() + " must survive the round trip");
        }
    }

    /**
     * Pins the no-dictionary fallback: {@code buildMessage(data)}, the single-argument overload
     * unit tests call with no live session, must keep using the entry's first field as the
     * delimiter and ascending-tag serialisation, exactly as before this change.
     */
    @Test
    void noDictionaryFallsBackToFirstFieldDelimiterAndAscendingTagOrder() throws Exception {
        FIXMessageData near = FIXMessageData.ofFields(ordered(600, "EUR/USD", 624, "1", 587, "0"));
        FIXMessageData far  = FIXMessageData.ofFields(ordered(600, "EUR/USD", 624, "2", 587, "6"));
        FIXMessageData data = new FIXMessageData(ordered(35, "AB", 11, "ORD-1"), Map.of(555, List.of(near, far)));

        Message msg = QuickFIXAdapter.buildMessage(data);
        String raw = msg.toString().replace((char) 1, '|');

        assertEquals(2, msg.getGroupCount(555));
        assertTrue(raw.contains("600=EUR/USD|587=0|624=1|"), raw);
        assertTrue(raw.contains("600=EUR/USD|587=6|624=2|"), raw);
    }
}
