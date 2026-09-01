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

# Run (current version)
java -jar fix-flow-api/target/fix-flow-api-0.7.3-beta.jar

# Run without browser auto-open (testing/CI)
java -Dfixflow.browser.auto-open=false -Dfixflow.no-relaunch=true \
  -jar fix-flow-api/target/fix-flow-api-0.7.3-beta.jar

# Dev mode (UI hot-reload on :5173, proxies /api + /ws to :8080)
~/maven/bin/mvn -pl fix-flow-api spring-boot:run   # backend
cd fix-flow-ui && npm run dev                       # frontend

# Tests (all modules)
~/maven/bin/mvn test

# UI build only
cd fix-flow-ui && npm run build   # outputs to fix-flow-ui/target/dist

# Kill app on port 8080
fuser -k 8080/tcp

# Browser automation (puppeteer-core in root node_modules)
node -e "require('./node_modules/puppeteer-core')" && echo ok
# Uses: executablePath: '/usr/bin/chromium-browser', args: ['--no-sandbox','--disable-setuid-sandbox']
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
| `fix-flow-api/src/main/java/.../config/BrowserOpenService.java` | Opens browser on ApplicationReadyEvent; disable with `-Dfixflow.browser.auto-open=false` |
| `fix-flow-engine/src/main/java/.../execution/ExecutionManager.java` | Runs scenarios node-by-node; emits events + persists node results/messages |
| `fix-flow-engine/src/main/java/.../scenario/ScenarioRegistry.java` | In-memory scenario store for engine |
| `fix-flow-engine/src/main/java/.../correlation/CorrelationEngine.java` | register()/onMessage()/cancel() — correlation waiters keyed by executionId |
| `fix-flow-engine/src/main/java/.../fix/MessageRouter.java` | Routes inbound FIX to CorrelationEngine; parks unmatched in MessageBuffer; drain() replays buffer |
| `fix-flow-engine/src/main/java/.../fix/MessageBuffer.java` | park()/poll()/pause()/resume() — buffers unmatched FIX during hot-reload or pre-registration races |
| `fix-flow-engine/src/main/java/.../handlers/ExpectFIXHandler.java` | Registers waiter, calls router.drain(sessionId) after register to consume pre-buffered msgs |
| `fix-flow-engine/src/main/java/.../handlers/RouteFIXHandler.java` | Same drain-after-register pattern for multi-rule routing |
| `fix-flow-adapters/src/main/java/.../quickfixj/QuickFIXAdapter.java` | QuickFIX/J connector lifecycle |
| `fix-flow-adapters/src/main/java/.../persistence/ExecutionRepositoryAdapter.java` | Persists executions, events, messages, node results |
| `fix-flow-ui/src/canvas/FlowCanvas.tsx` | ReactFlow canvas (local state pattern — see Gotchas) |
| `fix-flow-ui/src/lib/scenarioSerializer.ts` | Nodes/edges ↔ YAML DSL; synthesizes timeout edges from `timeout.jumpTo` on parse |
| `fix-flow-ui/src/lib/parseFIXMessage.ts` | Exports `ENGINE_TAGS = Set([8,9,10,34,49,52,56])` — session-level tags filtered before send |
| `fix-flow-ui/src/store/scenarioStore.ts` | Zustand: scenarios, nodes, edges, dirty flag |
| `fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx` | Timeout config UI; auto-manages timeout edge in store when JUMP action selected |
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

**QuickFIX/J BooleanConverter** — accepts only `"Y"`/`"N"`, not `"true"`/`"false"`. Use:
```java
settings.setString("ResetOnLogon", cfg.resetOnLogon() ? "Y" : "N");
```

**QuickFIX/J heartbeats** — handled at session layer (`fromAdmin`), never reach `fromApp`. Never appear in execution message log. Expected.

**ScenarioRegistry** — in-memory only. `ScenarioRegistryInitializer` populates from DB on startup. Scenarios created/updated via REST registered immediately via `registry.register(saved)`.

**ReactFlow v12 (`@xyflow/react`) — local state pattern** — `rfNodes`/`rfEdges` in `FlowCanvas.tsx` must be local `useState`, not derived from Zustand store. Recomputing from store on every `onNodesChange` strips React Flow's internal `measured` field → `visibility: hidden` forever. Use `applyNodeChanges` on local state; sync final drag positions back to store only.

**YAML DSL** — `id` must be valid UUID (or omitted). `fields` in SEND_FIX `config` accepts **both** `Map<Integer, String>` and a list of `{tag, value}` — `SendFIXHandler` has handled both forms since before repeating groups were added; the UI serialiser emits the map form for top-level fields, group entry fields use the list form. Nodes need explicit `onSuccess`/`onFailure` — edges array visual only, not used for traversal.

**Timeout jump edges** — `timeout.jumpTo` in node config is canonical source. `parseFromYaml` synthesizes timeout edge if missing from `edges` array. `TimeoutConfig.tsx` auto-upserts/removes edge in Zustand store on every change. Both must stay in sync.

**EXPECT_FIX / ROUTE_FIX race** — FIX message can arrive before `correlation.register()` called; gets parked in `MessageBuffer`. Always call `router.drain(ctx.sessionId().toString())` immediately after registering waiter. Both `ExpectFIXHandler` and `RouteFIXHandler` do this. Any new handler using `correlation.register*()` must do same.

**ENGINE_TAGS** — tags `8,9,10,34,49,52,56` session-level, managed by QuickFIX/J. `SendFIXHandler` filters before send. UI marks with yellow border via `ENGINE_TAGS` from `parseFIXMessage.ts`. Never include in test scenario field configs.

