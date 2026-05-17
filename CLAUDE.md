# FIX Flow Simulator
always act in caveman mode

## Commands

```bash
# Build fat JAR (bundles React UI)
~/maven/bin/mvn clean package -DskipTests

# Run
java -jar fix-flow-api/target/fix-flow-api-0.1.0-SNAPSHOT.jar

# Dev mode (UI hot-reload on :5173, proxies /api + /ws to :8080)
~/maven/bin/mvn -pl fix-flow-api spring-boot:run   # backend
cd fix-flow-ui && npm run dev                       # frontend

# Tests (91 tests)
~/maven/bin/mvn test

# UI build only
cd fix-flow-ui && npm run build   # outputs to fix-flow-ui/target/dist
```

## Architecture

```
fix-flow-core      — domain records (Scenario, Execution, FIXSession) + port interfaces
fix-flow-engine    — execution engine, node handlers, scenario registry, hot-reload, correlation
fix-flow-adapters  — JPA/H2 persistence + QuickFIX/J 2.3.1 FIX session adapter
fix-flow-api       — Spring Boot 3.3.2 REST + WebSocket (STOMP/SockJS) + static UI bundle
fix-flow-ui        — React 18 + Vite + @xyflow/react v12 + Zustand + TanStack Query + Tailwind
```

- Package root: `com.fixflow`
- Java 21, Maven at `~/maven/bin/mvn` (not system mvn)
- H2 file DB: `jdbc:h2:file:./data/fixflow` | console: `http://localhost:8080/h2-console`
- WebSocket STOMP endpoint: `/ws` (SockJS fallback)
- Fat JAR copies `fix-flow-ui/target/dist` → `BOOT-INF/classes/static` via maven-resources-plugin

## Key Files

| File | Purpose |
|---|---|
| `fix-flow-api/src/main/java/.../config/ScenarioRegistryInitializer.java` | Populates engine registry from DB on startup |
| `fix-flow-engine/src/main/java/.../execution/ExecutionManager.java` | Runs scenarios node-by-node |
| `fix-flow-engine/src/main/java/.../scenario/ScenarioRegistry.java` | In-memory scenario store for engine |
| `fix-flow-adapters/src/main/java/.../quickfixj/QuickFIXAdapter.java` | QuickFIX/J connector lifecycle |
| `fix-flow-ui/src/canvas/FlowCanvas.tsx` | ReactFlow canvas (local state pattern — see Gotchas) |
| `fix-flow-ui/src/lib/scenarioSerializer.ts` | Nodes/edges ↔ YAML DSL |
| `fix-flow-ui/src/store/scenarioStore.ts` | Zustand: scenarios, nodes, edges, dirty flag |
| `docs/dsl-reference.md` | YAML DSL reference |
| `docs/api-reference.md` | REST + WebSocket API |

## API Facts

- `POST /api/v1/scenarios/{id}/execute` returns `{ executionId }` — **not** `{ id }`
- `PUT /api/v1/sessions/{id}/connect` and `disconnect` return `void` — call `GET /{id}` to refresh state
- `ExecutionEventType` values: `EXECUTION_STARTED, EXECUTION_FINISHED, NODE_ENTERED, NODE_EXITED, MESSAGE_SENT, MESSAGE_RECEIVED, TIMEOUT, ERROR, SESSION_UP, SESSION_DOWN`
- WS topics: `/topic/executions/{id}/events`, `/topic/executions/{id}/messages`, `/topic/sessions/{id}/status`

## Gotchas

**QuickFIX/J BooleanConverter** — only accepts `"Y"`/`"N"`, not `"true"`/`"false"`. Use:
```java
settings.setString("ResetOnLogon", cfg.resetOnLogon() ? "Y" : "N");
```

**QuickFIX/J heartbeats** — handled at session layer (`fromAdmin`), never reach `fromApp`. They will never appear in the execution message log. Expected behavior.

**ScenarioRegistry** — in-memory only. `ScenarioRegistryInitializer` populates it from DB on startup. Scenarios created/updated via REST are registered immediately via `registry.register(saved)`.

**ReactFlow v12 (`@xyflow/react`) — local state pattern** — `rfNodes`/`rfEdges` in `FlowCanvas.tsx` must be local `useState`, not derived from Zustand store. Recomputing from store on every `onNodesChange` strips React Flow's internal `measured` field, causing `visibility: hidden` forever. Use `applyNodeChanges` on local state; only sync final drag positions back to store.

**YAML DSL** — `id` field must be valid UUID (or omitted). `fields` in SEND_FIX `config` must be `Map<Integer, String>` format, not a list of `{tag, value}` objects.

**Session connect on restart** — QuickFIX/J connectors are not persisted. After restart, call `PUT /api/v1/sessions/{id}/connect` again for each session.
