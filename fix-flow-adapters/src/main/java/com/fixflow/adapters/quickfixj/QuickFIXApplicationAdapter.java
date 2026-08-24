package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import quickfix.*;
import quickfix.Message;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuickFIXApplicationAdapter implements Application {

    private volatile InboundMessageListener listener;
    private final EventPublisherPort publisher;
    private final Map<SessionID, UUID> sessionUUIDs = new ConcurrentHashMap<>();

    public QuickFIXApplicationAdapter(InboundMessageListener listener, EventPublisherPort publisher) {
        this.listener = listener;
        this.publisher = publisher;
    }

    public void setInboundListener(InboundMessageListener listener) {
        this.listener = listener;
    }

    public void registerSession(SessionID sid, UUID uuid) {
        sessionUUIDs.put(sid, uuid);
    }

    public void unregisterSession(SessionID sid) {
        sessionUUIDs.remove(sid);
    }

    @Override
    public void onCreate(SessionID sessionId) { /* no-op */ }

    @Override
    public void onLogon(SessionID sessionId) {
        UUID uuid = sessionUUIDs.get(sessionId);
        if (uuid != null) publisher.publishSessionStatus(uuid, "UP");
    }

    @Override
    public void onLogout(SessionID sessionId) {
        UUID uuid = sessionUUIDs.get(sessionId);
        if (uuid != null) publisher.publishSessionStatus(uuid, "DOWN");
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void toApp(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void fromApp(Message message, SessionID sessionId) {
        FIXMessageData data = extractMessage(message);
        UUID uuid = sessionUUIDs.get(sessionId);
        String sessionKey = uuid != null ? uuid.toString() : sessionId.toString();
        listener.onMessage(sessionKey, data);
    }

    /**
     * Converts a QuickFIX/J message into engine data. Header and trailer fields join the
     * top-level map; every repeating group becomes an ordered list of entries, recursively.
     * Group counter tags are deliberately left out of the flat map — they describe structure,
     * not content, and a scenario matching on them would be matching on an implementation detail.
     */
    static FIXMessageData extractMessage(Message message) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        Map<Integer, List<FIXMessageData>> groups = new LinkedHashMap<>();

        copyFields(message.getHeader().iterator(), fields);
        collect(message, fields, groups);
        copyFields(message.getTrailer().iterator(), fields);

        return new FIXMessageData(fields, groups);
    }

    private static void collect(FieldMap map,
                                Map<Integer, String> fields,
                                Map<Integer, List<FIXMessageData>> groups) {
        Iterator<Integer> counterTags = map.groupKeyIterator();
        Set<Integer> counters = new LinkedHashSet<>();
        while (counterTags.hasNext()) counters.add(counterTags.next());

        Iterator<Field<?>> it = map.iterator();
        while (it.hasNext()) {
            Field<?> f = it.next();
            if (counters.contains(f.getTag())) continue;   // structural counter, skip
            fields.put(f.getTag(), String.valueOf(f.getObject()));
        }

        for (Integer counterTag : counters) {
            List<FIXMessageData> entries = new ArrayList<>();
            for (Group g : map.getGroups(counterTag)) {
                Map<Integer, String> entryFields = new LinkedHashMap<>();
                Map<Integer, List<FIXMessageData>> entryGroups = new LinkedHashMap<>();
                collect(g, entryFields, entryGroups);
                entries.add(new FIXMessageData(entryFields, entryGroups));
            }
            if (!entries.isEmpty()) groups.put(counterTag, entries);
        }
    }

    private static void copyFields(Iterator<Field<?>> it, Map<Integer, String> out) {
        while (it.hasNext()) {
            Field<?> f = it.next();
            out.put(f.getTag(), String.valueOf(f.getObject()));
        }
    }
}
