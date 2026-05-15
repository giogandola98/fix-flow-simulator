# FIX Flow Simulator — Design Spec

**Date:** 2026-05-15
**Status:** Approved

---

## Overview

Production-ready web tool to visually design, run, and monitor FIX protocol simulation scenarios. Runs as a local web app: single Spring Boot fat-JAR opens in browser. No install required beyond Java.

**Stack:**
- Backend: Java 21, Spring Boot 3.x, QuickFIX/J, JPA/Hibernate, H2 (file mode)
- Frontend: React 18, TypeScript, Vite, ReactFlow, Zustand, TanStack Query, Tailwind CSS
- Realtime: WebSocket STOMP/SockJS
- Build: Maven multi-module + Vite (frontend built into JAR static resources)
- Architecture: Monolithic fat-JAR with hexagonal internals (ports + adapters)
- FIX versions: FIX 4.2, FIX 4.4, FIX 5.0 SP2 (FIXT.1.1 session layer) — selected per session, configurable via GUI and REST API

---

## 1. Module Structure

```
fix-flow-simulator/               ← Maven root (BOM + aggregator)
├── fix-flow-core/                ← Domain model, ports (interfaces), DSL entities
│   ├── domain/scenario/          ← Scenario, Node, Edge, Variable
│   ├── domain/session/           ← FIXSession config
│   ├── domain/execution/         ← Execution, NodeResult, Event
│   └── ports/                    ← inbound + outbound port interfaces
├── fix-flow-engine/              ← Use cases, runtime, validation, correlation
│   ├── scenario/                 ← ScenarioRegistry, ExecutionManager
│   ├── fix/                      ← FIXSessionManager, MessageRouter, MessageBuffer
│   ├── validation/               ← ValidationEngine, DateRuleEngine
│   ├── variable/                 ← VariableResolver
│   └── correlation/              ← CorrelationEngine
├── fix-flow-adapters/            ← Outbound adapters
│   ├── quickfixj/                ← QuickFIX/J adapter (FIX port impl)
│   ├── persistence/              ← JPA/H2 repositories
│   └── events/                   ← WebSocket event publisher
├── fix-flow-api/                 ← Spring Boot app, REST controllers, WS config
│   ├── rest/                     ← Scenario CRUD, session, execution APIs
│   └── websocket/                ← STOMP endpoints, event subscriptions
└── fix-flow-ui/                  ← React + Vite (built into fix-flow-api resources)
    ├── src/canvas/               ← ReactFlow editor
    ├── src/panels/               ← Left palette, right properties, bottom runtime
    └── src/store/                ← Zustand state management
```

Single fat-JAR produced by `fix-flow-api`. `fix-flow-ui` build output copied to `fix-flow-api/src/main/resources/static` during Maven build.

---

## 2. Core Domain Model

### Scenario (DSL entity)
```
Scenario
├── id, name, description, version
├── sessionRef (FIXSessionId)
├── runtimePolicy (SEQUENTIAL | PARALLEL)
│     SEQUENTIAL: only one execution of this scenario runs at a time
│     PARALLEL: multiple concurrent executions of the same scenario allowed
├── routingRules: List<RoutingRule>
├── correlationRules: List<CorrelationRule>
├── nodes: List<Node>
├── edges: List<Edge>
└── variables: Map<String, VariableDef>

RoutingRule
├── criteria: Map<String, String>   ← tag/field matchers (e.g. MsgType=R, SenderCompID=BROKER)
├── scenarioId: String              ← route to this scenario
└── priority: int                   ← higher = evaluated first

CorrelationRule
├── sourceTag: int                  ← tag in incoming message (e.g. 131)
├── targetNode: String              ← node whose sent/received message provides reference value
├── targetTag: int                  ← tag from that node's message
└── timeWindowMs: long              ← max age of reference message (0 = no limit)
```

