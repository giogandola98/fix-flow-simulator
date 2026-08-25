package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.Direction;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.SessionExecutionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Routes inbound FIX to the correlation engine and, whether or not a block was waiting for it,
 * records the message against every execution running on that session.
 *
 * <p>The recording is the point: before it, an inbound message was persisted only as a side effect
 * of an EXPECT_FIX / ROUTE_FIX node succeeding, so a message that matched nothing was parked in the
 * buffer and left no trace anywhere — no event, no row in the FIX Messages tab (issue #77). The
 * router is now the single source of truth for the inbound direction, so what the tab shows is
 * what the wire delivered rather than what the graph happened to consume.
 */
@Service
public class MessageRouter implements InboundMessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    /** Cap on messages remembered as "arrived while no execution was running on the session". */
    private static final int PENDING_CAP = 512;

    private final CorrelationEngine correlation;
    private final MessageBuffer buffer;
    private final SessionExecutionRegistry sessions;
    private final EventPublisherPort publisher;
    private final ExecutionRepositoryPort executionRepo;

    /**
     * Messages that arrived before any execution was running on their session, so there was
     * nothing to attribute them to. If one is later consumed by a block that has since started,
     * {@link #drain} records it then. Size-capped LRU: a message nobody ever claims is dropped
     * rather than retained forever.
     */
    private final Map<FIXMessageData, String> pendingUnattributed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FIXMessageData, String> eldest) {
                    return size() > PENDING_CAP;
                }
            });

    @Autowired
    public MessageRouter(CorrelationEngine correlation,
                         MessageBuffer buffer,
                         SessionExecutionRegistry sessions,
                         EventPublisherPort publisher,
                         ExecutionRepositoryPort executionRepo) {
        this.correlation = correlation;
        this.buffer = buffer;
        this.sessions = sessions;
        this.publisher = publisher;
        this.executionRepo = executionRepo;
    }

    /** Convenience constructor for unit tests that only exercise routing, not recording. */
    public MessageRouter(CorrelationEngine correlation, MessageBuffer buffer) {
        this(correlation, buffer, new SessionExecutionRegistry(), null, null);
    }

    @Override
    public void onMessage(String sessionId, FIXMessageData message) {
        // Snapshot the executions BEFORE handing the message to the correlation engine: completing
        // a waiter unblocks its scenario thread, which can reach its END node and deregister
        // itself before this method gets to record anything.
        Set<UUID> executions = Set.copyOf(sessions.executionsFor(sessionId));

        if (buffer.isPaused()) {
            buffer.park(sessionId, message);
            record(sessionId, executions, message, "buffered: the scenario registry is reloading");
            return;
        }
        boolean consumed = correlation.onMessage(sessionId, message);
        if (!consumed) buffer.park(sessionId, message);
        record(sessionId, executions, message, consumed
                ? "matched by a waiting block"
                : "no block was waiting for it — buffered");
    }

    public void drain(String sessionId) {
        Optional<FIXMessageData> next;
        do {
            next = buffer.poll(sessionId, message -> correlation.onMessage(sessionId, message));
            next.ifPresent(message -> {
                // Only messages that arrived with no execution to attribute them to are still
                // unrecorded; everything else was already logged on arrival.
                if (pendingUnattributed.remove(message) != null) {
                    record(sessionId, Set.copyOf(sessions.executionsFor(sessionId)), message,
                            "matched by a waiting block (it had been buffered)");
                }
            });
        } while (next.isPresent());
    }

    /** Logs one inbound message against the executions that were running when it arrived. */
    private void record(String sessionId, Set<UUID> executions, FIXMessageData message, String note) {
        if (executions.isEmpty()) {
            pendingUnattributed.put(message, sessionId);
            return;
        }
        String raw = RawFixRenderer.render(message);
        String msgType = message.flatFields().get(35);
        String detail = "Received FIX" + (msgType == null ? "" : " " + msgType) + " — " + note;
        for (UUID executionId : executions) {
            emit(executionId, ExecutionEvent.of(executionId, ExecutionEventType.MESSAGE_RECEIVED, null, detail));
            store(executionId, new FIXMessage(UUID.randomUUID(), executionId, Direction.INBOUND,
                    raw, message.fields(), Instant.now()));
        }
    }

    private void emit(UUID executionId, ExecutionEvent event) {
        if (publisher != null) {
            try { publisher.publish(event); } catch (Throwable t) {
                log.warn("Failed to publish inbound event for execution {}: {}", executionId, t.getMessage());
            }
        }
        if (executionRepo != null) {
            try { executionRepo.addEvent(executionId, event); } catch (Throwable t) {
                log.warn("Failed to persist inbound event for execution {}: {}", executionId, t.getMessage());
            }
        }
    }

    private void store(UUID executionId, FIXMessage message) {
        if (executionRepo != null) {
            try { executionRepo.addMessage(executionId, message); } catch (Throwable t) {
                log.warn("Failed to persist inbound message for execution {}: {}", executionId, t.getMessage());
            }
        }
        if (publisher != null) {
            try { publisher.publishMessage(executionId, message); } catch (Throwable t) {
                log.warn("Failed to publish inbound message for execution {}: {}", executionId, t.getMessage());
            }
        }
    }
}