**Execution reset on rerun** — use atomic `useExecutionStore.setState({...})` to reset all fields at once. Splitting into `reset()` + `setActiveExecution()` creates null intermediate state; `useExecutionSubscription` briefly unsubscribes and misses first WS events.

**JAR double-click relaunch** — `FixFlowApplication.main()` checks `System.console() == null` (no TTY = double-clicked). Spawns terminal emulator and exits. Set `-Dfixflow.no-relaunch=true` to skip (already set on relaunched process to prevent loop). Without flag, running under `setsid` or any no-TTY context also triggers relaunch.

**UAT requires clean environment** — before UAT run, wipe all saved data; prior sessions/scenarios pollute results:
```bash
fuser -k 8080/tcp
rm -rf ./data/fixflow.*
java -Dfixflow.browser.auto-open=false -Dfixflow.no-relaunch=true \
  -jar fix-flow-api/target/fix-flow-api-0.7.3-beta.jar
```
Recreate sessions + scenarios from scratch. Never UAT against DB with leftover state.

**One instance per database** — the datasource URL has no `AUTO_SERVER`, on purpose (issue
#103). Two JVMs on the same `./data/fixflow` store could close the MVStore file channel under
a running instance, after which every endpoint answered 500 until the DB file was deleted. A
second instance now fails fast at startup with an explanatory message (`DatabaseLockPreflight`
probes the file lock before JPA starts; `DatabaseInUseFailureAnalyzer` formats the message —
Hibernate swallows H2's 90020, so probing first is the only way to keep the real cause). To run
a second simulator alongside one that is already up, give it both its own port and its own
database:
```bash
java -Dfixflow.browser.auto-open=false -Dfixflow.no-relaunch=true \
  -Dserver.port=9999 \
  -Dspring.datasource.url=jdbc:h2:file:./data/fixflow-9999 \
  -jar fix-flow-api/target/fix-flow-api-0.7.3-beta.jar
```
`GET /api/v1/system/health` reports the store: 200 `UP`, or 503 `DOWN` with the cause. While
down, every other endpoint answers 503 (not 500) with the same cause.

**Session connect on restart** — QuickFIX/J connectors not persisted. After restart, call `PUT /api/v1/sessions/{id}/connect` for each session. ACCEPTOR must connect before INITIATOR.

**Loopback FIX testing** — ACCEPTOR (SERVER/CLIENT, port 9001) + INITIATOR (CLIENT/SERVER, port 9001) both in same app instance. Acceptor shows `connected=false` waiting for logon — expected. Initiator shows `connected=true` once logon completes.

**Browser test pattern** — puppeteer-core in root `node_modules` (not fix-flow-ui). Run tests from repo root. Color check: browser renders hex as `rgb()` — test for `rgb(245, 158, 11)` not `#f59e0b`. Click scenario by button text containing scenario name. Wait 2–3s after click for React to re-render edges.

**Never `git add -A` / `git add .` / `git commit -a` while another agent may be working in the repo** — the index is shared across every process in a working tree, so a blanket stage sweeps in whoever else's half-finished files and the resulting commit is wrong for everyone but its author. Stage explicit paths only, ideally as a trailing pathspec on the commit itself:
```bash
git commit -m "message" -- path/one.java path/two.tsx
```
Run `git show --stat HEAD` after every commit and confirm the file list is exactly what you intended — this is what has caught every past incident. If a commit did sweep in someone else's files, `git reset --mixed HEAD~1` puts everything back on disk uncommitted, exactly as it was; re-commit with an explicit pathspec. Never `reset --hard`, never force-push.

**Repeating groups** — `FIXMessageData` carries `fields` plus `groups`
(`counterTag -> entries`), recursively. The flat `Map<Integer,String>` remains as
a top-level projection so correlation, ROUTE_FIX and DECISION are unchanged.
Never write a counter tag as a plain field: `Message.addGroup()` maintains it —
`SendFIXHandler` actively drops a plain field whose tag matches a declared
`counterTag`. In the GUI the counter is read-only and derived from the entry
count. Authoring convention: the **first field of an entry is the delimiter
tag** — this is necessary but not sufficient. `QuickFIXAdapter.buildMessage`
looks up the session's application `DataDictionary` and, when one is available,
builds each `Group` with `dd.getGroup(msgType, counterTag)`'s delimiter and full
ordered-field array, so the entry is serialised in dictionary order regardless
of authoring order; it falls back to the first-field convention and ascending-tag
order only when no dictionary is available (e.g. `buildMessage` called directly
from a unit test with no session). This matters because FIX requires an entry's
fields in dictionary-defined order, and QuickFIX/J's receiving parser — with
`checkUnorderedGroupFields` on, the default — ends an entry at the first field
whose group-order index doesn't increase; ascending-by-tag order is only
correct for groups whose dictionary order happens to already be ascending.
Entry fields are still serialised as an ordered LIST and never as a tag-keyed
map, both because the first-field-is-delimiter convention needs it and so the
fallback path stays deterministic: JavaScript iterates integer-like object keys
in ascending numeric order, so a map would move a lower tag ahead of the
delimiter and produce a malformed multileg message when no dictionary is
available. On the Java side, use `LinkedHashMap`, never `HashMap`.

**Inbound group parsing needs the data dictionary** — `AppDataDictionary=FIX50SP2.xml`
is set for FIXT.1.1 sessions in `QuickFIXAdapter.buildSettings`. Without it
QuickFIX/J parses repeated tags flat and `getGroups()` returns empty.
`ValidateIncomingMessage=N` disables validation, not group parsing.

**Group counter tags are stripped from the flat projection** —
`QuickFIXApplicationAdapter.extractMessage` deliberately omits 555, 864 and the
rest from `flatFields()`. A ROUTE_FIX matcher on a counter tag will never match.