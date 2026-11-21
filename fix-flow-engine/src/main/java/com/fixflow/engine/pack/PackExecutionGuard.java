package com.fixflow.engine.pack;

import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.SessionExecutionRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Which executions keep a pack from being changed: those of the pack's scenarios still running,
 * and any execution running on one of the pack's sessions (which an update may reconnect and an
 * uninstall deletes).
 */
@Component
public class PackExecutionGuard {

    private final ExecutionManager executions;
    private final SessionExecutionRegistry sessionExecutions;

    public PackExecutionGuard(ExecutionManager executions, SessionExecutionRegistry sessionExecutions) {
        this.executions = executions;
        this.sessionExecutions = sessionExecutions;
    }

    /** Sorted ids of the executions in the way; empty when the pack may change. */
    public List<UUID> running(Set<UUID> scenarioIds, Collection<UUID> sessionIds) {
        Set<UUID> out = new TreeSet<>(executions.unfinishedExecutionsOf(scenarioIds));
        for (UUID s : sessionIds) out.addAll(sessionExecutions.executionsFor(s.toString()));
        return List.copyOf(out);
    }

    void check(String packId, Set<UUID> scenarioIds, Collection<UUID> sessionIds) {
        List<UUID> running = running(scenarioIds, sessionIds);
        if (!running.isEmpty()) {
            throw new PackConflictException("pack " + packId + " has running executions: "
                    + String.join(", ", running.stream().map(UUID::toString).toList())
                    + "; stop them or wait for them to finish");
        }
    }
}
