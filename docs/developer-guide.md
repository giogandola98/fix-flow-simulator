# FIX Flow Simulator — Developer Guide

**Version:** 0.1.0-beta  
**Stack:** Java 21 · Spring Boot 3.3.2 · QuickFIX/J 2.3.1 · React 18 · Vite · Zustand · ReactFlow v12

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Prerequisites & Setup](#3-prerequisites--setup)
4. [Project Structure](#4-project-structure)
5. [Core Module — Domain Model & Ports](#5-core-module--domain-model--ports)
6. [Engine Module — Execution & Handlers](#6-engine-module--execution--handlers)
7. [Adapters Module — Persistence & QuickFIX/J](#7-adapters-module--persistence--quickfixj)
8. [API Module — REST & WebSocket](#8-api-module--rest--websocket)
9. [Frontend Architecture](#9-frontend-architecture)
10. [YAML Scenario DSL](#10-yaml-scenario-dsl)
11. [Variable Resolution](#11-variable-resolution)
12. [FIX Session Configuration](#12-fix-session-configuration)
13. [REST API Reference](#13-rest-api-reference)
14. [WebSocket Protocol](#14-websocket-protocol)
15. [Testing](#15-testing)
16. [Build & Deployment](#16-build--deployment)
17. [Configuration Reference](#17-configuration-reference)
18. [Gotchas & Known Issues](#18-gotchas--known-issues)

---

## 1. Overview

FIX Flow Simulator is a visual test automation tool for FIX protocol integrations. It lets engineers design scenario workflows as directed graphs in a browser-based canvas, execute them against live or loopback FIX sessions, and inspect the results — all without writing code.

**Key capabilities:**

- Create FIX sessions (INITIATOR / ACCEPTOR) with any QuickFIX/J-supported version (FIX 4.2, 4.4, FIX 5.0 SP2)
- Design scenarios visually via drag-and-drop node palette
- Define scenarios as versioned YAML DSL (import/export)
- Send and receive FIX messages with dynamic field values (UUIDs, timestamps, sequences, cross-node references)
- Validate received messages against field rules and date/time tolerances
- Branch flows on conditions, routing rules, retries, loops, and timeouts
- Execute scenarios against a session; watch events and messages stream in real time
- Download structured execution reports as JSON

---

## 2. Architecture

The project follows a **hexagonal (ports-and-adapters) architecture** split into five Maven modules. Dependencies flow inward: adapters and the API layer depend on the engine and core; nothing in core or engine depends on Spring or persistence.

```
┌─────────────────────────────────────────────────────────────┐
│  fix-flow-api  (Spring Boot 3.3.2 — REST + WebSocket)       │
│  Controllers · DTOs · WebSocketConfig · CORS · Wiring       │
├─────────────────────────────────────────────────────────────┤
│  fix-flow-adapters  (persistence + FIX transport)           │
│  JPA/H2 repository adapters · QuickFIX/J 2.3.1 adapter      │
├─────────────────────────────────────────────────────────────┤
│  fix-flow-engine  (business logic — no framework deps)      │
│  ExecutionManager · NodeHandlers · Validation · Variables   │
├─────────────────────────────────────────────────────────────┤
│  fix-flow-core  (pure domain — no dependencies)             │
│  Domain records · Port interfaces (inbound + outbound)      │
├─────────────────────────────────────────────────────────────┤
│  fix-flow-ui  (React 18 + Vite — served as static bundle)   │
│  ReactFlow canvas · Zustand state · TanStack Query          │
└─────────────────────────────────────────────────────────────┘
```

### Data flow — executing a scenario

```
Browser                 API                    Engine               QuickFIX/J
   │                     │                       │                      │
   │  POST /execute       │                       │                      │
   │─────────────────────>│                       │                      │
   │                     │  executionManager      │                      │
   │                     │    .start(id, sess)───>│                      │
   │                     │<── executionId ────────│                      │
   │<── { executionId } ─│                        │                      │
   │                     │                        │ [virtual thread]     │
   │  STOMP subscribe     │                        │  START node          │
   │──────────────────>  │                        │  SEND_FIX ──────────>│ FIX message
   │  /topic/executions/ │                        │                      │
   │  {id}/events        │                        │  EXPECT_FIX <────────│ FIX response
   │                     │  eventPublisher        │                      │
   │<── NODE_ENTERED ───  │<──────────────────────│                      │
   │<── NODE_EXITED ────  │                       │                      │
   │<── MESSAGE_SENT ───  │                       │                      │
   │<── EXECUTION_FINISHED│                       │                      │
```

---

## 3. Prerequisites & Setup

### Requirements

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21+ | Virtual threads; `JAVA_HOME` or system default |
| Maven | 3.9+ | Path: `~/maven/bin/mvn` on this machine |
| Node.js | 20+ | UI development only |
| npm | 9+ | Bundled with Node.js |

### Production Build

```bash
~/maven/bin/mvn clean package -DskipTests
java -jar fix-flow-api/target/fix-flow-api-0.1.0-beta.jar
```

The fat JAR copies `fix-flow-ui/target/dist` into `BOOT-INF/classes/static` via `maven-resources-plugin`. Open `http://localhost:8080`.

### Development Mode (hot reload)

```bash
# Terminal 1 — backend
~/maven/bin/mvn -pl fix-flow-api spring-boot:run

# Terminal 2 — frontend (proxies /api and /ws to :8080)
cd fix-flow-ui && npm install && npm run dev
```

Open `http://localhost:5173`. Vite's dev proxy configuration (`vite.config.ts`) forwards all `/api` and `/ws` requests to the Spring Boot process.

### Clean Environment (required before UAT)

Prior sessions and scenarios pollute test results. Before any integration test run:

```bash
fuser -k 8080/tcp                     # stop running instance
rm -rf ./data/fixflow.*               # wipe H2 database
java -jar fix-flow-api/target/fix-flow-api-0.1.0-beta.jar
```

Recreate sessions and scenarios from scratch. Never test against a DB with leftover state.

### H2 Console

`http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:file:./data/fixflow`  
Username: `sa` — no password.

---

## 4. Project Structure

```
fix-flow-simulator/
├── fix-flow-core/          Pure domain model + port interfaces
├── fix-flow-engine/        Execution logic, handlers, validation
├── fix-flow-adapters/      JPA persistence + QuickFIX/J adapter
├── fix-flow-api/           Spring Boot REST + WebSocket entrypoint
├── fix-flow-ui/            React 18 single-page application
├── docs/                   Developer documentation
│   ├── developer-guide.md  ← this file
│   ├── dsl-reference.md    YAML DSL syntax reference
│   ├── api-reference.md    REST + WebSocket reference
│   └── setup.md            Quick-start guide
├── uat-gui.js              Puppeteer end-to-end UAT script
├── data/                   H2 database files (auto-created)
└── pom.xml                 Root Maven multi-module POM
```

---

## 5. Core Module — Domain Model & Ports

Package root: `com.fixflow.core`

The core module has zero external dependencies. It defines the canonical domain types as Java records and the port interfaces that the engine calls into.

### Domain Records

#### `Scenario`

```java
record Scenario(
    UUID id,
    String name,
    String description,
    String version,
    String sessionRef,
    List<ScenarioNode> nodes,
    List<ScenarioEdge> edges
)
```

Key methods:
- `startNode()` — returns the single node of type `START`, or empty
- `findNode(id)` — looks up a node by ID

#### `ScenarioNode`

```java
record ScenarioNode(
    String id,
    String name,
    NodeType type,
    Map<String, Object> config,
    TimeoutConfig timeout,
    RetryPolicy retryPolicy,
    String onSuccess,
    String onFailure,
    String onTimeout,
    Position position             // UI layout only; not used by engine
)
```

`config` is a freeform `Map<String, Object>` — content depends on `NodeType`. The engine's handlers cast it as needed.

#### `ScenarioEdge`

```java
record ScenarioEdge(String from, String to, String label)
```

Edges exist for visualization. Traversal uses `onSuccess`/`onFailure`/`onTimeout` on nodes directly. The YAML serializer derives those IDs from edges.

#### `NodeType` (enum)

`START` · `SEND_FIX` · `EXPECT_FIX` · `VALIDATE` · `DECISION` · `BRANCH` · `ROUTE_FIX` · `RETRY` · `LOOP` · `WAIT` · `DELAY` · `TIMEOUT` · `HTTP_REQUEST` · `END_PASS` · `END_FAIL`

`BRANCH` is an alias for `DECISION`. `DELAY`/`TIMEOUT` are aliases for `WAIT`.

#### `FIXSessionConfig`

```java
record FIXSessionConfig(
    UUID id,
    String name,
    FIXMode mode,               // INITIATOR | ACCEPTOR
    FIXVersion fixVersion,      // FIX_42 | FIX_44 | FIXT_11
    String defaultApplVerID,    // required when fixVersion = FIXT_11
    String senderCompID,
    String targetCompID,
    String host,
    int port,
    int heartbeatInterval,
    boolean resetOnLogon,
    boolean resetOnLogout,
    boolean connected           // runtime state only; not persisted
)
```

#### `ExecutionEvent`

```java
record ExecutionEvent(
    UUID id,
    UUID executionId,
    ExecutionEventType type,
    String nodeId,
    Instant timestamp,
    String detail
)
```

`ExecutionEventType` values: `EXECUTION_STARTED` · `EXECUTION_FINISHED` · `NODE_ENTERED` · `NODE_EXITED` · `MESSAGE_SENT` · `MESSAGE_RECEIVED` · `TIMEOUT` · `ERROR` · `SESSION_UP` · `SESSION_DOWN`

#### `FIXMessage`

```java
record FIXMessage(
    UUID id,
    UUID executionId,
    Direction direction,         // INBOUND | OUTBOUND
    String rawFix,               // pipe-separated tag=value pairs
    Map<Integer, String> fields,
    Instant receivedAt
)
```

### Port Interfaces

#### Outbound ports (engine → infrastructure)

| Interface | Implementation |
|-----------|---------------|
| `EventPublisherPort` | `StompEventPublisher` (STOMP over WebSocket) |
| `ExecutionRepositoryPort` | `ExecutionRepositoryAdapter` (JPA/H2) |
| `ScenarioRepositoryPort` | `ScenarioRepositoryAdapter` (JPA/H2) |
| `FIXSessionPort` | `QuickFIXAdapter` (QuickFIX/J) |
| `InboundMessageListener` | `MessageRouter` (engine internal) |

---

## 6. Engine Module — Execution & Handlers

Package root: `com.fixflow.engine`

### ExecutionManager

`com.fixflow.engine.execution.ExecutionManager`

Central orchestrator. Spring `@Service`. Uses `Executors.newVirtualThreadPerTaskExecutor()` — each execution runs on its own virtual thread.

```java
UUID start(UUID scenarioId, UUID sessionId)
void stop(UUID executionId)
ExecutionStatus getStatus(UUID executionId)
ExecutionContext getContext(UUID executionId)
```

**Execution loop** (`runScenario`):

1. Emit `EXECUTION_STARTED`.
2. Find `START` node.
3. Loop while status = `RUNNING` and a current node exists:
   a. Emit `NODE_ENTERED`.
   b. `NodeDispatcher.dispatch(node, ctx)` → `NodeHandlerResult`.
   c. Persist `NodeResult` with measured duration.
   d. If `SEND_FIX`: persist outbound `FIXMessage`.
   e. If `EXPECT_FIX` / `ROUTE_FIX` and success: persist inbound `FIXMessage`.
   f. Emit `NODE_EXITED` (success) or `ERROR` (failure).
   g. Advance to `result.nextNodeId()`.
4. Final status: `PASSED` if loop exhausted naturally, else `FAILED` or `STOPPED`.
5. Emit `EXECUTION_FINISHED`. Persist final status.

All emit/persist calls swallow exceptions to avoid aborting the execution loop over telemetry failures.

### ExecutionContext

`com.fixflow.engine.execution.ExecutionContext`

Runtime state for one execution. Lives in memory only (not serialized to DB mid-run).

```java
UUID executionId()
Scenario scenario()
UUID sessionId()
ExecutionStatus status()

void setStatus(ExecutionStatus s)
void setCurrentNodeId(String id)

String getVariable(String key)
void setVariable(String key, String value)

Map<Integer, String> getNodeMessage(String nodeId)  // last FIX message stored for this node
void storeNodeMessage(String nodeId, Map<Integer, String> fields)
```

Variables are used by `VariableResolver` to resolve `{{node:id:tagN}}` and `{{var:key}}` placeholders.

### NodeDispatcher & Handlers

`com.fixflow.engine.handlers.NodeDispatcher` routes each node to its handler by `NodeType`.

All handlers implement:

```java
public interface NodeHandler {
    NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx);
}
```

`NodeHandlerResult` carries:

```java
record NodeHandlerResult(
    boolean success,
    String nextNodeId,      // null = end of flow
    String errorMessage     // non-null on failure
)
```

#### Handler summary

| Handler | Behaviour |
|---------|-----------|
| `StartHandler` | Immediately returns `success → node.onSuccess`. |
| `SendFIXHandler` | Resolves field variables, builds QuickFIX `Message`, sends via `FIXSessionPort`, stores fields in context. Returns `onSuccess`. |
| `ExpectFIXHandler` | Polls `MessageBuffer` until a matching inbound message arrives or timeout. Match uses `CorrelationEngine`. Stores matched fields. Returns `onSuccess` or `onTimeout`. |
| `ValidateHandler` | Runs `ValidationEngine` on context message fields. Returns `onSuccess` or `onFailure`. |
| `DecisionHandler` | Evaluates condition expression against context variables. Returns `onSuccess` (true) or `onFailure` (false). |
| `RouteFIXHandler` | Waits for inbound message; evaluates routing rules top-to-bottom; routes to matching rule's `targetNodeId`. |
| `RetryHandler` / `LoopHandler` | Executes sub-graph `maxAttempts` times with `delayMs` between attempts. |
| `WaitHandler` / `DelayHandler` | `Thread.sleep(timeout.toMillis())`. Returns `onSuccess`. |
| `HttpRequestHandler` | Issues HTTP request via `java.net.http.HttpClient`. Stores response body in context. |
| `EndHandler` / `EndFailHandler` | Returns `success=true/false` with `nextNodeId=null`. |

### CorrelationEngine

`com.fixflow.engine.correlation.CorrelationEngine`

Matches an inbound FIX message to an `EXPECT_FIX` node using the node's `correlation` config:

```yaml
correlation:
  sourceTag: 11     # tag to look up in the inbound message
  fromNode: send-x  # node whose outbound message provided the value
  targetTag: 11     # tag in the previously sent message
```

The engine resolves `fromNode`'s stored message, extracts `targetTag`, then checks whether the inbound message's `sourceTag` equals that value. If `correlation` is absent, any message of the right `msgType` matches.

### MessageBuffer

`com.fixflow.engine.fix.MessageBuffer`

Thread-safe queue of inbound FIX messages. `ExpectFIXHandler` and `RouteFIXHandler` poll this buffer with backoff. `MessageRouter` (implements `InboundMessageListener`) feeds messages into it as they arrive from QuickFIX/J.

### ValidationEngine

`com.fixflow.engine.validation.ValidationEngine`

Runs a list of `ValidationRuleConfig` entries against a `Map<Integer, String>` of FIX fields.

**Rule types:**

| Rule | Config fields |
|------|--------------|
| `EQUALS` | `tag`, `value` |
| `NOT_EQUALS` | `tag`, `value` |
| `ENUM` | `tag`, `values: []` |
| `REGEX` | `tag`, `pattern` |
| `NUMERIC_MIN` | `tag`, `numericValue` |
| `NUMERIC_MAX` | `tag`, `numericValue` |
| `FIELD_PRESENT` | `tag` |
| `FIELD_ABSENT` | `tag` |
| `DATE_RULE` | `tag`, `dateRuleId` (points to a `DateRule`) |

`DATE_RULE` supports two sub-types:
- `CURRENT_TIMESTAMP` — field value must be within `tolerance` of `Instant.now()`
- `FIELD_OFFSET` — field value must be within `tolerance` of `sourceNode.sourceTag ± offset`

In `strictMode: true`, any tag present in the message that has no rule defined causes a validation failure.

### VariableResolver

`com.fixflow.engine.variable.VariableResolver`

Resolves `{{...}}` placeholders in node config values at execution time. Uses a plugin chain — first plugin whose `supports(expression)` returns true wins.

| Expression | Plugin | Result |
|------------|--------|--------|
| `{{now}}` | `NowPlugin` | `Instant.now().toString()` (ISO 8601) |
| `{{uuid}}` | `UuidPlugin` | Random UUID string |
| `{{seq:name}}` | `SeqPlugin` | Monotonic counter keyed by `name` (per-JVM lifecycle) |
| `{{env:VAR}}` | `EnvPlugin` | `System.getenv("VAR")`, empty string if absent |
| `{{node:id:tagN}}` | `NodeFieldPlugin` | Value of tag `N` from the message stored for node `id` |
| `{{node:id:tagN:offset:+5m}}` | `DateOffsetPlugin` | Tag value parsed as `Instant`, plus `+5m` offset |
| `{{var:key}}` | `VarPlugin` | Value from `ExecutionContext.getVariable(key)` |

Offset format: `[+-](\d+)[smhd]` where `s`=seconds, `m`=minutes, `h`=hours, `d`=days.

To add a custom resolver, implement `VariableResolverPlugin` and register it in the constructor.

### ScenarioRegistry

`com.fixflow.engine.scenario.ScenarioRegistry`

In-memory map of `UUID → Scenario`. Populated by `ScenarioRegistryInitializer` at startup (reads all persisted scenarios from DB). When a scenario is created or updated via REST, the controller calls `registry.register(scenario)` immediately.

**The registry is not persisted.** After a JVM restart, `ScenarioRegistryInitializer` repopulates it.

### ScenarioDslParser

`com.fixflow.engine.scenario.ScenarioDslParser`

Parses a YAML DSL string (via Jackson `YAMLMapper`) into a `Scenario` domain record. Called by `ScenarioRepositoryAdapter` when saving a scenario with a non-empty `yamlDsl` field.

### HotReloadService

`com.fixflow.engine.fix.HotReloadService`

Re-parses a scenario's YAML DSL and re-registers it in the `ScenarioRegistry` without restarting QuickFIX/J sessions. Called on `PUT /scenarios/{id}`.

---

## 7. Adapters Module — Persistence & QuickFIX/J

Package root: `com.fixflow.adapters`

### Persistence

JPA entities live in `com.fixflow.adapters.persistence.entity`. Entity names map to H2 tables:

| Entity | Table | Notes |
|--------|-------|-------|
| `ScenarioEntity` | `scenario` | Stores `yamlDsl` as TEXT |
| `ExecutionEntity` | `execution` | Status, start/end times |
| `ExecutionEventEntity` | `execution_event` | Ordered by timestamp |
| `NodeResultEntity` | `node_result` | Per-node pass/fail + duration |
| `FIXMessageEntity` | `fix_message` | Raw FIX string + direction |
| `FIXSessionEntity` | `fix_session` | Session config; `connected` not persisted |
| `ValidationErrorEntity` | `validation_error` | Per-field rule failures |

Repository adapters (`ScenarioRepositoryAdapter`, `ExecutionRepositoryAdapter`, `FIXSessionRepositoryAdapter`) implement the core port interfaces by delegating to Spring Data JPA repositories.

**Schema management:** Hibernate `ddl-auto: update` creates/alters tables on startup. No migration tooling. To reset, delete the H2 files and restart.

### QuickFIX/J Adapter

`com.fixflow.adapters.quickfixj.QuickFIXAdapter` implements `FIXSessionPort`.

Responsibilities:
- Create and start `SocketAcceptor` / `SocketInitiator` per session config
- Translate between domain `FIXSessionConfig` and QuickFIX/J `SessionSettings`
- Implement `Application` callbacks (`fromApp`, `toApp`, `onLogon`, `onLogout`) via `QuickFIXApplicationAdapter`
- Route inbound `fromApp` messages to `InboundMessageListener` (= `MessageRouter`)
- Report session status changes via `EventPublisherPort` (`SESSION_UP` / `SESSION_DOWN`)

**Critical QuickFIX/J behaviour:**

QuickFIX/J's `BooleanConverter` only accepts `"Y"` / `"N"`, not `"true"` / `"false"`. Always set boolean settings with:

```java
settings.setString("ResetOnLogon", cfg.resetOnLogon() ? "Y" : "N");
```

Heartbeat messages are handled at the session layer (`fromAdmin`) and never reach `fromApp`. They will never appear in the execution message log — this is expected.

**Loopback testing** (ACCEPTOR + INITIATOR in the same JVM, same port):

- ACCEPTOR must connect before INITIATOR. Start ACCEPTOR first, then INITIATOR.
- The ACCEPTOR shows `connected=false` until the INITIATOR sends the logon — this is normal.
- After logon exchange, both sessions report `connected=true`.

---

## 8. API Module — REST & WebSocket

Package root: `com.fixflow.api`

### Application Entry Point

```java
@SpringBootApplication(scanBasePackages = "com.fixflow")
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
public class FixFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixFlowApplication.class, args);
    }
}
```

### WebSocket Configuration

STOMP over SockJS. Endpoint: `/ws`. All topic subscriptions use prefix `/topic`.

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

### ScenarioRegistryInitializer

Runs on `ApplicationReadyEvent`. Loads all scenarios from the DB and calls `ScenarioRegistry.register()` for each. Without this, scenarios saved in a previous JVM run would be unknown to the engine.

### REST Controllers

`/api/v1` prefix. All controllers use `@RestController` and produce/consume `application/json`.

| Controller | Path prefix | Key operations |
|-----------|-------------|----------------|
| `SessionController` | `/api/v1/sessions` | CRUD, connect, disconnect |
| `ScenarioController` | `/api/v1/scenarios` | CRUD, execute, import, export |
| `ExecutionController` | `/api/v1/executions` | list, get, stop, report |

**Execute endpoint** response shape:

```json
POST /api/v1/scenarios/{id}/execute
{ "sessionId": "uuid" }

→ { "executionId": "uuid" }
```

The field is `executionId`, not `id`.

**Connect / disconnect** return `204 No Content`. Call `GET /sessions/{id}` afterwards to read updated `connected` state.

**Session `connected` field** is a runtime property from the QuickFIX/J adapter. It is NOT persisted in H2. After a restart, all sessions report `connected: false` until explicitly reconnected.

### StompEventPublisher

Implements `EventPublisherPort`. Publishes to three STOMP topics:

```
/topic/executions/{executionId}/events    → ExecutionEvent JSON
/topic/executions/{executionId}/messages  → FIXMessage JSON
/topic/sessions/{sessionId}/status        → { sessionId, status: "UP"|"DOWN"|"LOGON" }
```

---

## 9. Frontend Architecture

Stack: React 18 · Vite 5 · TypeScript 5 · Tailwind 3 · @xyflow/react v12 · Zustand 4 · TanStack Query v5 · React Hook Form 7 · js-yaml 4 · SockJS + STOMP

### Directory Overview

```
fix-flow-ui/src/
├── main.tsx                  Entry point — imports i18n, mounts <App />
├── App.tsx                   Root layout (TopBar + left/right/bottom panels + canvas)
├── api/                      Axios-based HTTP client modules
│   ├── client.ts             Base Axios instance (baseURL = /api/v1)
│   ├── scenarios.ts          Scenario CRUD + execute + import/export
│   ├── sessions.ts           Session CRUD + connect/disconnect
│   └── executions.ts         Execution list/get/stop/report
├── store/                    Zustand stores (see §State Management)
│   ├── scenarioStore.ts
│   ├── executionStore.ts
│   └── sessionStore.ts
├── types/index.ts            TypeScript domain types
├── lib/
│   ├── scenarioSerializer.ts Nodes/edges ↔ YAML DSL (js-yaml)
│   └── parseFIXMessage.ts    Raw FIX string → { fields, msgType, skipped }
├── canvas/                   ReactFlow canvas and node components
├── panels/                   Left / right / bottom panel components
├── components/TopBar.tsx     Top toolbar (run, stop, save, language switcher)
├── hooks/                    WebSocket subscription hooks
├── app/wsClient.ts           SockJS + STOMP client
├── theme/colors.ts           Node border colors keyed by NodeType
└── i18n/                     i18next initialization + locale JSON files
```

### State Management (Zustand)

Three stores with no cross-store dependencies.

#### `scenarioStore`

```typescript
activeScenario: Scenario | null
scenarios: Scenario[]
nodes: ScenarioNode[]
edges: ScenarioEdge[]
selectedNodeId: string | null
isDirty: boolean

// Actions
setActiveScenario(s)    setScenarios(list)
setNodes(list)          setEdges(list)
addNode(n)              updateNode(id, patch)    removeNode(id)
addEdge(e)              removeEdge(id)
setSelectedNodeId(id)
markDirty()             markClean()
```

`isDirty` becomes `true` on any node/edge mutation and `false` after a successful save.

#### `executionStore`

```typescript
activeExecutionId: string | null
executionStatus: ExecutionStatus | 'IDLE'
events: ExecutionEvent[]
messages: FIXMessage[]
nodeStatuses: Record<string, 'idle'|'running'|'passed'|'failed'>
startedAt: string | null
endedAt: string | null

// Actions
setActiveExecution(id)
updateStatus(status)
addEvent(event)        // deduped by event.id
addMessage(msg)        // deduped by msg.id
setNodeStatus(nodeId, status)
setStartedAt(iso)      setEndedAt(iso)
reset()
```

`reset()` is called before each new execution to clear previous results.

#### `sessionStore`

```typescript
sessions: FIXSessionConfig[]
activeSession: FIXSessionConfig | null

setSessions(list)
setActiveSession(s)
updateSession(id, patch)  // patches both sessions[] and activeSession if id matches
```

`updateSession` is called by `useSessionSubscription` when a WS `SESSION_UP`/`SESSION_DOWN` event arrives, keeping `activeSession.connected` up to date without a full refetch.

> **Important:** Avoid re-selecting the active session from the TanStack Query `sessions` array after a connection is established. The TanStack cache may lag behind the WS-updated Zustand state. Re-selecting via `setActiveSession(sessions.find(...))` can overwrite `connected: true` with a stale `connected: false` from the cache.

### ReactFlow Canvas

`fix-flow-ui/src/canvas/FlowCanvas.tsx`

Uses the **local state pattern** required by @xyflow/react v12:

```typescript
// CORRECT: local useState, sync to store only on drag end
const [rfNodes, setRfNodes] = useState<RFNode[]>(() => toRFNodes(storeNodes));
const [rfEdges, setRfEdges] = useState<RFEdge[]>(() => toRFEdges(storeEdges));

const onNodesChange = useCallback((changes) => {
    setRfNodes(ns => applyNodeChanges(changes, ns)); // local only
}, []);

// On drag stop: sync position back to store
const onNodeDragStop = useCallback((_, node) => {
    updateNode(node.id, { position: node.position });
}, [updateNode]);
```

Recomputing `rfNodes` from the store on every `onNodesChange` strips ReactFlow's internal `measured` field and causes nodes to permanently disappear (`visibility: hidden`).

**Drag-and-drop from palette** uses HTML5 drag events. The palette sets `dataTransfer.setData('application/fix-flow-node-type', type)` on drag start. The canvas wrapper `div` handles `onDrop` and creates a new node at the drop coordinates.

### WebSocket Subscriptions

`useExecutionSubscription(executionId)` subscribes to:
- `/topic/executions/{id}/events` → calls `addEvent(e)` and `setNodeStatus(...)` on the execution store
- `/topic/executions/{id}/messages` → calls `addMessage(m)` on the execution store

`useSessionSubscription(sessionIds)` subscribes to `/topic/sessions/{id}/status` for each session ID and calls `updateSession(id, { connected })` on the session store.

Both hooks clean up STOMP subscriptions on unmount.

### Scenario Serialization

`fix-flow-ui/src/lib/scenarioSerializer.ts`

**`serializeToYaml(nodes, edges, meta)`** → YAML string

- Converts `fields: [{ tag, value }]` (SendFIX) to `fields: { tag: value }` map
- Resolves edges to `onSuccess`/`onFailure`/`onTimeout` IDs on each node
- Strips UI-only fields (`position`, ReactFlow internal state)
- Uses js-yaml with `noRefs: true`, `lineWidth: 120`

**`parseFromYaml(yaml)`** → `{ nodes, edges }`

- Converts `fields: { tag: value }` map back to `[{ tag, value }]` array
- Assigns default canvas positions if absent
- Synthesizes `edges` array from `onSuccess`/`onFailure`/`onTimeout` references

The YAML DSL is the canonical format. The in-memory graph is a derived view.

### Internationalization

`fix-flow-ui/src/i18n/index.ts`

Uses i18next + react-i18next + i18next-browser-languagedetector.

- Languages: `en` (English), `it` (Italian), `fr` (French)
- Detection order: `localStorage` → `navigator`
- Storage key: `fix-flow-lang`
- Fallback: `en`

Language switcher lives in `TopBar.tsx` (EN / IT / FR chip buttons). `i18n.changeLanguage(code)` is called on click.

All user-visible strings use `t('key')` from `useTranslation()`. Translation files: `src/i18n/locales/{en,it,fr}.json`.

---

## 10. YAML Scenario DSL

See **`docs/dsl-reference.md`** for the complete syntax reference including all node types, config shapes, timeout/retry options, and a worked RFQ example.

### DSL storage

The YAML is stored verbatim in the `yamlDsl` column of the `scenario` table. It is passed through `ScenarioDslParser` whenever the engine needs to execute the scenario. The GUI serializes the visual graph to YAML on save and parses YAML back to the graph on load.

### `id` field

Node IDs in the DSL must be valid UUIDs or stable strings used for cross-references (`fromNode`, `targetNodeId`, `jumpTo`). If omitted from the top-level scenario, a UUID is generated by the API.

### Fields in SEND_FIX

```yaml
# DSL format (map — stored in DB)
config:
  msgType: D
  fields:
    11: "{{uuid}}"
    55: AAPL

# In-memory (UI) format — array
config:
  msgType: D
  fields:
    - { tag: 11, value: "{{uuid}}" }
    - { tag: 55, value: AAPL }
```

`scenarioSerializer.ts` converts between the two forms transparently.

---

## 11. Variable Resolution

Variables are resolved by `VariableResolver` inside node handlers at execution time. The `{{...}}` syntax is supported in any string field value within a node config.

### Supported placeholders

| Placeholder | Resolves to |
|-------------|------------|
| `{{now}}` | Current UTC timestamp as ISO 8601 string |
| `{{uuid}}` | Random UUID (new value each resolution) |
| `{{seq:name}}` | Incrementing integer starting at 1; `name` scopes the counter |
| `{{env:VAR}}` | Value of environment variable `VAR`; empty string if unset |
| `{{node:id:tagN}}` | Value of FIX tag `N` from the last message stored for node `id` |
| `{{node:id:tagN:offset:+5m}}` | Same, with time offset applied (see format below) |
| `{{var:key}}` | Value set via `ExecutionContext.setVariable(key, value)` |

**Offset format:** `[+-]\d+[smhd]` — e.g. `+30s`, `-2h`, `+1d`, `+5m`.

### Sequence counters

`{{seq:name}}` counters are JVM-scoped (not per-execution). They reset on restart. Use a unique name per scenario or per field to avoid collisions across concurrent executions.

---

## 12. FIX Session Configuration

Sessions are created via the GUI or `POST /api/v1/sessions`. QuickFIX/J handles the FIX protocol layer.

### Field reference

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Human-readable label |
| `mode` | `INITIATOR` \| `ACCEPTOR` | INITIATOR connects out; ACCEPTOR listens |
| `fixVersion` | `FIX_42` \| `FIX_44` \| `FIXT_11` | FIX protocol version |
| `defaultApplVerID` | string | Required for `FIXT_11` (e.g. `FIX.5.0SP2`) |
| `senderCompID` | string | Tag 49 — your side |
| `targetCompID` | string | Tag 56 — counterparty |
| `host` | string | ACCEPTOR host/IP; only relevant for INITIATOR |
| `port` | int | TCP port; ACCEPTOR listens, INITIATOR connects |
| `heartbeatInterval` | int | FIX tag 108, seconds (standard: 30) |
| `resetOnLogon` | bool | Send reset flag on logon |
| `resetOnLogout` | bool | Reset state on logout |

### Session lifecycle

```
POST /sessions        → create config in DB
PUT /sessions/{id}/connect    → QuickFIX/J starts connector
                                 WS event SESSION_UP fires on logon
PUT /sessions/{id}/disconnect → QuickFIX/J stops connector
                                 WS event SESSION_DOWN fires
DELETE /sessions/{id}         → disconnect if needed, then delete config
```

Sessions are not auto-reconnected after JVM restart. Re-issue `PUT /connect` for each session on startup.

### Loopback testing

To test a FIX flow within a single simulator instance:

1. Create an ACCEPTOR session (`senderCompID=SERVER`, `targetCompID=CLIENT`, any port, e.g. 9901)
2. Create an INITIATOR session (`senderCompID=CLIENT`, `targetCompID=SERVER`, `host=localhost`, same port)
3. Connect ACCEPTOR first, then INITIATOR
4. After logon exchange (1–3 seconds), both show `connected: true`

---

## 13. REST API Reference

See **`docs/api-reference.md`** for the complete endpoint reference. Key facts below.

### Execute scenario

```
POST /api/v1/scenarios/{id}/execute
Content-Type: application/json

{ "sessionId": "uuid" }

→ 200 OK
{ "executionId": "uuid" }
```

Field is `executionId`, not `id`.

### Connect / disconnect

```
PUT /api/v1/sessions/{id}/connect     → 204 No Content
PUT /api/v1/sessions/{id}/disconnect  → 204 No Content
```

Call `GET /api/v1/sessions/{id}` after connect to read updated `connected` state. The connect is asynchronous (QuickFIX/J logon); allow 1–3 s before checking.

### GET executions messages

```
GET /api/v1/executions/{id}/messages
```

Queries the `FIXMessageEntity` table directly, not the in-memory execution context. Only available after execution completes (or while running, with partial results).

### HTTP status codes

| Status | Meaning |
|--------|---------|
| 200 | Success with body |
| 204 | Success, no body |
| 400 | Validation error |
| 404 | Resource not found |
| 409 | Conflict (e.g. session already connected) |
| 500 | Internal server error |

---

## 14. WebSocket Protocol

Connect using SockJS + STOMP:

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {
    client.subscribe('/topic/executions/{executionId}/events', msg => {
      const event = JSON.parse(msg.body);
      // event.type, event.nodeId, event.timestamp, event.detail
    });
  },
});
client.activate();
```

### Topics

| Topic | Payload type | When |
|-------|-------------|------|
| `/topic/executions/{id}/events` | `ExecutionEvent` | On each engine event |
| `/topic/executions/{id}/messages` | `FIXMessage` | On each FIX send/receive |
| `/topic/sessions/{id}/status` | `{ sessionId, status }` | On session up/down/logon |

### ExecutionEvent payload

```json
{
  "id": "uuid",
  "executionId": "uuid",
  "type": "NODE_ENTERED",
  "nodeId": "send-nos",
  "timestamp": "2025-01-01T10:00:00.123Z",
  "detail": "Entering node Send NOS [SEND_FIX]"
}
```

`type` values: `EXECUTION_STARTED` · `EXECUTION_FINISHED` · `NODE_ENTERED` · `NODE_EXITED` · `MESSAGE_SENT` · `MESSAGE_RECEIVED` · `TIMEOUT` · `ERROR` · `SESSION_UP` · `SESSION_DOWN`

### FIXMessage payload

```json
{
  "id": "uuid",
  "executionId": "uuid",
  "direction": "OUTBOUND",
  "rawFix": "11=UAT-001|35=D|38=100|40=1|54=1|55=AAPL",
  "fields": { "11": "UAT-001", "35": "D", "38": "100" },
  "receivedAt": "2025-01-01T10:00:00.456Z"
}
```

### Session status payload

```json
{ "sessionId": "uuid", "status": "UP" }
```

`status`: `"UP"` · `"LOGON"` · `"DOWN"`

---

## 15. Testing

### Backend tests

```bash
~/maven/bin/mvn test
```

Tests live in each module's `src/test/java`. The engine module has unit tests for handlers and validation rules that do not require Spring context.

`ExecutionManager` has a constructor for unit tests that skips persistence:

```java
new ExecutionManager(registry, dispatcher)  // no repo, no event publisher
```

### Frontend tests

No automated frontend tests currently. The `uat-gui.js` script at the repo root is a Puppeteer end-to-end test that covers the complete GUI workflow.

```bash
# Requires app running on :8080 with clean DB
node uat-gui.js
```

The script tests: session creation via form, drag-drop from palette, node configuration (paste FIX feature), visual edge connections, save, run, event log, FIX message log, statistics, session panel collapse, and language switching (EN/IT/FR).

---

## 16. Build & Deployment

### Full build

```bash
~/maven/bin/mvn clean package -DskipTests
```

Build order enforced by POM: `fix-flow-core` → `fix-flow-engine` → `fix-flow-adapters` → `fix-flow-ui` (runs `npm run build`) → `fix-flow-api` (copies `fix-flow-ui/target/dist` → static resources).

Output: `fix-flow-api/target/fix-flow-api-0.1.0-beta.jar` (~50 MB, self-contained).

### Running the fat JAR

```bash
java -jar fix-flow-api/target/fix-flow-api-0.1.0-beta.jar
```

Optional overrides:

```bash
java -jar fix-flow-api/target/fix-flow-api-0.1.0-beta.jar \
  --server.port=9090 \
  --spring.datasource.url=jdbc:h2:file:/data/prod/fixflow
```

### UI build only

```bash
cd fix-flow-ui && npm run build
# output: fix-flow-ui/dist/ (symlinked to fix-flow-ui/target/dist by Maven wrapper)
```

### Kill process on port 8080

```bash
fuser -k 8080/tcp
```

---

## 17. Configuration Reference

`fix-flow-api/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/fixflow;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:           # empty
  jpa:
    hibernate:
      ddl-auto: update  # creates/alters tables on startup; change to 'validate' in prod
    show-sql: false
  h2:
    console:
      enabled: true
      path: /h2-console
  threads:
    virtual:
      enabled: true     # Java 21 virtual threads for execution pool
  application:
    name: fix-flow

server:
  port: 8080

logging:
  level:
    com.fixflow: DEBUG  # set to INFO to reduce noise
```

`AUTO_SERVER=TRUE` in the JDBC URL enables H2's auto-server mode, allowing the H2 console to connect while the app is running.

### Frontend environment

Vite's dev proxy is configured in `fix-flow-ui/vite.config.ts`:

```typescript
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/ws':  { target: 'http://localhost:8080', ws: true, changeOrigin: true },
}
```

In production (fat JAR), the React build is served as static content by Spring Boot — no proxy needed.

---

## 18. Gotchas & Known Issues

### QuickFIX/J BooleanConverter

`BooleanConverter` only accepts `"Y"` / `"N"`. Using `"true"` / `"false"` throws at session startup.

```java
// Correct
settings.setString("ResetOnLogon", cfg.resetOnLogon() ? "Y" : "N");
```

### Heartbeat messages

QuickFIX/J heartbeats are handled at the session layer (`fromAdmin`) and never reach `fromApp`. They will never appear in the execution message log — this is expected behaviour.

### ScenarioRegistry on restart

`ScenarioRegistry` is in-memory only. `ScenarioRegistryInitializer` re-populates it from DB at startup. Scenarios created via REST are registered immediately. There is no automatic scenario reload after a crash without restart.

### ReactFlow v12 local state pattern

`FlowCanvas.tsx` keeps ReactFlow nodes/edges in local `useState`, not derived from Zustand. Deriving from the store on every `onNodesChange` call strips ReactFlow's internal `measured` field, causing permanent `visibility: hidden` on all nodes. Only sync positions back to the store on drag end.

### Active session vs TanStack Query cache

The `SessionPanel`'s active session dropdown calls `setActiveSession(sessions.find(...))` on change. The `sessions` here comes from TanStack Query, which may lag behind the WS-updated Zustand state. If you re-select a session programmatically after establishing a connection, the `connected: true` flag set by the WS `SESSION_UP` event will be overwritten with a potentially stale `connected: false` from the cache. Avoid unnecessary re-selection after connect.

### Session connect is asynchronous

`PUT /sessions/{id}/connect` returns immediately. The FIX logon exchange completes asynchronously. Wait 1–3 seconds before reading `connected` state via `GET /sessions/{id}` or the WS topic.

### Node IDs must be stable for cross-references

`{{node:id:tagN}}` and `correlation.fromNode` reference nodes by `id`. If you rename or replace a node, update all references. The GUI uses UUIDs generated by `crypto.randomUUID()` which are stable for the lifetime of the scenario.

### YAML DSL id field

Node IDs in the DSL should be UUIDs or stable string identifiers. Arbitrary short strings work but risk collisions if the same scenario is imported multiple times.

### UAT requires clean database

Before any UAT run, wipe the H2 database to avoid cross-test pollution:

```bash
fuser -k 8080/tcp
rm -rf ./data/fixflow.*
java -jar fix-flow-api/target/fix-flow-api-0.1.0-beta.jar
```

### Scenario `nodeCount` field

`GET /api/v1/scenarios` returns a lightweight list that includes a `nodeCount` field derived from the persisted YAML at query time. This count may differ from the live canvas if there are unsaved changes.

---

*End of developer guide.*
