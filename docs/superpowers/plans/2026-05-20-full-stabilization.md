# Full Codebase Stabilization Plan — Issue #59

Branch: `enhancement/59-full-codebase-stabilization`

## Context

Comprehensive stabilization pass covering all critical and high-priority bugs found in deep codebase analysis. No new features — fix only. After all tasks complete, bump version and merge to master.

---

## Task 1: Fix CorrelationEngine cross-session contamination and race conditions

**Files:** `fix-flow-engine/src/main/java/com/fixflow/engine/correlation/CorrelationEngine.java`

**Problems:**
1. `onMessage()` iterates all registered waiters without filtering by `sessionId` — a FIX message from session A can wake up a waiter registered by session B.
2. `registerMulti()` uses `put()` instead of `putIfAbsent()` — silently overwrites an existing CompletableFuture, potentially losing a waiter.
3. `register()` uses `putIfAbsent()` correctly but `registerMulti()` doesn't.

**Fix:**
1. In `onMessage(FIXMessage message)`: filter `futures` (or the multi-rule map) to only consider waiters that match `message.getSessionId()`. The waiter key is `executionId`; the `FIXMessage` carries a `sessionId`. Store sessionId alongside the waiter at registration time, or pass it when filtering.
2. In `registerMulti()`: change `put()` to `putIfAbsent()` for each rule's future entry.

**Acceptance:** Unit tests pass; no cross-session message contamination.

---

## Task 2: Fix ExecutionManager startTime overwrite and contexts memory leak

**Files:** `fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionManager.java`

**Problems:**
1. `persistFinalStatus()` (around line 192-199) calls `Instant.now()` as startTime instead of reading the original startTime from context — overwrites real execution start time with finish time.
2. `contexts` ConcurrentHashMap (line 36) is populated on execution start and never cleaned up — memory leak for long-running apps with many executions.
3. Empty catch blocks (around line 146-154) silently swallow errors — bugs become invisible.

**Fix:**
1. In `persistFinalStatus()`: read startTime from the execution context object, not `Instant.now()`. If stored as `context.getStartTime()`, use that.
2. In completion path (EXECUTION_FINISHED / ERROR): call `contexts.remove(executionId)` after persisting final status.
3. In empty catch blocks: add `log.error("...", e)` with appropriate message; do not swallow silently.

**Acceptance:** Tests pass; context map cleaned after execution; errors logged.

---

## Task 3: Fix EXECUTION_FINISHED status always reporting PASSED

**Files:** `fix-flow-ui/src/hooks/useExecutionSubscription.ts`

**Problem:** Line ~29 — on `EXECUTION_FINISHED` event, always calls `updateStatus('PASSED')` regardless of the actual event payload. A failed execution shows green.

**Fix:** Read the actual status from the event payload. The event carries a `status` field (e.g., `PASSED`, `FAILED`, `ERROR`). Use it: `updateStatus(event.status ?? 'PASSED')`.

**Acceptance:** Failed executions show failed status in UI.

---

## Task 4: Fix ValidationErrors tab always empty

**Files:** `fix-flow-ui/src/panels/right/ValidationErrors.tsx`

**Problem:** Line ~12 — filters events for type `'VALIDATION_FAILED'`. Backend emits `'ERROR'` for validation failures, not `'VALIDATION_FAILED'`. Tab always shows empty.

**Fix:** Change filter from `'VALIDATION_FAILED'` to `'ERROR'`. Optionally also check event payload for validation-specific message patterns if ERROR covers non-validation errors too.

**Acceptance:** Validation errors surface in the tab when they occur.

---

## Task 5: Fix FIXMessageLog exportAll broken on Firefox

**Files:** `fix-flow-ui/src/panels/right/FIXMessageLog.tsx`

**Problem:** Line ~38 — `exportAll` creates an anchor element but never appends it to the DOM before calling `.click()`. Works in Chrome (which tolerates detached-element click), fails silently on Firefox.

**Fix:** Append anchor to `document.body` before `.click()`, then remove it after. Standard pattern:
```ts
document.body.appendChild(a);
a.click();
document.body.removeChild(a);
```

**Acceptance:** Export works in both Chrome and Firefox.

---

