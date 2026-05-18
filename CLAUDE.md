# FIX Flow Simulator

## Claude Behavior (mandatory)

- **Always use caveman mode**: invoke `/caveman:caveman` at session start. All responses + commit messages — drop articles, filler, pleasantries; keep full technical substance.
- **Branch discipline**: never commit features or bugfixes directly to `master`. Create dedicated branch (`feat/<name>` or `fix/<name>`), work there, open PR or merge to master when done.

## Environment

- OS: Linux (Ubuntu-based)
- Java: 21 (via `JAVA_HOME` or system default)
- Maven: `~/maven/bin/mvn` — **not** system `mvn`
- Node: system node + npm
- Shell: bash
- DB: H2 file at `./data/fixflow` (auto-created first run)
- App URL: `http://localhost:8080`
- Dev UI URL: `http://localhost:5173` (Vite hot-reload)
- H2 console: `http://localhost:8080/h2-console` — JDBC URL `jdbc:h2:file:./data/fixflow`
- GitHub: `gh` CLI authenticated as `giogandola98`, repo `giogandola98/fix-flow-simulator`

## Commands

```bash
# Build fat JAR (bundles React UI)
~/maven/bin/mvn clean package -DskipTests

# Run
java -jar fix-flow-api/target/fix-flow-api-0.2.2-beta.jar

# Dev mode (UI hot-reload on :5173, proxies /api + /ws to :8080)
~/maven/bin/mvn -pl fix-flow-api spring-boot:run   # backend
cd fix-flow-ui && npm run dev                       # frontend

# Tests (91 tests)
~/maven/bin/mvn test

# UI build only
cd fix-flow-ui && npm run build   # outputs to fix-flow-ui/target/dist

# Kill app on port 8080
fuser -k 8080/tcp
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
- H2 file DB: `jdbc:h2:file:./data/fixflow`
- WebSocket STOMP endpoint: `/ws` (SockJS fallback)
- Fat JAR copies `fix-flow-ui/target/dist` → `BOOT-INF/classes/static` via maven-resources-plugin

## Key Files

| File | Purpose |
|---|---|
| `fix-flow-api/src/main/java/.../config/ScenarioRegistryInitializer.java` | Populates engine registry from DB on startup |
| `fix-flow-engine/src/main/java/.../execution/ExecutionManager.java` | Runs scenarios node-by-node; emits events + persists node results/messages |
| `fix-flow-engine/src/main/java/.../scenario/ScenarioRegistry.java` | In-memory scenario store for engine |
| `fix-flow-adapters/src/main/java/.../quickfixj/QuickFIXAdapter.java` | QuickFIX/J connector lifecycle |
| `fix-flow-adapters/src/main/java/.../persistence/ExecutionRepositoryAdapter.java` | Persists executions, events, messages, node results |
| `fix-flow-ui/src/canvas/FlowCanvas.tsx` | ReactFlow canvas (local state pattern — see Gotchas) |
| `fix-flow-ui/src/lib/scenarioSerializer.ts` | Nodes/edges ↔ YAML DSL |
| `fix-flow-ui/src/store/scenarioStore.ts` | Zustand: scenarios, nodes, edges, dirty flag |
| `fix-flow-ui/src/hooks/useSessionSubscription.ts` | WS subscription for real-time session status |
| `docs/dsl-reference.md` | YAML DSL reference |
| `docs/api-reference.md` | REST + WebSocket API |

## API Facts

- `POST /api/v1/scenarios/{id}/execute` returns `{ executionId }` — **not** `{ id }`
- `PUT /api/v1/sessions/{id}/connect` and `disconnect` return `void` — call `GET /{id}` to refresh state
- `GET /api/v1/executions/{id}/messages` queries message table directly (`FIXMessageEntity`)
- `ExecutionEventType` values: `EXECUTION_STARTED, EXECUTION_FINISHED, NODE_ENTERED, NODE_EXITED, MESSAGE_SENT, MESSAGE_RECEIVED, TIMEOUT, ERROR, SESSION_UP, SESSION_DOWN`
- WS topics: `/topic/executions/{id}/events`, `/topic/executions/{id}/messages`, `/topic/sessions/{id}/status`
- ScenarioRequest body field is `yamlDsl` (not `yaml`)

## Gotchas

**QuickFIX/J BooleanConverter** — only accepts `"Y"`/`"N"`, not `"true"`/`"false"`. Use:
```java
settings.setString("ResetOnLogon", cfg.resetOnLogon() ? "Y" : "N");
```

**QuickFIX/J heartbeats** — handled at session layer (`fromAdmin`), never reach `fromApp`. Never appear in execution message log. Expected.

**ScenarioRegistry** — in-memory only. `ScenarioRegistryInitializer` populates from DB on startup. Scenarios created/updated via REST registered immediately via `registry.register(saved)`.

**ReactFlow v12 (`@xyflow/react`) — local state pattern** — `rfNodes`/`rfEdges` in `FlowCanvas.tsx` must be local `useState`, not derived from Zustand store. Recomputing from store on every `onNodesChange` strips React Flow's internal `measured` field → `visibility: hidden` forever. Use `applyNodeChanges` on local state; sync final drag positions back to store only.

**YAML DSL** — `id` must be valid UUID (or omitted). `fields` in SEND_FIX `config` must be `Map<Integer, String>`. Nodes need explicit `onSuccess`/`onFailure` — edges array visual only, not used for traversal.

**UAT requires clean environment** — before any UAT run, wipe all saved data; prior sessions/scenarios pollute results:
```bash
# Stop app, delete H2 DB, restart
fuser -k 8080/tcp
rm -rf ./data/fixflow.*
java -jar fix-flow-api/target/fix-flow-api-0.2.2-beta.jar
```
Recreate sessions + scenarios from scratch. Never UAT against DB with leftover state.

**Session connect on restart** — QuickFIX/J connectors not persisted. After restart, call `PUT /api/v1/sessions/{id}/connect` for each session. ACCEPTOR must connect before INITIATOR.

**Loopback FIX testing** — ACCEPTOR (SERVER/CLIENT, port 9001) + INITIATOR (CLIENT/SERVER, port 9001) both in same app instance. Acceptor shows `connected=false` waiting for logon — expected. Initiator shows `connected=true` once logon completes.