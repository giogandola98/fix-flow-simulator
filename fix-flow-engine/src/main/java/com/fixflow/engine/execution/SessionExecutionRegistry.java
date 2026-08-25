package com.fixflow.engine.execution;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which executions are currently running against which FIX session.
 *
 * <p>Inbound messages arrive session-scoped, while the event log and the FIX Messages tab are
 * execution-scoped: without this lookup the message router has no execution to attribute a
 * received message to, which is why a message that matched no waiting block used to leave no
 * trace at all (issue #77).
 *
 * <p>Deliberately a standalone bean rather than a method on {@code ExecutionManager}: the manager
 * already depends (through the dispatcher and its handlers) on the router, so the reverse
 * dependency would close a constructor-injection cycle.
 */
@Service
public class SessionExecutionRegistry {

    private final ConcurrentHashMap<String, Set<UUID>> bySession = new ConcurrentHashMap<>();

    public void register(String sessionId, UUID executionId) {
        if (sessionId == null || sessionId.isBlank() || executionId == null) return;
        bySession.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(executionId);
    }

    public void unregister(String sessionId, UUID executionId) {
        if (sessionId == null || executionId == null) return;
        bySession.computeIfPresent(sessionId, (k, set) -> {
            set.remove(executionId);
            return set.isEmpty() ? null : set;
        });
    }

    /** Executions currently running on {@code sessionId}; never null. */
    public Set<UUID> executionsFor(String sessionId) {
        Set<UUID> set = sessionId == null ? null : bySession.get(sessionId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }
}