### Node (polymorphic)
```
Node
├── id, name, type (enum), description
├── config: Map<String, Object>      ← type-specific config
├── timeout: TimeoutConfig
├── retryPolicy: RetryPolicy
└── onSuccess, onFailure, onTimeout: String (→ next nodeId)

Node types: START, SEND_FIX, EXPECT_FIX, VALIDATE, WAIT,
            TIMEOUT, DECISION, BRANCH, RETRY, LOOP,
            DELAY, END_PASS, END_FAIL
```

### Execution
```
Execution
├── id, scenarioId, scenarioVersion, sessionId
├── status: RUNNING | PASSED | FAILED | STOPPED
├── startTime, endTime, currentNodeId
├── variables: Map<String, String>    ← resolved at runtime
├── nodeResults: List<NodeResult>
└── events: List<ExecutionEvent>

ExecutionEvent
├── type: NODE_STARTED | NODE_COMPLETED | NODE_FAILED | MSG_SENT |
│         MSG_RECEIVED | VALIDATION_PASSED | VALIDATION_FAILED |
│         TIMEOUT | RETRY | SESSION_UP | SESSION_DOWN |
│         HOT_RELOAD_STARTED | HOT_RELOAD_COMPLETED | SCENARIO_PASSED | SCENARIO_FAILED
├── nodeId, timestamp, detail
└── fixMessage (optional)
```

### Scenario YAML DSL
```yaml
version: "1.0"
name: "RFQ Flow"
session: SIMULATOR
runtimePolicy: SEQUENTIAL
variables:
  quoteReqId: { type: UUID }
nodes:
  - id: n1
    type: SEND_FIX
    name: "Send RFQ"
    config:
      msgType: R
      fields:
        131: "{{uuid}}"
        60: "{{now}}"
        55: "AAPL"
    onSuccess: n2
    timeout:
      value: 5
      unit: SECONDS
      onTimeout: FAIL

  - id: n2
    type: EXPECT_FIX
    name: "Await Quote"
    config:
      msgType: S
      correlate:
        field: 131
        fromNode: n1
    onSuccess: n3
    onTimeout: end_fail
    timeout:
      value: 10
      unit: SECONDS

  - id: n3
    type: VALIDATE
    name: "Validate Quote"
    config:
      validations:
        - tag: 35   rule: EQUALS   value: "S"
        - tag: 131  rule: EQUALS   ref: "{{node:n1:tag131}}"
        - tag: 132  rule: NUMERIC_MIN   value: 0
      strictMode: false
    onSuccess: end_pass
    onFailure: end_fail

  - id: end_pass
    type: END_PASS

  - id: end_fail
    type: END_FAIL

edges:
  - from: n1   to: n2   label: success
  - from: n2   to: n3   label: success
  - from: n2   to: end_fail   label: timeout
  - from: n3   to: end_pass   label: success
  - from: n3   to: end_fail   label: failure
```

---

## 3. Runtime Architecture

### Component Map
```
FIXSessionManager
├── owns Map<sessionId, QuickFIX/J Session>
├── builds QuickFIX/J SessionSettings from persisted FIXSession config at connect time
├── supports FIX 4.2, FIX 4.4, FIXT.1.1/FIX50SP2 — data dictionary selected by fixVersion field
├── start/stop sessions independently of scenarios
├── emits: SESSION_UP, SESSION_DOWN events
└── on reconnect: notifies all active executions

MessageRouter (inbound)
├── receives all inbound FIX from QuickFIX/J adapter
├── evaluates RoutingRules → finds candidate executions
├── if match: delivers to CorrelationEngine
├── if no match: parks in MessageBuffer (keyed by sessionId+msgType)
└── no message dropped during hot reload (buffer absorbs)

CorrelationEngine
├── per execution: matches buffered/live msgs to current ExpectFIX node
├── rules: field match, msgType, tag match, prior-node ref, time window
└── on match: delivers to ExecutionManager

MessageBuffer
├── ring buffer per session (configurable capacity, default 1000)
├── TTL per entry (default 60s)
└── drained when execution resumes or scenario reloads

ScenarioRegistry
├── Map<scenarioId, ScenarioDefinition>
├── hot reload: swap definition; running executions finish on old version
└── version tracked per execution

ExecutionManager
├── Map<executionId, ExecutionContext>
├── each execution: own virtual thread (Java 21 Project Loom)
├── node dispatcher: routes to NodeHandler by type
├── exception in one execution → mark FAILED; no impact on others
└── FIX session failure → broadcasts to active executions (configurable: PAUSE | FAIL)

NodeHandlers (strategy pattern, one per node type)
├── SendFIXHandler      → resolves variables → sends via FIXSessionManager
├── ExpectFIXHandler    → blocks on CorrelationEngine with timeout
├── ValidateHandler     → runs ValidationEngine against last received message
├── DecisionHandler     → evaluates condition expression → branches
├── RetryHandler        → loops back N times with configurable delay
├── LoopHandler         → iterates sub-flow N times or until condition
├── WaitHandler         → sleeps configured duration
├── DelayHandler        → fixed delay between nodes
└── EndHandler          → marks execution PASSED or FAILED

HotReloadService
├── watches scenario file changes (filesystem) or API-triggered
├── pauses MessageBuffer, swaps ScenarioRegistry, resumes buffer
└── in-flight executions unaffected (pinned to snapshot version)
```

