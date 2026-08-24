package com.fixflow.core.domain.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A FIX message as carried inside the engine: top-level fields plus repeating groups,
 * keyed by their counter tag (555 NoLegs, 864 NoEvents, ...). Recursive, so nested
 * groups are representable.
 *
 * <p>Field order is significant and preserved: the <em>first</em> field of a group entry
 * is the group delimiter tag, which QuickFIX/J needs to build a {@code quickfix.Group}.
 */
public record FIXMessageData(
        Map<Integer, String> fields,
        Map<Integer, List<FIXMessageData>> groups
) {

    public FIXMessageData {
        fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));

        Map<Integer, List<FIXMessageData>> copied = new LinkedHashMap<>();
        if (groups != null) {
            groups.forEach((counterTag, entries) ->
                    copied.put(counterTag, entries == null ? List.of() : List.copyOf(entries)));
        }
        groups = Collections.unmodifiableMap(copied);
    }

    public static FIXMessageData ofFields(Map<Integer, String> fields) {
        return new FIXMessageData(fields, Map.of());
    }

    /** Top-level fields only — the projection every pre-existing consumer still sees. */
    public Map<Integer, String> flatFields() {
        return fields;
    }

    public List<FIXMessageData> group(int counterTag) {
        return groups.getOrDefault(counterTag, List.of());
    }

    public Optional<String> groupValue(int counterTag, int index, int tag) {
        List<FIXMessageData> entries = group(counterTag);
        if (index < 0 || index >= entries.size()) return Optional.empty();
        return Optional.ofNullable(entries.get(index).fields().get(tag));
    }

    public boolean hasGroups() {
        return !groups.isEmpty();
    }
}
