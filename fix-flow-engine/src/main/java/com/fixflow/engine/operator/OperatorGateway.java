package com.fixflow.engine.operator;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-off between an OPERATOR node, which waits, and the REST layer, through which a person
 * confirms or fails the step. Prompts are keyed by the id of the execution the node runs in (a
 * PARALLEL child has its own); each remembers the executions it is nested under so the parent can
 * list the prompts of its children.
 */
@Service
public class OperatorGateway {

    public record OperatorAnswer(boolean ok, String note) {}

    public record OpenPrompt(UUID executionId, String nodeId, String instruction,
                             String confirmLabel, String failLabel) {}

    private record Entry(OpenPrompt prompt, List<UUID> ancestors, CompletableFuture<OperatorAnswer> future) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private static String key(UUID executionId, String nodeId) { return executionId + "/" + nodeId; }

    public CompletableFuture<OperatorAnswer> open(UUID executionId, String nodeId, String instruction) {
        return open(executionId, List.of(), nodeId, instruction, null, null);
    }

    public CompletableFuture<OperatorAnswer> open(UUID executionId, List<UUID> ancestors, String nodeId,
                                                  String instruction, String confirmLabel, String failLabel) {
        Entry entry = new Entry(new OpenPrompt(executionId, nodeId, instruction, confirmLabel, failLabel),
                List.copyOf(ancestors), new CompletableFuture<>());
        Entry previous = entries.put(key(executionId, nodeId), entry);
        if (previous != null) previous.future().cancel(false);
        return entry.future();
    }

    /** Completes the prompt of {@code nodeId} in {@code executionId}; false when none is open. */
    public boolean answer(UUID executionId, String nodeId, boolean ok, String note) {
        Entry entry = entries.remove(key(executionId, nodeId));
        if (entry == null) return false;
        return entry.future().complete(new OperatorAnswer(ok, note));
    }

    /** An open prompt of the execution itself or, failing that, of one nested under it. */
    public Optional<OpenPrompt> open(UUID executionId) {
        return entries.values().stream()
                .filter(e -> e.prompt().executionId().equals(executionId))
                .map(Entry::prompt).findFirst()
                .or(() -> entries.values().stream()
                        .filter(e -> e.ancestors().contains(executionId))
                        .map(Entry::prompt).findFirst());
    }

    /** Drops the prompt of one node once its handler has stopped waiting. */
    public void close(UUID executionId, String nodeId) {
        Entry entry = entries.remove(key(executionId, nodeId));
        if (entry != null) entry.future().cancel(false);
    }

    /** Cancels every prompt of the execution and of the ones nested under it. */
    public void cancel(UUID executionId) {
        entries.entrySet().removeIf(e -> {
            boolean mine = e.getValue().prompt().executionId().equals(executionId)
                    || e.getValue().ancestors().contains(executionId);
            if (mine) e.getValue().future().cancel(false);
            return mine;
        });
    }
}