## Task 6: Fix onNodeDragStop stale closure in FlowCanvas

**Files:** `fix-flow-ui/src/canvas/FlowCanvas.tsx`

**Problem:** Lines 162-167 — `onNodeDragStop` callback closes over `nodes` from render time. If nodes change between renders, the stale snapshot is used when syncing drag position back to store.

**Fix:** Use functional updater `setNodes(prev => prev.map(...))` instead of closing over `nodes` directly. This avoids the stale reference.

**Acceptance:** Drag position sync uses current nodes, no stale update.

---

## Task 7: Fix RetryHandler and LoopHandler missing event emission

**Files:**
- `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RetryHandler.java`
- `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/LoopHandler.java`

**Problem:** Both handlers bypass `emitAndPersist` during iterations — no `NODE_ENTERED`/`NODE_EXITED` events for each retry or loop cycle. Execution log looks empty during repeat runs; status indicators don't update.

**Fix:** For each iteration, emit `NODE_ENTERED` before executing the target node and `NODE_EXITED` after. Use the same `emitAndPersist` mechanism that `ExecutionManager` uses for regular node traversal.

**Acceptance:** Each retry/loop iteration produces NODE_ENTERED + NODE_EXITED events visible in the execution timeline.

---

## Task 8: Fix CallScenarioHandler depth tracking

**Files:** `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/CallScenarioHandler.java`

**Problem:** Lines 61-68 — depth guard reads depth from parent context. Child context gets `depth+1` but parent check compares parent's depth. A scenario calling itself N times never sees N > MAX_DEPTH because the check always reads the parent's (non-incrementing) depth. Cycles can bypass the MAX_DEPTH guard.

**Fix:** Pass the incremented depth into the child context AND check the incremented depth before creating child. `if (currentDepth + 1 > MAX_DEPTH) throw ...` before constructing child context.

**Acceptance:** A scenario that calls itself recursively throws at depth == MAX_DEPTH.

---

## Task 9: Fix ExpectFIXConfig invalid default tag values

**Files:** `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`

**Problem:** Line ~53 — `sourceTag` and `targetTag` default to `0`. Tag 0 is not a valid FIX tag. This creates confusing default state and may cause matching to fail silently.

**Fix:** Default to `''` (empty string) and validate that the user has entered a nonzero value before allowing save/execute. Show placeholder text like "e.g. 35" in the input.

**Acceptance:** New EXPECT_FIX nodes don't pre-fill tag fields with 0.

---

## Task 10: Browser UI validation — all block types

**Method:** Automated puppeteer-core test from repo root.

**Coverage:**
1. Create scenario, add one of each node type (SEND_FIX, EXPECT_FIX, DECISION, ROUTE_FIX, LOOP, RETRY, DELAY, CALL_SCENARIO)
2. Verify drag-and-drop places node on canvas
3. Click each node, verify properties panel opens with correct type label
4. For ROUTE_FIX: add rule, set target → verify arrow appears on canvas; draw arrow on canvas → verify dropdown populated
5. For RETRY/LOOP: select target node → verify arrow appears
6. Save scenario, reload page, verify nodes + edges re-render at correct positions
7. Switch between two scenarios, verify canvas resets correctly

**Acceptance:** All nodes render; bidirectional sync works; save/reload preserves layout.

---

## Task 11: Version bump and release

**Files:**
- `fix-flow-core/pom.xml` (and all module pom.xml files that reference version)
- `fix-flow-api/src/main/java/.../FixFlowApplication.java` (if version hardcoded)
- `CLAUDE.md` (jar filename reference)

**Fix:**
1. Bump version from `0.2.8-beta` to `0.3.0-beta` across all pom.xml files.
2. Update CLAUDE.md jar filename.
3. Build fat JAR to verify compile + package succeeds.
4. Commit version bump.

**Acceptance:** `~/maven/bin/mvn clean package -DskipTests` succeeds; JAR at `fix-flow-api/target/fix-flow-api-0.3.0-beta.jar`.

---

## Execution Order

Tasks 1-9 are independent — can be executed sequentially in any order. Task 10 (browser test) must come after Tasks 3-9. Task 11 (version bump) must be last.

Suggested order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11
