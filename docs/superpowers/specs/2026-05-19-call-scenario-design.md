# CALL_SCENARIO Block — Design Spec

**Issue:** #57  
**Date:** 2026-05-19  
**Status:** Approved  

---

## Problem

Large workflows must fit in a single scenario graph. No reuse, duplicated logic, poor maintainability.

## Goal

Add a `CALL_SCENARIO` node type that executes another scenario as a synchronous sub-flow from within the parent scenario.

---

## Decisions

| Concern | Decision |
|---|---|
| Variable passing | Explicit mapping: user defines `inputVars` (parent→child) and `outputVars` (child→parent) |
| Event visibility | Single `NODE_EXITED` event in parent log: `"Called scenario X → PASSED/FAILED"` |
| FIX session | Child inherits parent session (no override in v1) |
| Execution model | Synchronous, same virtual thread |
| Async | Not in v1 |
| Engine approach | Extract `ScenarioExecutor` service from `ExecutionManager` |

---

## Architecture

### New / changed components

```
fix-flow-core
  NodeType                  + CALL_SCENARIO

fix-flow-engine
  ScenarioExecutor          NEW
  ExecutionManager          REFACTORED — delegates inner loop to ScenarioExecutor
  CallScenarioHandler       NEW — NodeHandler for CALL_SCENARIO

fix-flow-ui
  CallScenarioNode.tsx      NEW — canvas node (violet)
  CallScenarioConfig.tsx    NEW — config panel
  nodeTypes.ts              + CALL_SCENARIO → CallScenarioNode
  NodePalette.tsx           + CALL_SCENARIO in "Composition" group
  i18n (en/it/fr)           + all CALL_SCENARIO keys
```

### ScenarioExecutor

```java
@Service
public class ScenarioExecutor {
    private final NodeDispatcher dispatcher;

    // Walks the scenario graph synchronously.
    // No event emission. No persistence.
    // Returns ExecutionStatus (PASSED / FAILED / STOPPED).
    // Throws InterruptedException only.
    public ExecutionStatus execute(Scenario scenario, ExecutionContext ctx)
            throws InterruptedException { ... }
}
```

`ExecutionManager.runScenario()` delegates the node-walk loop to `ScenarioExecutor.execute()` and wraps it with persistence and event emission as before.

### CallScenarioHandler

```java
@Component
public class CallScenarioHandler implements NodeHandler {
    // deps: ScenarioRegistry, ScenarioExecutor, VariableResolver

    @Override NodeType getSupportedType() { return NodeType.CALL_SCENARIO; }

    @Override
    NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx)
            throws InterruptedException {

        // 1. Read targetScenarioId from config
        // 2. Validate (not null, found in registry)
        // 3. Check call depth ≤ 5
        // 4. Create childCtx (same sessionId, depth = parentDepth + 1)
        // 5. Copy inputVars parent → child
        // 6. ScenarioExecutor.execute(target, childCtx)
        // 7. Copy outputVars child → parent
        // 8. Return success / failure based on childCtx.status()
    }
}
```

---

## Config YAML shape

```yaml
- id: call-rfq
  name: Call RFQ Validation
  type: CALL_SCENARIO
  config:
    targetScenarioId: "11111111-2222-3333-4444-555555555555"
    inputVars:
      - { from: "var:orderId", to: "orderId" }
      - { from: "var:clOrdId", to: "clOrdId" }
    outputVars:
      - { from: "rfqResult", to: "var:rfqResult" }
  onSuccess: next-node
  onFailure: error-node
```

- `inputVars[].from` — expression in parent ctx (supports `{{var:x}}` or plain `var:x`)
- `inputVars[].to` — key set in child ctx via `ctx.setVariable(to, resolvedValue)`
- `outputVars[].from` — key read from child ctx
- `outputVars[].to` — key set in parent ctx (plain var name)

---

## UI

### Canvas node (CallScenarioNode.tsx)

- Violet border (`#8b5cf6`), rect shape, BaseNode
- Type badge: `CALL_SCENARIO`
- Body: target scenario name (looked up from store scenarios list by `targetScenarioId`)
- Falls back to `targetScenarioId` if scenario not found in store
- Standard top (target) + bottom (source) handles

### Config panel (CallScenarioConfig.tsx)

```
[ Node Name input ]

Target Scenario  [dropdown: all scenarios except current]  ?

Input Variables                                            [+ Add]
  Parent expression        Child key              [✕]
  var:orderId              orderId                 ✕

Output Variables                                           [+ Add]
  Child key                Parent variable         [✕]
  rfqResult                var:rfqResult           ✕
```

- Both tables follow the same pattern as `RouteFIXConfig` rule rows
- `from` / `to` = free-text inputs

### Palette

New group `"Composition"` in `NodePalette.tsx`:

```
CALL_SCENARIO — Execute another scenario as a reusable sub-flow
```

### i18n keys

```
palette.groups.composition
palette.nodes.CALL_SCENARIO
palette.descriptions.CALL_SCENARIO
nodeConfig.callScenario.targetScenario
nodeConfig.callScenario.inputVars
nodeConfig.callScenario.outputVars
nodeConfig.callScenario.addVar
nodeConfig.callScenario.from
nodeConfig.callScenario.to
nodeConfig.callScenario.noScenarios
```

---

## Error handling

| Condition | Result |
|---|---|
| `targetScenarioId` null/blank | `failure("No target scenario configured")` |
| Target not in registry | `failure("Scenario not found: <id>")` |
| Call depth > 5 | `failure("Max call depth exceeded (5)")` |
| Child ends at `END_FAIL` | `failure(node.onFailure(), "Sub-scenario ended with FAIL")` |
| Child throws exception | `failure(node.onFailure(), exception message)` |
| Child status `STOPPED` | propagate stop to parent |
| Output var key missing in child | silently skip (no error) |
| `InterruptedException` in child | rethrow to parent thread |

**Loop / recursion protection:**
- `call:depth` tracked implicitly via child `ExecutionContext` variable
- Parent reads depth from `ctx.getVariable("call:depth")` (default `"0"`)
- Child receives `call:depth = parentDepth + 1`
- Depth > 5 → immediate failure before creating child ctx

---

## Testing

### Unit

- `ScenarioExecutorTest` — PASSED / FAILED / STOPPED paths
- `CallScenarioHandlerTest`:
  - Success: child PASSES → parent success
  - Failure: child FAILS → parent failure
  - Missing target → immediate failure
  - Depth limit: depth 6 → failure
  - Input var copy: parent var appears in child
  - Output var copy: child var appears in parent after call

### Integration (`CallScenarioIntegrationTest`)

```
parent: START → CALL_SCENARIO(target=child) → END_PASS
child:  START → END_PASS
→ parent PASSED
```

```
parent: START → CALL_SCENARIO → END_PASS / onFailure=END_FAIL
child:  START → END_FAIL
→ parent FAILED
```

```
var round-trip: parent var:x → child reads x → child sets y → parent reads var:y
```

```
recursion: scenario A calls A → fails at depth 6
```

### Browser test

1. Drag `CALL_SCENARIO` onto canvas
2. Configure target scenario + 1 input var + 1 output var
3. Save → reload → verify config persists (positions + config)
4. Run with connected FIX session → verify single `NODE_EXITED` in parent event log

---

## Out of scope (v1)

- Async execution
- Sub-execution separate record in executions list
- Session override per node
- Navigation from node to referenced scenario
- Visual parent/child relationship in canvas