### Event Flow
```
QuickFIX/J → MessageRouter → CorrelationEngine
                                    ↓
                           ExecutionManager → NodeHandler
                                    ↓
                           ExecutionEventPublisher
                                    ↓
                    STOMP /topic/executions/{id}/events
                                    ↓
                              React UI (live updates)
```

### VariableResolver
```
{{now}}                  → current UTC ISO-8601 timestamp
{{uuid}}                 → random UUID
{{seq:name}}             → named auto-increment integer
{{env:VAR}}              → environment variable
{{node:n1:tag131}}       → tag 131 from node n1's last sent/received message
{{offset:node:n1:tag60:+5m}}  → datetime from n1.tag60 + 5 minutes
```
Extensible via `VariableResolverPlugin` interface.

---

## 4. API Design

### REST (`/api/v1`)

| Method | Path | Description |
|--------|------|-------------|
| GET | /scenarios | List all scenarios |
| POST | /scenarios | Create scenario |
| GET | /scenarios/{id} | Get scenario |
| PUT | /scenarios/{id} | Update scenario |
| DELETE | /scenarios/{id} | Delete scenario |
| GET | /scenarios/{id}/versions | Version history |
| POST | /scenarios/{id}/validate | Validate DSL |
| POST | /scenarios/{id}/import | Import YAML/JSON |
| GET | /scenarios/{id}/export | Download YAML |
| POST | /scenarios/{id}/execute | Start execution |
| POST | /executions/{id}/stop | Stop execution |
| GET | /executions/{id} | Execution status + results |
| GET | /executions/{id}/events | Full event log |
| GET | /executions/{id}/messages | All FIX messages |
| GET | /executions/{id}/report | Downloadable report |
| GET | /sessions | List sessions |
| POST | /sessions | Create/configure session |
| PUT | /sessions/{id} | Update session config (including FIX version) |
| DELETE | /sessions/{id} | Delete session |
| PUT | /sessions/{id}/connect | Connect session |
| PUT | /sessions/{id}/disconnect | Disconnect session |
| GET | /sessions/{id}/status | Live session status |
| POST | /scenarios/{id}/reload | Hot reload |

**FIX Session config payload (POST/PUT /sessions):**
```json
{
  "name": "SIMULATOR",
  "mode": "INITIATOR",
  "fixVersion": "FIXT.1.1",
  "defaultApplVerID": "FIX.5.0SP2",
  "senderCompID": "CLIENT",
  "targetCompID": "SERVER",
  "host": "localhost",
  "port": 9878,
  "heartbeatInterval": 30,
  "reconnectInterval": 5,
  "resetOnLogon": false,
  "resetOnLogout": false
}
```
`fixVersion` accepted values: `"FIX.4.2"`, `"FIX.4.4"`, `"FIXT.1.1"` (FIX 5.0 SP2).
- FIX 4.2 / 4.4: standard session + application layer, single data dictionary.
- FIXT.1.1 + FIX50SP2: split session/application dictionaries, QuickFIX/J `DefaultApplVerID=FIX.5.0SP2`.

