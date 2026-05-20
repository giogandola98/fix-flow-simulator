# Conditional Routing (Issue #19) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three functional gaps in the already-scaffolded ROUTE_FIX / DECISION feature: placeholder resolution in matchers, debug event emission, message persistence, edge label UX, UI polish, and DSL docs.

**Architecture:** `RouteFIXHandler` gets `VariableResolver` injected and resolves `{{...}}` matcher values before registering with `CorrelationEngine`. `ExecutionManager` gains one branch to emit matched-rule label in `NODE_EXITED` and persist inbound ROUTE_FIX messages. UI: `RouteFIXConfig` and `DecisionConfig` get Wave-2-style banners/hints/VarRefPanel; `FlowCanvas.onConnect` uses rule label for ROUTE_FIX edges. DSL docs updated.

**Tech Stack:** Java 21, Spring Boot 3.3.2, JUnit 5, AssertJ, React 18, TypeScript, Tailwind

---

## File map

| Status | File | Change |
|--------|------|--------|
| Modify | `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RouteFIXHandler.java` | Inject `VariableResolver`; resolve matcher values; store matched rule label |
| Modify | `fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionManager.java` | Emit matched rule in NODE_EXITED; persist ROUTE_FIX inbound message |
| Create | `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RouteFIXHandlerTest.java` | Handler unit tests |
| Modify | `fix-flow-engine/src/test/java/com/fixflow/engine/correlation/CorrelationEngineTest.java` | Multi-rule tests |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/RouteFIXConfig.tsx` | Banner, hints, VarRefPanel |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/DecisionConfig.tsx` | Banner, hints, VarRefPanel |
| Modify | `fix-flow-ui/src/canvas/FlowCanvas.tsx` | Use rule label for ROUTE_FIX edges |
| Modify | `docs/dsl-reference.md` | ROUTE_FIX + DECISION sections |

---

### Task 1: Resolve placeholders in ROUTE_FIX matchers + store matched rule label

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RouteFIXHandler.java`

- [ ] **Step 1: Write the failing test**

Create `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RouteFIXHandlerTest.java`:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RouteFIXHandlerTest {

    private static ExecutionContext freshCtx() {
        Scenario s = new Scenario(UUID.randomUUID(), "t", "", "1.0", "s",
                RuntimePolicy.PARALLEL, List.of(), List.of(), List.of(), List.of(), Map.of());
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void staticMatcherRoutesToCorrectTarget() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();

        Map<String, Object> rule = Map.of(
            "ruleId", "r1", "label", "Quote",
            "matchers", Map.of("35", "S"),
            "targetNodeId", "quote-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(rule)),
            new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            correlation.onMessage("sess", Map.of(35, "S", 131, "RFQ-001"));
        });

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("quote-node");
        assertThat(ctx.getVariable("node:route1:matchedRuleLabel")).isEqualTo("Quote");
    }

    @Test
    void placeholderMatcherValueResolvedBeforeMatching() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();
        ctx.storeNodeMessage("send-rfq", Map.of(131, "RFQ-001"));

        Map<String, Object> rule = Map.of(
            "ruleId", "r1", "label", "Matched RFQ",
            "matchers", Map.of("131", "{{node:send-rfq:tag131}}"),
            "targetNodeId", "process-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(rule)),
            new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            correlation.onMessage("sess", Map.of(131, "RFQ-001", 35, "S"));
        });

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("process-node");
    }

    @Test
    void defaultRuleUsedWhenNoMatchersFire() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();

        Map<String, Object> specificRule = Map.of(
            "ruleId", "r1", "label", "Quote",
            "matchers", Map.of("35", "S"),
            "targetNodeId", "quote-node");
        Map<String, Object> defaultRule = Map.of(
            "ruleId", "r2", "label", "Default",
            "matchers", Map.of(),
            "targetNodeId", "default-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(specificRule, defaultRule)),
            new TimeoutConfig(500, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            correlation.onMessage("sess", Map.of(35, "AG"));  // does not match "S"
        });

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("default-node");
        assertThat(ctx.getVariable("node:route1:matchedRuleLabel")).isEqualTo("Default");
    }

    @Test
    void timeoutYieldsFailure() throws Exception {
        CorrelationEngine correlation = new CorrelationEngine();
        RouteFIXHandler handler = new RouteFIXHandler(correlation, new VariableResolver());
        ExecutionContext ctx = freshCtx();

        Map<String, Object> rule = Map.of(
            "ruleId", "r1", "label", "Quote",
            "matchers", Map.of("35", "S"),
            "targetNodeId", "quote-node");
        ScenarioNode node = new ScenarioNode("route1", "Router", NodeType.ROUTE_FIX,
            Map.of("rules", List.of(rule)),
            new TimeoutConfig(100, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
            null, null, "fail", null);

        NodeHandlerResult r = handler.handle(node, ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.nextNodeId()).isEqualTo("fail");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator
~/maven/bin/mvn test -pl fix-flow-engine -Dtest=RouteFIXHandlerTest -q 2>&1 | tail -20
```

