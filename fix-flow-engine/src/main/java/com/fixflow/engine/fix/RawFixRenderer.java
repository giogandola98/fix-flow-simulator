package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FIXMessageData} into the pipe-delimited raw form persisted and published for
 * the FIX Messages tab. Shared by the execution manager (outbound) and the message router
 * (inbound) so both directions read identically in the log.
 */
public final class RawFixRenderer {

    private RawFixRenderer() {}

    /**
     * Top-level fields are sorted by tag for a stable, readable record — safe because top-level
     * fields carry no delimiter ordering requirement. Group entries are rendered by
     * {@link #renderEntry}, which must NOT sort.
     */
    public static String render(FIXMessageData data) {
        List<String> parts = new ArrayList<>();
        data.fields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> parts.add(e.getKey() + "=" + e.getValue()));
        data.groups().forEach((counterTag, entries) -> {
            parts.add(counterTag + "=" + entries.size());
            for (FIXMessageData entry : entries) {
                parts.add(renderEntry(entry));
            }
        });
        return String.join("|", parts);
    }

    /**
     * Renders one repeating-group entry (and any nested sub-groups) in original insertion order —
     * deliberately UNSORTED, unlike {@link #render}'s top-level fields. The first field of an entry
     * is the FIX delimiter tag, read positionally by {@code QuickFIXAdapter.applyGroups}; sorting
     * by tag here would routinely move a lower-numbered field (e.g. LegSettlType 587) ahead of the
     * delimiter (e.g. LegSymbol 600) and misrepresent what was actually sent or received.
     */
    private static String renderEntry(FIXMessageData entry) {
        List<String> parts = new ArrayList<>();
        entry.fields().forEach((tag, value) -> parts.add(tag + "=" + value));
        entry.groups().forEach((counterTag, nested) -> {
            parts.add(counterTag + "=" + nested.size());
            for (FIXMessageData nestedEntry : nested) {
                parts.add(renderEntry(nestedEntry));
            }
        });
        return String.join("|", parts);
    }
}