QuickFIX/J loads the corresponding data dictionary at session creation. Changing `fixVersion` requires session disconnect + reconnect (enforced by API — 409 if session connected).

### WebSocket STOMP Topics
```
/topic/executions/{id}/events     → ExecutionEvent stream
/topic/executions/{id}/messages   → raw + parsed FIX messages
/topic/sessions/{id}/status       → session up/down events
/app/executions/{id}/stop         → inbound stop command
```

---

## 5. Persistence Schema (H2/JPA)

```sql
scenarios         (id, name, version, yaml_dsl, created_at, updated_at)
scenario_versions (id, scenario_id, version, yaml_dsl, created_at)
fix_sessions      (id, name, config_json, mode, fix_version, default_appl_ver_id,
                   sender_comp_id, target_comp_id, host, port, heartbeat_interval)
executions        (id, scenario_id, scenario_version, session_id,
                   status, start_time, end_time)
execution_events  (id, execution_id, type, node_id, timestamp, detail_json)
fix_messages      (id, execution_id, direction, raw_fix, fields_json, received_at)
node_results      (id, execution_id, node_id, status, start_time, end_time, error)
validation_errors (id, node_result_id, tag, rule, expected, actual)
```

---

## 6. Validation & Date Rules

### ValidationEngine rule types
```yaml
validations:
  - tag: 35    rule: EQUALS        value: "8"
  - tag: 39    rule: ENUM          values: ["0","1","2"]
  - tag: 11    rule: EQUALS        ref: "{{node:n1:tag11}}"
  - tag: 60    rule: DATE_RULE     dateRule: transactTimeRule
  - tag: 38    rule: NUMERIC_MIN   value: 1
  - tag: 38    rule: NUMERIC_MAX   value: 1000000
  - tag: 999   rule: FIELD_ABSENT
  - tag: 58    rule: REGEX         pattern: ".*filled.*"
  - tag: 49    rule: NOT_EQUALS    value: "UNKNOWN"
  - tag: 14    rule: FIELD_PRESENT
strictMode: true
```

### DateRuleEngine — 2 types
```yaml
dateRules:
  # Current message receive timestamp validation
  - id: transactTimeRule
    type: CURRENT_TIMESTAMP
    tolerance: { value: 5, unit: SECONDS }

  # Prior message field ± offset
  - id: expireTimeRule
    type: FIELD_OFFSET
    sourceNode: n1
    sourceTag: 60
    offset: { value: 5, unit: MINUTES }
    tolerance: { value: 2, unit: SECONDS }
```

Supported offset units: SECONDS, MINUTES, HOURS, DAYS (positive and negative).

---

## 7. Frontend Architecture

**Stack:** React 18 + TypeScript + Vite + ReactFlow + Zustand + TanStack Query + Tailwind CSS