Expected: FAIL — constructor `RouteFIXHandler(CorrelationEngine, VariableResolver)` not found.

- [ ] **Step 3: Implement — inject VariableResolver + store matched rule label**

Replace `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RouteFIXHandler.java` fully:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Component
public class RouteFIXHandler implements NodeHandler {

    private final CorrelationEngine correlation;
    private final VariableResolver resolver;

    public RouteFIXHandler(CorrelationEngine correlation, VariableResolver resolver) {
        this.correlation = correlation;
        this.resolver = resolver;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.ROUTE_FIX; }

    @Override
    @SuppressWarnings("unchecked")
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) cfg.getOrDefault("rules", List.of());

        List<CorrelationEngine.RoutingRule> rules = new ArrayList<>();
        for (Map<String, Object> r : rawRules) {
            String ruleId       = Objects.toString(r.get("ruleId"), UUID.randomUUID().toString());
            String label        = Objects.toString(r.getOrDefault("label", ""), "");
            String targetNodeId = Objects.toString(r.get("targetNodeId"), "");
            Map<Integer, String> matchers = new LinkedHashMap<>();
            Object matchersObj = r.get("matchers");
            if (matchersObj instanceof Map<?,?> mm) {
                for (Map.Entry<?,?> e : mm.entrySet()) {
                    String resolved = resolver.resolveAll(e.getValue().toString(), ctx);
                    matchers.put(Integer.parseInt(e.getKey().toString()), resolved);
                }
            }
            rules.add(new CorrelationEngine.RoutingRule(ruleId, label, matchers, targetNodeId));
        }

        String execId = ctx.executionId().toString() + ":route:" + node.id();
        CompletableFuture<CorrelationEngine.RoutedResult> future = correlation.registerMulti(execId, rules);

        long timeoutMs = node.timeout() == null ? 30_000L : node.timeout().toMillis();

        try {
            CorrelationEngine.RoutedResult result =
                    future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            ctx.storeNodeMessage(node.id(), result.fields());
            ctx.setVariable("node:" + node.id() + ":matchedRuleId", result.matchedRuleId());
            String matchedLabel = rules.stream()
                    .filter(rl -> rl.ruleId().equals(result.matchedRuleId()))
                    .map(CorrelationEngine.RoutingRule::label)
                    .findFirst().orElse(result.matchedRuleId());
            ctx.setVariable("node:" + node.id() + ":matchedRuleLabel", matchedLabel);
            return NodeHandlerResult.success(result.targetNodeId());
        } catch (TimeoutException timeout) {
            correlation.cancelMulti(execId);
            return onTimeout(node);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            correlation.cancelMulti(execId);
            throw ie;
        } catch (Exception other) {
            correlation.cancelMulti(execId);
            return NodeHandlerResult.failure(node.onFailure(), other.getMessage());
        }
    }

    private NodeHandlerResult onTimeout(ScenarioNode node) {
        TimeoutAction action = node.timeout() == null ? TimeoutAction.FAIL : node.timeout().onTimeout();
        return switch (action) {
            case FAIL     -> NodeHandlerResult.failure(node.onFailure(), "timeout");
            case CONTINUE -> NodeHandlerResult.success(node.onSuccess());
            case RETRY    -> NodeHandlerResult.failure(node.onFailure(), "timeout-retry-exhausted");
            case JUMP     -> NodeHandlerResult.success(node.timeout().jumpTo());
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
~/maven/bin/mvn test -pl fix-flow-engine -Dtest=RouteFIXHandlerTest -q 2>&1 | tail -20
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Run full test suite**

```bash
~/maven/bin/mvn test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RouteFIXHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RouteFIXHandlerTest.java
git commit -m "feat(engine): resolve placeholders in ROUTE_FIX matcher values; store matched rule label"
```

---

### Task 2: ExecutionManager — emit matched rule in NODE_EXITED; persist ROUTE_FIX message

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionManager.java:102-114`

- [ ] **Step 1: Locate the message-persistence block and the NODE_EXITED emit**

The relevant section is `ExecutionManager.java:102-114`:

```java
if (current.type() == NodeType.SEND_FIX) {
    persistMessage(ctx, current.id(), Direction.OUTBOUND);
} else if (current.type() == NodeType.EXPECT_FIX && result.success()) {
    persistMessage(ctx, current.id(), Direction.INBOUND);
}

if (result.success()) {
    emitAndPersist(ctx.executionId(), ExecutionEventType.NODE_EXITED, current.id(),
            "Node " + current.name() + " completed");
} else {
    emitAndPersist(...);
}
```

- [ ] **Step 2: Apply both changes**

Replace that block with:

```java
if (current.type() == NodeType.SEND_FIX) {
    persistMessage(ctx, current.id(), Direction.OUTBOUND);
} else if ((current.type() == NodeType.EXPECT_FIX || current.type() == NodeType.ROUTE_FIX) && result.success()) {
    persistMessage(ctx, current.id(), Direction.INBOUND);
}

if (result.success()) {
    String exitDetail = "Node " + current.name() + " completed";
    if (current.type() == NodeType.ROUTE_FIX) {
        String matchedLabel = ctx.getVariable("node:" + current.id() + ":matchedRuleLabel");
        if (matchedLabel != null) exitDetail = "Routed via rule: " + matchedLabel;
    }
    emitAndPersist(ctx.executionId(), ExecutionEventType.NODE_EXITED, current.id(), exitDetail);
} else {
    emitAndPersist(ctx.executionId(), ExecutionEventType.ERROR, current.id(),
            result.errorMessage() != null ? result.errorMessage() : "Node failed");
}
```

- [ ] **Step 3: Run full test suite**

```bash
~/maven/bin/mvn test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionManager.java
git commit -m "feat(engine): emit matched rule in NODE_EXITED detail; persist ROUTE_FIX inbound message"
```

---

### Task 3: CorrelationEngine multi-rule tests

**Files:**
- Modify: `fix-flow-engine/src/test/java/com/fixflow/engine/correlation/CorrelationEngineTest.java`

- [ ] **Step 1: Add multi-rule tests**

Append these three test methods inside `CorrelationEngineTest`:

```java
@Test
void multiRuleRoutesToFirstMatchingRule() throws Exception {
    CorrelationEngine engine = new CorrelationEngine();
    CorrelationEngine.RoutingRule quoteRule = new CorrelationEngine.RoutingRule("r1", "Quote", Map.of(35, "S"), "quote-node");
    CorrelationEngine.RoutingRule rejectRule = new CorrelationEngine.RoutingRule("r2", "Reject", Map.of(35, "AG"), "reject-node");

    CompletableFuture<CorrelationEngine.RoutedResult> f =
            engine.registerMulti("exec-1", List.of(quoteRule, rejectRule));

    engine.onMessage("sess", Map.of(35, "S", 131, "RFQ-001"));

    CorrelationEngine.RoutedResult result = f.get(500, TimeUnit.MILLISECONDS);
    assertThat(result.matchedRuleId()).isEqualTo("r1");
    assertThat(result.targetNodeId()).isEqualTo("quote-node");
    assertThat(result.fields()).containsEntry(35, "S");
}

@Test
void multiRuleDefaultRuleMatchesWhenNoSpecificRuleFires() throws Exception {
    CorrelationEngine engine = new CorrelationEngine();
    CorrelationEngine.RoutingRule quoteRule = new CorrelationEngine.RoutingRule("r1", "Quote", Map.of(35, "S"), "quote-node");
    CorrelationEngine.RoutingRule defaultRule = new CorrelationEngine.RoutingRule("r2", "Default", Map.of(), "default-node");

    CompletableFuture<CorrelationEngine.RoutedResult> f =
            engine.registerMulti("exec-1", List.of(quoteRule, defaultRule));

    engine.onMessage("sess", Map.of(35, "8", 39, "2"));

    CorrelationEngine.RoutedResult result = f.get(500, TimeUnit.MILLISECONDS);
    assertThat(result.matchedRuleId()).isEqualTo("r2");
    assertThat(result.targetNodeId()).isEqualTo("default-node");
}

@Test
void multiRuleCompoundAndConditionRequiresAllTagsToMatch() throws Exception {
    CorrelationEngine engine = new CorrelationEngine();
    // Rule: 35=8 AND 39=8 → rejected execution
    CorrelationEngine.RoutingRule rejectedRule = new CorrelationEngine.RoutingRule(
            "r1", "Rejected", Map.of(35, "8", 39, "8"), "rejected-node");

    CompletableFuture<CorrelationEngine.RoutedResult> f =
            engine.registerMulti("exec-1", List.of(rejectedRule));

    // Message matches 35=8 but NOT 39=8 — should not match
    boolean matched = engine.onMessage("sess", Map.of(35, "8", 39, "2"));
    assertThat(matched).isFalse();

    // Re-register since no multi-waiter is pending after the failed match attempt
    // (the waiter was not removed since match was false)
    engine.onMessage("sess", Map.of(35, "8", 39, "8"));  // now full match

    CorrelationEngine.RoutedResult result = f.get(500, TimeUnit.MILLISECONDS);
    assertThat(result.matchedRuleId()).isEqualTo("r1");
    assertThat(result.targetNodeId()).isEqualTo("rejected-node");
}
```

- [ ] **Step 2: Run test to verify it passes**

```bash
~/maven/bin/mvn test -pl fix-flow-engine -Dtest=CorrelationEngineTest -q 2>&1 | tail -20
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 3: Run full suite**

```bash
~/maven/bin/mvn test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add fix-flow-engine/src/test/java/com/fixflow/engine/correlation/CorrelationEngineTest.java
git commit -m "test(engine): add multi-rule routing tests to CorrelationEngineTest"
```

---

### Task 4: RouteFIXConfig UX — banner, hints, VarRefPanel

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/RouteFIXConfig.tsx`

- [ ] **Step 1: Replace RouteFIXConfig.tsx with improved version**

Full replacement of `fix-flow-ui/src/panels/right/NodeConfig/RouteFIXConfig.tsx`:

```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';
import { VarRefPanel } from './VarRefPanel';

interface MatcherRow { tag: number; value: string; }
interface RoutingRule { ruleId: string; label: string; matchers: MatcherRow[]; targetNodeId: string; }
interface RouteCfg { rules?: RoutingRule[]; }
interface Props { node: ScenarioNode; }

export function RouteFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RouteCfg) ?? {};
  const rules: RoutingRule[] = cfg.rules ?? [];

  const patchRules = (next: RoutingRule[]) =>
    updateNode(node.id, { config: { ...cfg, rules: next } });

  const addRule = () =>
    patchRules([...rules, { ruleId: crypto.randomUUID(), label: '', matchers: [], targetNodeId: '' }]);

  const removeRule = (i: number) => patchRules(rules.filter((_, idx) => idx !== i));

  const updateRule = (i: number, patch: Partial<RoutingRule>) =>
    patchRules(rules.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  const addMatcher = (i: number) =>
    updateRule(i, { matchers: [...rules[i].matchers, { tag: 0, value: '' }] });

  const removeMatcher = (ruleIdx: number, matcherIdx: number) =>
    updateRule(ruleIdx, { matchers: rules[ruleIdx].matchers.filter((_, idx) => idx !== matcherIdx) });

  const updateMatcher = (ruleIdx: number, matcherIdx: number, patch: Partial<MatcherRow>) =>
    updateRule(ruleIdx, {
      matchers: rules[ruleIdx].matchers.map((m, idx) => (idx === matcherIdx ? { ...m, ...patch } : m)),
    });

  const otherNodes = allNodes.filter((n) => n.id !== node.id);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-pink-300 bg-pink-900/20 border border-pink-800/40 rounded px-2 py-1.5">
        Waits for an inbound FIX message and routes to the first rule whose matchers all match.
        Rules are evaluated top-to-bottom. A rule with no matchers acts as default/fallback.
      </div>

      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-[10px] text-gray-500">
            Routing Rules
            <span title="Each rule is tested in order. The first rule where all matchers match is selected. A rule with zero matchers is a catch-all default. The matched rule's target node becomes the next step." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button className="text-[10px] px-2 py-0.5 bg-pink-700 hover:bg-pink-600 rounded" onClick={addRule}>+ Rule</button>
        </div>
        <div className="space-y-2">
          {rules.map((r, i) => (
            <div key={r.ruleId} className="border border-[#2a2d3a] rounded p-2 space-y-1">
              <div className="flex items-center justify-between">
                <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 mr-1"
                  placeholder="Rule label (e.g. Quote, Reject, Default)"
                  value={r.label} onChange={(e) => updateRule(i, { label: e.target.value })} />
                <button className="text-red-400 hover:text-red-300" onClick={() => removeRule(i)}>x</button>
              </div>
              <div>
                <div className="text-[10px] text-gray-500 flex items-center justify-between">
                  <span>
                    Matchers
                    <span title="FIX tag-value pairs that must ALL match the incoming message (AND logic). Leave empty to make this rule a default/fallback that catches any message. Value supports {{node:id:tagN}} placeholders." className="ml-1 text-gray-600 cursor-help">?</span>
                    {r.matchers.length === 0 && (
                      <span className="ml-1 text-pink-400 font-medium">(default)</span>
                    )}
                  </span>
                  <button className="text-[10px] px-1 bg-blue-700 hover:bg-blue-600 rounded" onClick={() => addMatcher(i)}>+ Tag</button>
                </div>
                {r.matchers.map((m, j) => (
                  <div key={j} className="flex gap-1 mt-0.5">
                    <input type="number" className="w-14 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                      placeholder="Tag" value={m.tag}
                      onChange={(e) => updateMatcher(i, j, { tag: Number(e.target.value) })} />
                    <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                      placeholder="Value or {{node:id:tagN}}"
                      value={m.value} onChange={(e) => updateMatcher(i, j, { value: e.target.value })} />
                    <button className="text-red-400 hover:text-red-300" onClick={() => removeMatcher(i, j)}>x</button>
                  </div>
                ))}
              </div>
              <div>
                <label className="text-[10px] text-gray-500">
                  Target Node
                  <span title="The node to execute when this rule matches. Draw an edge from this ROUTE_FIX block to the target node on the canvas to visualise the branch." className="ml-1 text-gray-600 cursor-help">?</span>
                </label>
                <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                  value={r.targetNodeId}
                  onChange={(e) => updateRule(i, { targetNodeId: e.target.value })}>
                  <option value="">— select —</option>
                  {otherNodes.map((n) => (
                    <option key={n.id} value={n.id}>{n.name} ({n.type})</option>
                  ))}
                </select>
              </div>
            </div>
          ))}
          {rules.length === 0 && (
            <div className="text-[10px] text-gray-600 italic">No rules yet. Add rules to route incoming FIX messages.</div>
          )}
        </div>
      </div>

      <VarRefPanel />

      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
```

- [ ] **Step 2: Start dev server and verify visually**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator/fix-flow-ui && npm run dev &
```

Open `http://localhost:5173`. Drop a ROUTE_FIX block on the canvas, select it, verify in right panel:
- Pink banner text visible
- `?` tooltip on "Routing Rules" label
- `+ Rule` button adds rule card with label input and `?` on Matchers
- Empty matchers shows "(default)" tag
- Value field placeholder says "Value or {{node:id:tagN}}"
- VarRefPanel appears below rules
- TimeoutConfig appears at bottom

- [ ] **Step 3: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/RouteFIXConfig.tsx
git commit -m "ux: add banner, hints and VarRefPanel to RouteFIXConfig"
```

---

### Task 5: DecisionConfig UX — banner, hints, VarRefPanel

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/DecisionConfig.tsx`

- [ ] **Step 1: Replace DecisionConfig.tsx with improved version**

Full replacement of `fix-flow-ui/src/panels/right/NodeConfig/DecisionConfig.tsx`:

```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { VarRefPanel } from './VarRefPanel';

interface DecisionCfg { condition?: string; }
interface Props { node: ScenarioNode; }

export function DecisionConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as DecisionCfg) ?? {};

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-yellow-300 bg-yellow-900/20 border border-yellow-800/40 rounded px-2 py-1.5">
        Evaluates a condition expression. True → success path (onSuccess). False → failure path (onFailure).
      </div>

      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <div>
        <label className="text-[10px] text-gray-500">
          Condition
          <span title="Expression: LEFT OP RIGHT. Operators: == (exact match), != (not equal), contains (substring). Use {{node:id:tagN}} to reference FIX fields from previous nodes. Example: {{node:expect-er:tag39}} == 0" className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono"
          placeholder='{{node:expect-er:tag39}} == "0"'
          value={cfg.condition ?? ''}
          onChange={(e) => updateNode(node.id, { config: { ...cfg, condition: e.target.value } })} />
        <div className="text-[10px] text-gray-600 mt-0.5">Operators: <code>==</code> <code>!=</code> <code>contains</code></div>
      </div>

      <VarRefPanel />
    </div>
  );
}
```

- [ ] **Step 2: Verify visually in dev server**

Select a DECISION block on the canvas:
- Yellow banner visible
- `?` tooltip on Condition label shows full explanation
- Placeholder in input shows example
- VarRefPanel appears below

- [ ] **Step 3: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/DecisionConfig.tsx
git commit -m "ux: add banner, hints and VarRefPanel to DecisionConfig"
```

---

### Task 6: FlowCanvas — use rule label as edge label for ROUTE_FIX connections

**Files:**
- Modify: `fix-flow-ui/src/canvas/FlowCanvas.tsx:184-188`

- [ ] **Step 1: Locate onConnect in FlowCanvas.tsx**

Current code at lines 184-188:

```typescript
const onConnect = useCallback(
  (conn: Connection) => {
    const edge = { from: conn.source, to: conn.target, label: 'success' };
    addEdge(conn.sourceHandle ? { ...edge, sourceHandle: conn.sourceHandle } : edge);
  },
```

- [ ] **Step 2: Replace with rule-label-aware version**

Replace only the `onConnect` callback body (lines 184-189):

```typescript
const onConnect = useCallback(
  (conn: Connection) => {
    let label = 'success';
    if (conn.sourceHandle && conn.sourceHandle !== 'default') {
      const sourceRfNode = rfNodes.find((n) => n.id === conn.source);
      const cfg = sourceRfNode?.data?.config as
        { rules?: Array<{ ruleId: string; label: string }> } | undefined;
      const rule = cfg?.rules?.find((r) => r.ruleId === conn.sourceHandle);
      if (rule?.label) label = rule.label;
    }
    const edge = { from: conn.source!, to: conn.target!, label };
    addEdge(conn.sourceHandle ? { ...edge, sourceHandle: conn.sourceHandle } : edge);
  },
```

- [ ] **Step 3: Verify visually**

1. Add a ROUTE_FIX node; add two rules: "Quote" (tag 35 = S) and "Reject" (tag 35 = AG).
2. Draw an edge from the left handle (Quote rule) to an END_PASS node.
3. The edge label on canvas should read "Quote" (not "success").
4. Draw an edge from the right handle (Reject rule) to an END_FAIL node.
5. The edge label should read "Reject".

- [ ] **Step 4: Run TypeScript check**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator/fix-flow-ui && npm run build 2>&1 | tail -20
```

Expected: `built in ...ms` with no TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/canvas/FlowCanvas.tsx
git commit -m "ux: use routing rule label as edge label for ROUTE_FIX connections"
```

---

### Task 7: DSL docs — add ROUTE_FIX and DECISION sections

**Files:**
- Modify: `docs/dsl-reference.md`

- [ ] **Step 1: Add ROUTE_FIX to node type table and add config section**

In the node types table, update the DECISION row and add ROUTE_FIX:

Replace the existing table row:
```
| `DECISION` | Branch based on previous result. |
```
with:
```
| `DECISION` | Evaluate a condition expression; branch on true/false. |
| `ROUTE_FIX` | Wait for inbound FIX; route to first matching rule. |
```

Then add two new sections after the VALIDATE config section. Insert after line 99 (`toleranceUnit: SECONDS`):

```markdown
## DECISION config

```yaml
config:
  condition: '{{node:expect-er:tag39}} == "2"'
  # Operators: == != contains
  # Left and right sides support {{...}} placeholders.
  # True  → onSuccess path
  # False → onFailure path
```

## ROUTE_FIX config

```yaml
config:
  rules:
    - ruleId: r1
      label: Quote
      matchers:
        35: S            # tag 35 must equal "S"
        131: "{{node:send-rfq:tag131}}"  # tag 131 must match outbound value
      targetNodeId: process-quote

    - ruleId: r2
      label: Reject
      matchers:
        35: AG
      targetNodeId: handle-reject

    - ruleId: r3
      label: Default
      matchers: {}       # empty = catch-all default
      targetNodeId: unexpected-msg
```

Rules are evaluated top-to-bottom; first match wins. Matcher values support `{{node:id:tagN}}` placeholders resolved at execution time. After routing, the matched rule label appears in the `NODE_EXITED` event detail.
```

- [ ] **Step 2: Verify docs render correctly**

```bash
grep -n "ROUTE_FIX\|DECISION" docs/dsl-reference.md
```

Expected: entries in node type table AND config section headings found.

- [ ] **Step 3: Commit**

```bash
git add docs/dsl-reference.md
git commit -m "docs: add ROUTE_FIX and DECISION config sections to DSL reference"
```

---

## Self-review

### Spec coverage

| Requirement | Task |
|-------------|------|
| Multiple possible incoming FIX patterns | Already existed; Task 1 fixes placeholder gap |
| Priority/order evaluation | Already existed (first-match); Task 3 tests it |
| Optional fallback/default branch | Already existed; Task 4 labels it visually; Task 3 tests it |
| Matching against static values | Task 1 (unchanged, was working) |
| Matching against previous node values (placeholders) | Task 1 (fix) |
| Compound conditions AND | Already existed; Task 3 tests it |
| Visual branches in editor | Already existed (multi-handle); Task 6 adds rule labels on edges |
| GUI usable for non-expert FIX users | Tasks 4 + 5 (banners, hints) |
| Real-time debug: which rule matched | Task 2 (NODE_EXITED detail) |
| Received message in message log | Task 2 (persistMessage for ROUTE_FIX) |
| DSL documentation | Task 7 |

### Placeholder scan: none found.

### Type consistency

- `RoutingRule.label()` used in Task 1 (store label in ctx) — matches `CorrelationEngine.RoutingRule` record field `label` defined in existing code.
- `ctx.getVariable("node:{id}:matchedRuleLabel")` in Task 2 — stored by Task 1.
- `conn.sourceHandle` used in Task 6 — exists on ReactFlow `Connection` type.
- `rfNodes` used in Task 6 — local state in `FlowCanvas.tsx`, already in scope.