```
fix-flow-ui/src/
├── app/
│   ├── App.tsx                    ← layout shell (dark theme)
│   ├── router.tsx                 ← React Router routes
│   └── wsClient.ts                ← STOMP WebSocket client singleton
├── canvas/
│   ├── FlowCanvas.tsx             ← ReactFlow wrapper
│   ├── nodes/                     ← custom node component per type
│   └── edges/                     ← custom edges (success/fail/timeout coloring)
├── panels/
│   ├── left/
│   │   ├── NodePalette.tsx        ← drag-source for node types
│   │   └── ScenarioList.tsx       ← scenario browser + version selector
│   ├── right/
│   │   ├── PropertiesPanel.tsx    ← context-aware selected node config
│   │   ├── NodeConfig/            ← per-node config forms
│   │   │   ├── SendFIXConfig.tsx
│   │   │   ├── ExpectFIXConfig.tsx
│   │   │   ├── ValidateConfig.tsx
│   │   │   ├── DateRulesEditor.tsx
│   │   │   └── TimeoutConfig.tsx
│   │   └── SessionPanel.tsx       ← session selector + full session config form:
│   │                                   name, mode (INITIATOR/ACCEPTOR),
│   │                                   FIX version dropdown (4.2/4.4/5.0SP2),
│   │                                   SenderCompID, TargetCompID,
│   │                                   host, port, heartbeat interval,
│   │                                   connect/disconnect button
│   └── bottom/
│       ├── RuntimePanel.tsx       ← tabs: Events | FIX Messages | Errors | Stats
│       ├── EventLog.tsx           ← live execution events (WS feed)
│       ├── FIXMessageLog.tsx      ← raw + parsed FIX messages
│       │                              ☑ "Hide Heartbeats" checkbox filters MsgType=0 (Heartbeat)
│       │                              and MsgType=1 (TestRequest) from display; messages still persisted
│       ├── ValidationErrors.tsx
│       └── ExecutionStats.tsx
├── store/
│   ├── scenarioStore.ts
│   ├── executionStore.ts
│   └── sessionStore.ts
├── api/
│   ├── scenarios.ts
│   ├── executions.ts
│   └── sessions.ts
└── theme/
    └── darkTheme.ts               ← dark palette matching mockup
```

**UI behaviors:**
- Drag node from palette → drop on canvas → new node created
- Click node → right panel renders that node's config form
- Run → POST /execute → subscribe WS → nodes highlight live (green=passed, red=failed, amber=running)
- Bottom panel auto-scrolls FIX log during execution
- Save → triggers hot reload API → reload event appears in log

---

## 8. Testing Strategy

```
Unit (JUnit 5 + Mockito)
  ├── DSL parsing          — ScenarioParser YAML↔domain round-trips
  ├── VariableResolver     — each variable type resolves correctly
  ├── DateRuleEngine       — offset math, tolerance boundary cases
  ├── ValidationEngine     — each rule type pass/fail
  └── CorrelationEngine    — field match, time window, fallback

Integration (Spring Boot Test + H2)
  ├── ScenarioRegistry     — CRUD + versioning
  ├── ExecutionManager     — state transitions, node sequencing
  ├── MessageRouter        — routing rules, buffer drain on reload
  ├── HotReloadService     — reload while execution running
  └── REST API             — full HTTP round-trips

FIX Tests (FakeFixAdapter — in-memory, no network)
  ├── MultiScenarioTest    — 3 scenarios on 1 session simultaneously
  ├── TimeoutTest          — node timeout triggers correct action
  ├── RetryTest            — retry N times then fail
  └── SessionFailureTest   — session down mid-execution
```

---

## 9. Implementation Phases

Full system, delivered step-by-step:

| Phase | Scope |
|-------|-------|
| 1 | Maven multi-module scaffold, core domain model, DSL parsing |
| 2 | H2 persistence, JPA entities, ScenarioRegistry |
| 3 | QuickFIX/J adapter, FIXSessionManager, initiator + acceptor |
| 4 | ExecutionManager, NodeHandlers (Send/Expect/End), virtual threads |
| 5 | MessageRouter, CorrelationEngine, MessageBuffer |
| 6 | ValidationEngine, DateRuleEngine, VariableResolver |
| 7 | Full node types: Decision, Branch, Retry, Loop, Delay, Wait |
| 8 | HotReloadService, multi-scenario multi-session support |
| 9 | REST API, WebSocket STOMP events |
| 10 | React UI: layout shell, canvas, node palette, properties panel |
| 11 | React UI: runtime panel, live event log, FIX message log |
| 12 | React UI: node config forms, validation editor, date rules editor |
| 13 | Reporting: execution report, downloadable output |
| 14 | Tests: unit + integration + FIX adapter tests |
| 15 | Documentation: user guide, DSL reference, API reference |

---

## Non-Functional Requirements

- Java 21 (virtual threads for execution isolation)
- One execution failure must not affect others or the FIX session
- One FIX session failure must not crash the application
- Multiplatform: macOS, Windows, Linux (single JAR)
- Observable: all events persisted + streamed
- Extensible: VariableResolver, NodeHandler, ValidationRule all plugin-capable
