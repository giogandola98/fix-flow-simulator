# FX Lifecycle Templates + FIX Repeating Groups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add FIX repeating group support end-to-end (engine, adapter, graphical editor) and ship five importable venue-side FIX 5.0 SP2 scenario templates covering the full order lifecycle for FX spot, deliverable forward, NDF, swap and vanilla option.

**Architecture:** A recursive `FIXMessageData` record (top-level fields + `counterTag -> entries` groups) becomes the message currency across ports, correlation, handlers and the QuickFIX/J adapter. The existing flat `Map<Integer,String>` survives as a projection, so every scenario already saved keeps working. The UI gains a reusable `FieldTable` plus a recursive `GroupEditor` so groups are fully editable, not just round-tripped.

**Tech Stack:** Java 21, Spring Boot 3.3.2, QuickFIX/J 2.3.1 (FIX50SP2 + FIXT11 dictionaries already on the classpath), Maven multi-module; React 18, TypeScript 5.4, Vite 5, Zustand, TanStack Query, vitest 2 + @testing-library/react, js-yaml.

**Spec:** `docs/superpowers/specs/2026-08-24-fx-lifecycle-templates-design.md`

## Global Constraints

- Wire protocol for all templates: **FIX 5.0 SP2** over a FIXT.1.1 session, `DefaultApplVerID=9`.
- Simulator role in all templates: **venue / sell-side**. Scenarios wait for an inbound order, then reply. They never originate an order.
- Session-managed tags `8, 9, 10, 34, 49, 52, 56` are never written by a scenario. `SendFIXHandler` filters them; the UI flags them with a yellow border.
- Repeating group counter tags are **never written by hand**. `quickfix.Message.addGroup()` maintains the counter; the UI shows it read-only, derived from entry count.
- The **first field of a group entry is the group delimiter tag**. Entry field order is significant and must be preserved through YAML, store and adapter (`LinkedHashMap`, not `HashMap`).
- Backward compatibility is a hard requirement: every existing test must pass unmodified.
- Branch: `feat/fx-lifecycle-templates`. Never commit to `master` (see `CLAUDE.md`).
- SecurityType / CFI values are fixed by the spec, section 9. Copy them verbatim:
  - FX Spot `167=FXSPOT`, `461=IFXXXP`
  - FX Forward deliverable `167=FXFWD`, `461=JFTXFP`
  - NDF `167=FXNDF`, `461=JFTXFN`
  - FX Swap `167=FXSWAP`, `461=SFAXXP`
  - FX Vanilla Option `167=OPT`, `460=4`, `461=HFRAVP` (European call, physical) / `HFRDVP` (European put)
- Do **not** use `DATE_RULE` validation rules in templates. `ValidateHandler.toConfig` reads the key `dateRule` and expects `dateRules` as a `Map`, while `docs/dsl-reference.md` documents `dateRuleId` and a list. That pre-existing mismatch is out of scope here; templates use `EQUALS`, `NOT_EQUALS`, `ENUM`, `REGEX`, `NUMERIC_MIN`, `NUMERIC_MAX`, `FIELD_PRESENT`, `FIELD_ABSENT` only.

- The five FX templates live **outside this repository**, in the surrounding
  project folder (`<project>/fx-templates/`, sibling to the `fix-flow-simulator`
  checkout). They are the user's instrument content, not part of the simulator,
  and are never committed to this repo. Only the engine and UI changes are
  committed here.

## Prerequisites

The machine running this plan needs **JDK 21, Maven 3.9+ and Node.js 20+**. Verify before Task 1:

```bash
java -version    # expect 21.x
mvn -v           # or ~/maven/bin/mvn per CLAUDE.md
node -v          # expect 20.x
```

On the Windows workstation used for this plan none of the three were installed,
so a portable toolchain was downloaded into the session scratchpad. Export these
before running any build command there:

```powershell
$TC   = 'C:\Users\giorgio\AppData\Local\Temp\claude\C--Users-giorgio-Desktop-fix-simulator\1f083371-7538-49f8-85b5-56ed6d53134a\scratchpad\toolchain'
$env:JAVA_HOME = "$TC\jdk-21.0.12.1+1"
$env:M2_HOME   = "$TC\apache-maven-3.9.9"
$env:Path      = "$env:JAVA_HOME\bin;$env:M2_HOME\bin;$TC\node-v20.18.1-win-x64;$env:Path"
```

Verified: OpenJDK 21.0.12.1 LTS, Apache Maven 3.9.9, Node v20.18.1.

Build and test commands used throughout (substitute `~/maven/bin/mvn` for `mvn` on the machine described in `CLAUDE.md`):

```bash
mvn -q test                                  # all Java modules
mvn -q -pl fix-flow-core test                # single module
cd fix-flow-ui && npm test                   # vitest run, all UI tests
cd fix-flow-ui && npx vitest run src/lib/scenarioSerializer.test.ts   # single UI file
```

---

## Task 1: `FIXMessageData` core record

**Files:**
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/FIXMessageData.java`
- Test: `fix-flow-core/src/test/java/com/fixflow/core/domain/execution/FIXMessageDataTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `FIXMessageData(Map<Integer,String> fields, Map<Integer,List<FIXMessageData>> groups)` with `static FIXMessageData ofFields(Map<Integer,String>)`, `Map<Integer,String> flatFields()`, `List<FIXMessageData> group(int counterTag)`, `Optional<String> groupValue(int counterTag, int index, int tag)`, `boolean hasGroups()`. Every later task depends on these exact names.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.core.domain.execution;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FIXMessageDataTest {

    @Test
    void ofFieldsCreatesMessageWithNoGroups() {
        FIXMessageData m = FIXMessageData.ofFields(Map.of(35, "D", 11, "ORD-1"));
        assertEquals("D", m.flatFields().get(35));
        assertTrue(m.groups().isEmpty());
        assertFalse(m.hasGroups());
    }

    @Test
    void nullArgumentsBecomeEmptyMaps() {
        FIXMessageData m = new FIXMessageData(null, null);
        assertTrue(m.fields().isEmpty());
        assertTrue(m.groups().isEmpty());
    }

    @Test
    void fieldOrderIsPreserved() {
        Map<Integer, String> in = new LinkedHashMap<>();
        in.put(600, "EUR/USD");
        in.put(624, "1");
        in.put(587, "0");
        FIXMessageData m = FIXMessageData.ofFields(in);
        assertIterableEquals(List.of(600, 624, 587), m.fields().keySet());
    }

    @Test
    void groupsAreAccessibleByCounterTagAndIndex() {
        FIXMessageData near = FIXMessageData.ofFields(new LinkedHashMap<>(Map.of(600, "EUR/USD")));
        FIXMessageData far  = FIXMessageData.ofFields(new LinkedHashMap<>(Map.of(600, "EUR/GBP")));
        FIXMessageData m = new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(near, far)));

        assertEquals(2, m.group(555).size());
        assertTrue(m.hasGroups());
        assertEquals("EUR/USD", m.groupValue(555, 0, 600).orElseThrow());
        assertEquals("EUR/GBP", m.groupValue(555, 1, 600).orElseThrow());
        assertTrue(m.groupValue(555, 2, 600).isEmpty());
        assertTrue(m.groupValue(999, 0, 600).isEmpty());
        assertTrue(m.groupValue(555, 0, 9999).isEmpty());
    }

    @Test
    void nestedGroupsAreSupported() {
        FIXMessageData inner = FIXMessageData.ofFields(Map.of(865, "1"));
        FIXMessageData leg = new FIXMessageData(Map.of(600, "EUR/USD"), Map.of(864, List.of(inner)));
        FIXMessageData m = new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(leg)));
        assertEquals("1", m.group(555).get(0).groupValue(864, 0, 865).orElseThrow());
    }

    @Test
    void defensiveCopyPreventsExternalMutation() {
        Map<Integer, String> mutable = new LinkedHashMap<>();
        mutable.put(35, "D");
        FIXMessageData m = FIXMessageData.ofFields(mutable);
        mutable.put(11, "LATE");
        assertNull(m.fields().get(11));
        assertThrows(UnsupportedOperationException.class, () -> m.fields().put(99, "x"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-core test -Dtest=FIXMessageDataTest`
Expected: FAIL — compilation error, `FIXMessageData` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.fixflow.core.domain.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A FIX message as carried inside the engine: top-level fields plus repeating groups,
 * keyed by their counter tag (555 NoLegs, 864 NoEvents, ...). Recursive, so nested
 * groups are representable.
 *
 * <p>Field order is significant and preserved: the <em>first</em> field of a group entry
 * is the group delimiter tag, which QuickFIX/J needs to build a {@code quickfix.Group}.
 */
public record FIXMessageData(
        Map<Integer, String> fields,
        Map<Integer, List<FIXMessageData>> groups
) {

    public FIXMessageData {
        fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));

        Map<Integer, List<FIXMessageData>> copied = new LinkedHashMap<>();
        if (groups != null) {
            groups.forEach((counterTag, entries) ->
                    copied.put(counterTag, entries == null ? List.of() : List.copyOf(entries)));
        }
        groups = Collections.unmodifiableMap(copied);
    }

    public static FIXMessageData ofFields(Map<Integer, String> fields) {
        return new FIXMessageData(fields, Map.of());
    }

    /** Top-level fields only — the projection every pre-existing consumer still sees. */
    public Map<Integer, String> flatFields() {
        return fields;
    }

    public List<FIXMessageData> group(int counterTag) {
        return groups.getOrDefault(counterTag, List.of());
    }

    public Optional<String> groupValue(int counterTag, int index, int tag) {
        List<FIXMessageData> entries = group(counterTag);
        if (index < 0 || index >= entries.size()) return Optional.empty();
        return Optional.ofNullable(entries.get(index).fields().get(tag));
    }

    public boolean hasGroups() {
        return !groups.isEmpty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl fix-flow-core test -Dtest=FIXMessageDataTest`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-core/src/main/java/com/fixflow/core/domain/execution/FIXMessageData.java \
        fix-flow-core/src/test/java/com/fixflow/core/domain/execution/FIXMessageDataTest.java
git commit -m "feat(core): add FIXMessageData record for repeating group support"
```

---

## Task 2: Port overloads and `ExecutionContext` group storage

**Files:**
- Modify: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/FIXSessionPort.java`
- Modify: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/InboundMessageListener.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionContext.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/execution/ExecutionContextGroupTest.java`

**Interfaces:**
- Consumes: `FIXMessageData` from Task 1.
- Produces: `FIXSessionPort.sendMessage(UUID, FIXMessageData)`; `InboundMessageListener.onMessage(String, FIXMessageData)`; `ExecutionContext.storeNodeMessage(String, FIXMessageData)` and `ExecutionContext.getNodeMessageData(String)`. The pre-existing `Map`-based signatures remain as `default` / overloads so no caller breaks.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextGroupTest {

    private ExecutionContext ctx() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref",
                null, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    @Test
    void storingFlatMapStillReadableAsFlatMap() {
        ExecutionContext c = ctx();
        c.storeNodeMessage("n1", Map.of(35, "D", 11, "ORD-1"));
        assertEquals("ORD-1", c.getNodeMessage("n1").get(11));
        assertTrue(c.getNodeMessageData("n1").groups().isEmpty());
    }

    @Test
    void storingMessageDataExposesBothViews() {
        ExecutionContext c = ctx();
        FIXMessageData leg = FIXMessageData.ofFields(Map.of(600, "EUR/USD"));
        c.storeNodeMessage("n1", new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(leg))));

        assertEquals("AB", c.getNodeMessage("n1").get(35));
        assertEquals("EUR/USD", c.getNodeMessageData("n1").groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void unknownNodeReturnsNullForBothViews() {
        ExecutionContext c = ctx();
        assertNull(c.getNodeMessage("nope"));
        assertNull(c.getNodeMessageData("nope"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-engine test -Dtest=ExecutionContextGroupTest`
Expected: FAIL — `getNodeMessageData` does not exist.

- [ ] **Step 3: Write minimal implementation**

`FIXSessionPort.java` — replace the `sendMessage` declaration:

```java
package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.session.FIXSessionConfig;

import java.util.Map;
import java.util.UUID;

public interface FIXSessionPort {
    void connect(FIXSessionConfig config);
    void disconnect(UUID sessionId);

    void sendMessage(UUID sessionId, FIXMessageData message);

    /** Legacy no-group form. Kept so existing callers and fakes compile unchanged. */
    default void sendMessage(UUID sessionId, Map<Integer, String> fields) {
        sendMessage(sessionId, FIXMessageData.ofFields(fields));
    }

    boolean isConnected(UUID sessionId);
    void setInboundListener(InboundMessageListener listener);
}
```

`InboundMessageListener.java` — note it can no longer be `@FunctionalInterface`:

```java
package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.FIXMessageData;

import java.util.Map;

public interface InboundMessageListener {

    void onMessage(String sessionId, FIXMessageData message);

    /** Legacy no-group form, for tests and callers that only have a flat map. */
    default void onMessage(String sessionId, Map<Integer, String> fields) {
        onMessage(sessionId, FIXMessageData.ofFields(fields));
    }
}
```

`ExecutionContext.java` — swap the store type and add the second accessor:

```java
    private final Map<String, FIXMessageData> nodeMessages = new ConcurrentHashMap<>();

    public void storeNodeMessage(String nodeId, FIXMessageData message) {
        nodeMessages.put(nodeId, message);
    }

    public void storeNodeMessage(String nodeId, Map<Integer, String> fields) {
        nodeMessages.put(nodeId, FIXMessageData.ofFields(fields));
    }

    /** Top-level fields of the message stored for {@code nodeId}, or null if none. */
    public Map<Integer, String> getNodeMessage(String nodeId) {
        FIXMessageData m = nodeMessages.get(nodeId);
        return m == null ? null : m.flatFields();
    }

    /** Full message including repeating groups, or null if none. */
    public FIXMessageData getNodeMessageData(String nodeId) {
        return nodeMessages.get(nodeId);
    }
```

Add `import com.fixflow.core.domain.execution.FIXMessageData;` to `ExecutionContext.java`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test`
Expected: PASS across all modules. This full run is the backward-compatibility gate — if any pre-existing test fails, the `default` overloads are wrong; fix them rather than editing the old test.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/ \
        fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionContext.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/execution/ExecutionContextGroupTest.java
git commit -m "feat(core): carry FIXMessageData through session port, inbound listener and execution context"
```

---

## Task 3: `SEND_FIX` builds repeating groups from config

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/SendFIXHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/SendFIXHandlerGroupTest.java`

**Interfaces:**
- Consumes: `FIXMessageData` (Task 1), `FIXSessionPort.sendMessage(UUID, FIXMessageData)` and `ExecutionContext.storeNodeMessage(String, FIXMessageData)` (Task 2).
- Produces: the `groups: [{ counterTag, entries: [{ fields: [...], groups: [...] }] }]` DSL shape, resolved recursively with `VariableResolver`. Templates in Tasks 16-20 rely on it.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SendFIXHandlerGroupTest {

    private final List<FIXMessageData> sent = new ArrayList<>();

    private final FIXSessionPort port = new FIXSessionPort() {
        public void connect(FIXSessionConfig config) {}
        public void disconnect(UUID sessionId) {}
        public void sendMessage(UUID sessionId, FIXMessageData message) { sent.add(message); }
        public boolean isConnected(UUID sessionId) { return true; }
        public void setInboundListener(InboundMessageListener listener) {}
    };

    private ExecutionContext ctx() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref",
                null, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        return new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());
    }

    private ScenarioNode node(Map<String, Object> config) {
        return new ScenarioNode("send", "Send", NodeType.SEND_FIX, config, null, null, "next", null, null);
    }

    private Map<String, Object> field(int tag, String value) {
        return Map.of("tag", tag, "value", value);
    }

    @Test
    void emitsTwoLegEntriesInOrder() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("fields", List.of(field(11, "ORD-1")));
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(
                        Map.of("fields", List.of(field(600, "EUR/USD"), field(624, "1"))),
                        Map.of("fields", List.of(field(600, "EUR/USD"), field(624, "2")))
                ))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        FIXMessageData m = sent.get(0);
        assertEquals("AB", m.flatFields().get(35));
        assertEquals("ORD-1", m.flatFields().get(11));
        assertEquals(2, m.group(555).size());
        assertEquals("1", m.groupValue(555, 0, 624).orElseThrow());
        assertEquals("2", m.groupValue(555, 1, 624).orElseThrow());
        assertIterableEquals(List.of(600, 624), m.group(555).get(0).fields().keySet());
    }

    @Test
    void counterTagIsNeverWrittenAsAPlainField() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(600, "EUR/USD")))))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertNull(sent.get(0).flatFields().get(555));
    }

    @Test
    void placeholdersAreResolvedInsideGroupEntries() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(654, "{{seq:leg}}")))))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertEquals("1", sent.get(0).groupValue(555, 0, 654).orElseThrow());
    }

    @Test
    void sessionTagsAreFilteredInsideGroupEntriesToo() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("fields", List.of(field(49, "SENDER"), field(11, "ORD-1")));
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(52, "x"), field(600, "EUR/USD")))))));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertNull(sent.get(0).flatFields().get(49));
        assertNull(sent.get(0).groupValue(555, 0, 52).orElse(null));
        assertEquals("EUR/USD", sent.get(0).groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void emptyEntryListDropsTheGroup() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "D");
        cfg.put("groups", List.of(Map.of("counterTag", 555, "entries", List.of())));

        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), ctx());

        assertTrue(sent.get(0).groups().isEmpty());
    }

    @Test
    void storedNodeMessageKeepsGroupsForLaterPlaceholders() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("msgType", "AB");
        cfg.put("groups", List.of(Map.of(
                "counterTag", 555,
                "entries", List.of(Map.of("fields", List.of(field(600, "EUR/USD")))))));

        ExecutionContext c = ctx();
        new SendFIXHandler(port, new VariableResolver()).handle(node(cfg), c);

        assertEquals("EUR/USD", c.getNodeMessageData("send").groupValue(555, 0, 600).orElseThrow());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-engine test -Dtest=SendFIXHandlerGroupTest`
Expected: FAIL — groups ignored, `m.group(555)` is empty.

- [ ] **Step 3: Write minimal implementation**

Replace the body of `SendFIXHandler.java`:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SendFIXHandler implements NodeHandler {

    private static final Set<Integer> SESSION_TAGS = Set.of(8, 9, 10, 34, 49, 52, 56);

    private final FIXSessionPort port;
    private final VariableResolver variableResolver;

    public SendFIXHandler(FIXSessionPort port, VariableResolver variableResolver) {
        this.port = port;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType getSupportedType() { return NodeType.SEND_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        Map<String, Object> cfg = node.config();

        Map<Integer, String> outFields = resolveFields(cfg.get("fields"), ctx);

        Object msgType = cfg.get("msgType");
        if (msgType != null) {
            Map<Integer, String> withType = new LinkedHashMap<>();
            withType.put(35, variableResolver.resolveAll(String.valueOf(msgType), ctx));
            withType.putAll(outFields);
            outFields = withType;
        }

        FIXMessageData message = new FIXMessageData(outFields, resolveGroups(cfg.get("groups"), ctx));

        port.sendMessage(ctx.sessionId(), message);
        ctx.storeNodeMessage(node.id(), message);
        return NodeHandlerResult.success(node.onSuccess());
    }

    /** Accepts both the map form ({tag: value}) and the list form ([{tag, value}]). */
    private Map<Integer, String> resolveFields(Object fields, ExecutionContext ctx) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (fields instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                put(out, String.valueOf(e.getKey()), e.getValue(), ctx);
            }
        } else if (fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    Object tagObj = row.get("tag");
                    Object valObj = row.get("value");
                    if (tagObj != null && valObj != null) {
                        put(out, String.valueOf(tagObj), valObj, ctx);
                    }
                }
            }
        }
        return out;
    }

    private void put(Map<Integer, String> out, String rawTag, Object value, ExecutionContext ctx) {
        int tag = Integer.parseInt(rawTag.trim());
        if (SESSION_TAGS.contains(tag)) return;
        out.put(tag, variableResolver.resolveAll(String.valueOf(value), ctx));
    }

    private Map<Integer, List<FIXMessageData>> resolveGroups(Object groups, ExecutionContext ctx) {
        Map<Integer, List<FIXMessageData>> out = new LinkedHashMap<>();
        if (!(groups instanceof List<?> list)) return out;

        for (Object g : list) {
            if (!(g instanceof Map<?, ?> group)) continue;
            Object counterObj = group.get("counterTag");
            if (counterObj == null) continue;
            int counterTag = Integer.parseInt(String.valueOf(counterObj).trim());

            List<FIXMessageData> entries = new ArrayList<>();
            if (group.get("entries") instanceof List<?> rawEntries) {
                for (Object e : rawEntries) {
                    if (!(e instanceof Map<?, ?> entry)) continue;
                    Map<Integer, String> entryFields = resolveFields(entry.get("fields"), ctx);
                    if (entryFields.isEmpty()) continue;
                    entries.add(new FIXMessageData(entryFields, resolveGroups(entry.get("groups"), ctx)));
                }
            }
            // An empty group is dropped: QuickFIX/J would otherwise emit a zero counter.
            if (!entries.isEmpty()) out.put(counterTag, entries);
        }
        return out;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl fix-flow-engine test`
Expected: PASS — `SendFIXHandlerGroupTest` (6 tests) plus every pre-existing engine test.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/SendFIXHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/SendFIXHandlerGroupTest.java
git commit -m "feat(engine): build repeating groups from SEND_FIX config"
```

---

## Task 4: QuickFIX/J outbound group serialisation

**Files:**
- Modify: `fix-flow-adapters/src/main/java/com/fixflow/adapters/quickfixj/QuickFIXAdapter.java:101-114`
- Test: `fix-flow-adapters/src/test/java/com/fixflow/adapters/quickfixj/QuickFIXMessageBuilderTest.java`

**Interfaces:**
- Consumes: `FIXMessageData` (Task 1), `FIXSessionPort.sendMessage(UUID, FIXMessageData)` (Task 2).
- Produces: `static quickfix.Message QuickFIXAdapter.buildMessage(FIXMessageData)` — package-visible, so the builder is testable without a live session.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;
import quickfix.Message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuickFIXMessageBuilderTest {

    private Map<Integer, String> ordered(Object... pairs) {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((Integer) pairs[i], (String) pairs[i + 1]);
        return m;
    }

    @Test
    void msgTypeGoesToTheHeader() throws Exception {
        Message msg = QuickFIXAdapter.buildMessage(
                FIXMessageData.ofFields(ordered(35, "D", 11, "ORD-1")));
        assertEquals("D", msg.getHeader().getString(35));
        assertEquals("ORD-1", msg.getString(11));
    }

    @Test
    void twoLegGroupSerialisesWithCounterAndBothEntries() throws Exception {
        FIXMessageData near = FIXMessageData.ofFields(ordered(600, "EUR/USD", 624, "1", 587, "0"));
        FIXMessageData far  = FIXMessageData.ofFields(ordered(600, "EUR/USD", 624, "2", 587, "6"));
        FIXMessageData data = new FIXMessageData(ordered(35, "AB", 11, "ORD-1"), Map.of(555, List.of(near, far)));

        Message msg = QuickFIXAdapter.buildMessage(data);
        String raw = msg.toString().replace('', '|');

        assertEquals(2, msg.getGroupCount(555), "NoLegs counter must be maintained by addGroup");
        assertTrue(raw.contains("555=2|"), raw);
        assertTrue(raw.contains("600=EUR/USD|624=1|587=0|"), raw);
        assertTrue(raw.contains("600=EUR/USD|624=2|587=6|"), raw);
    }

    @Test
    void nestedGroupIsSerialisedInsideItsParentEntry() throws Exception {
        FIXMessageData event = FIXMessageData.ofFields(ordered(865, "13", 866, "20260826"));
        FIXMessageData leg = new FIXMessageData(ordered(600, "EUR/USD"), Map.of(864, List.of(event)));
        FIXMessageData data = new FIXMessageData(ordered(35, "AB"), Map.of(555, List.of(leg)));

        String raw = QuickFIXAdapter.buildMessage(data).toString().replace('', '|');

        assertTrue(raw.contains("864=1|"), raw);
        assertTrue(raw.contains("865=13|"), raw);
    }

    @Test
    void messageWithNoGroupsIsUnchangedFromLegacyBehaviour() throws Exception {
        Message msg = QuickFIXAdapter.buildMessage(
                FIXMessageData.ofFields(ordered(35, "8", 150, "0", 39, "0")));
        assertEquals("0", msg.getString(150));
        assertEquals(0, msg.getGroupCount(555));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-adapters test -Dtest=QuickFIXMessageBuilderTest`
Expected: FAIL — `buildMessage` does not exist.

- [ ] **Step 3: Write minimal implementation**

In `QuickFIXAdapter.java`, replace `sendMessage` and add the builder. Add imports `com.fixflow.core.domain.execution.FIXMessageData`, `quickfix.FieldMap`, `quickfix.Group`, `java.util.List`.

```java
    @Override
    public void sendMessage(UUID sessionId, FIXMessageData message) {
        SessionID sid = sessions.get(sessionId);
        if (sid == null) throw new IllegalStateException("Unknown session: " + sessionId);
        try {
            Session.sendToTarget(buildMessage(message), sid);
        } catch (SessionNotFound e) {
            throw new IllegalStateException("Session not found: " + sessionId, e);
        }
    }

    /**
     * Builds a QuickFIX/J message from engine data. Tag 35 goes to the header; every repeating
     * group entry becomes a {@link Group} whose delimiter is the entry's first field, so entry
     * field order matters. The counter tag is written by {@code addGroup}, never by hand.
     */
    static Message buildMessage(FIXMessageData data) {
        Message msg = new Message();
        data.fields().forEach((tag, value) -> {
            if (tag == 35) msg.getHeader().setString(35, value);
            else msg.setString(tag, value);
        });
        applyGroups(msg, data);
        return msg;
    }

    private static void applyGroups(FieldMap target, FIXMessageData data) {
        data.groups().forEach((counterTag, entries) -> {
            for (FIXMessageData entry : entries) {
                if (entry.fields().isEmpty()) continue;
                int delimiterTag = entry.fields().keySet().iterator().next();
                Group group = new Group(counterTag, delimiterTag);
                entry.fields().forEach(group::setString);
                applyGroups(group, entry);
                target.addGroup(group);
            }
        });
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl fix-flow-adapters test`
Expected: PASS — 4 new tests plus every pre-existing adapter test.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-adapters/src/main/java/com/fixflow/adapters/quickfixj/QuickFIXAdapter.java \
        fix-flow-adapters/src/test/java/com/fixflow/adapters/quickfixj/QuickFIXMessageBuilderTest.java
git commit -m "feat(adapters): serialise repeating groups on outbound FIX messages"
```

---

## Task 5: QuickFIX/J inbound group extraction

**Files:**
- Modify: `fix-flow-adapters/src/main/java/com/fixflow/adapters/quickfixj/QuickFIXApplicationAdapter.java:65-80`
- Test: `fix-flow-adapters/src/test/java/com/fixflow/adapters/quickfixj/QuickFIXInboundExtractionTest.java`

**Interfaces:**
- Consumes: `FIXMessageData` (Task 1), `InboundMessageListener.onMessage(String, FIXMessageData)` (Task 2).
- Produces: `static FIXMessageData QuickFIXApplicationAdapter.extractMessage(quickfix.Message)` — package-visible for testing.

**Context the implementer needs:** `AppDataDictionary=FIX50SP2.xml` is already configured for FIXT.1.1 sessions in `QuickFIXAdapter.buildSettings`, and `quickfixj-messages-fix50sp2` is on the classpath, so QuickFIX/J parses inbound groups into real `quickfix.Group` objects. `ValidateIncomingMessage=N` disables *validation*, not group *parsing*. `FieldMap.groupKeyIterator()` yields the counter tags present.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;
import quickfix.Group;
import quickfix.Message;

import static org.junit.jupiter.api.Assertions.*;

class QuickFIXInboundExtractionTest {

    private Group leg(String symbol, String side) {
        Group g = new Group(555, 600);
        g.setString(600, symbol);
        g.setString(624, side);
        return g;
    }

    @Test
    void topLevelFieldsAreExtracted() {
        Message msg = new Message();
        msg.getHeader().setString(35, "D");
        msg.setString(11, "ORD-1");
        msg.setString(55, "EUR/USD");

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertEquals("D", data.flatFields().get(35));
        assertEquals("ORD-1", data.flatFields().get(11));
        assertFalse(data.hasGroups());
    }

    @Test
    void repeatingGroupEntriesAreExtractedInOrder() {
        Message msg = new Message();
        msg.getHeader().setString(35, "AB");
        msg.setString(11, "ORD-1");
        msg.addGroup(leg("EUR/USD", "1"));
        msg.addGroup(leg("EUR/USD", "2"));

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertEquals(2, data.group(555).size());
        assertEquals("1", data.groupValue(555, 0, 624).orElseThrow());
        assertEquals("2", data.groupValue(555, 1, 624).orElseThrow());
        assertEquals("EUR/USD", data.groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void counterTagIsNotLeakedIntoTopLevelFields() {
        Message msg = new Message();
        msg.getHeader().setString(35, "AB");
        msg.addGroup(leg("EUR/USD", "1"));

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertNull(data.flatFields().get(555),
                "the NoLegs counter is structure, not a field the scenario should match on");
    }

    @Test
    void nestedGroupsAreExtracted() {
        Group event = new Group(864, 865);
        event.setString(865, "13");
        event.setString(866, "20260826");
        Group legWithEvent = leg("EUR/USD", "1");
        legWithEvent.addGroup(event);

        Message msg = new Message();
        msg.getHeader().setString(35, "AB");
        msg.addGroup(legWithEvent);

        FIXMessageData data = QuickFIXApplicationAdapter.extractMessage(msg);

        assertEquals("13", data.group(555).get(0).groupValue(864, 0, 865).orElseThrow());
    }

    @Test
    void roundTripsThroughTheOutboundBuilder() {
        FIXMessageData original = new FIXMessageData(
                java.util.Map.of(35, "AB"),
                java.util.Map.of(555, java.util.List.of(
                        FIXMessageData.ofFields(new java.util.LinkedHashMap<>(java.util.Map.of(600, "EUR/USD"))))));

        FIXMessageData back = QuickFIXApplicationAdapter.extractMessage(
                QuickFIXAdapter.buildMessage(original));

        assertEquals("EUR/USD", back.groupValue(555, 0, 600).orElseThrow());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-adapters test -Dtest=QuickFIXInboundExtractionTest`
Expected: FAIL — `extractMessage` does not exist.

- [ ] **Step 3: Write minimal implementation**

In `QuickFIXApplicationAdapter.java`, replace `fromApp` and `extractFields`. Add imports `com.fixflow.core.domain.execution.FIXMessageData`, `quickfix.FieldMap`, `quickfix.Group`, `java.util.ArrayList`, `java.util.LinkedHashMap`, `java.util.List`.

```java
    @Override
    public void fromApp(Message message, SessionID sessionId) {
        FIXMessageData data = extractMessage(message);
        UUID uuid = sessionUUIDs.get(sessionId);
        String sessionKey = uuid != null ? uuid.toString() : sessionId.toString();
        listener.onMessage(sessionKey, data);
    }

    /**
     * Converts a QuickFIX/J message into engine data. Header and trailer fields join the
     * top-level map; every repeating group becomes an ordered list of entries, recursively.
     * Group counter tags are deliberately left out of the flat map — they describe structure,
     * not content, and a scenario matching on them would be matching on an implementation detail.
     */
    static FIXMessageData extractMessage(Message message) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        Map<Integer, List<FIXMessageData>> groups = new LinkedHashMap<>();

        copyFields(message.getHeader().iterator(), fields);
        collect(message, fields, groups);
        copyFields(message.getTrailer().iterator(), fields);

        return new FIXMessageData(fields, groups);
    }

    private static void collect(FieldMap map,
                                Map<Integer, String> fields,
                                Map<Integer, List<FIXMessageData>> groups) {
        Iterator<Integer> counterTags = map.groupKeyIterator();
        Set<Integer> counters = new HashSet<>();
        while (counterTags.hasNext()) counters.add(counterTags.next());

        Iterator<Field<?>> it = map.iterator();
        while (it.hasNext()) {
            Field<?> f = it.next();
            if (counters.contains(f.getTag())) continue;   // structural counter, skip
            fields.put(f.getTag(), String.valueOf(f.getObject()));
        }

        for (Integer counterTag : counters) {
            List<FIXMessageData> entries = new ArrayList<>();
            for (Group g : map.getGroups(counterTag)) {
                Map<Integer, String> entryFields = new LinkedHashMap<>();
                Map<Integer, List<FIXMessageData>> entryGroups = new LinkedHashMap<>();
                collect(g, entryFields, entryGroups);
                entries.add(new FIXMessageData(entryFields, entryGroups));
            }
            if (!entries.isEmpty()) groups.put(counterTag, entries);
        }
    }
```

Keep the existing `copyFields(Iterator<Field<?>>, Map<Integer,String>)` helper, but make it `static`. Add `java.util.HashSet` and `java.util.Set` imports.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl fix-flow-adapters test`
Expected: PASS — 5 new tests plus every pre-existing adapter test.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-adapters/src/main/java/com/fixflow/adapters/quickfixj/QuickFIXApplicationAdapter.java \
        fix-flow-adapters/src/test/java/com/fixflow/adapters/quickfixj/QuickFIXInboundExtractionTest.java
git commit -m "feat(adapters): extract repeating groups from inbound FIX messages"
```

---

## Task 6: Carry `FIXMessageData` through router, buffer, correlation and the waiting handlers

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/MessageRouter.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/MessageBuffer.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/correlation/CorrelationEngine.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ExpectFIXHandler.java:78-80`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RouteFIXHandler.java:74-76`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/GroupPropagationTest.java`

**Interfaces:**
- Consumes: `FIXMessageData` (Task 1), `ExecutionContext.storeNodeMessage(String, FIXMessageData)` (Task 2).
- Produces: `CorrelationEngine.register(...)` returns `CompletableFuture<FIXMessageData>`; `CorrelationEngine.RoutedResult(FIXMessageData message, String matchedRuleId, String targetNodeId)`; `MessageBuffer.park(String, FIXMessageData)` and `poll(String, Predicate<FIXMessageData>)`. Matching logic keeps reading `message.flatFields()`, so routing and correlation behaviour is unchanged.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GroupPropagationTest {

    private FIXMessageData multileg() {
        FIXMessageData near = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "1"));
        FIXMessageData far  = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "2"));
        return new FIXMessageData(Map.of(35, "AB", 11, "ORD-1"), Map.of(555, List.of(near, far)));
    }

    @Test
    void correlationDeliversGroupsToTheWaiter() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CompletableFuture<FIXMessageData> future =
                engine.register("exec-1", "sess-1", new CorrelationRule(11, "n", 11, 0), "ORD-1");

        assertTrue(engine.onMessage("sess-1", multileg()));

        FIXMessageData received = future.get(1, TimeUnit.SECONDS);
        assertEquals("2", received.groupValue(555, 1, 624).orElseThrow());
    }

    @Test
    void multiRouteMatchesOnFlatFieldsAndDeliversGroups() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CompletableFuture<CorrelationEngine.RoutedResult> future = engine.registerMulti(
                "exec-2", "sess-1",
                List.of(new CorrelationEngine.RoutingRule("r1", "Multileg", Map.of(35, "AB"), "handle-ab")));

        assertTrue(engine.onMessage("sess-1", multileg()));

        CorrelationEngine.RoutedResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals("handle-ab", result.targetNodeId());
        assertEquals("EUR/USD", result.message().groupValue(555, 0, 600).orElseThrow());
    }

    @Test
    void bufferParksAndReplaysGroupsIntact() {
        MessageBuffer buffer = new MessageBuffer();
        buffer.park("sess-1", multileg());

        var polled = buffer.poll("sess-1", m -> "AB".equals(m.flatFields().get(35)));

        assertTrue(polled.isPresent());
        assertEquals("1", polled.get().groupValue(555, 0, 624).orElseThrow());
    }

    @Test
    void routerParksUnmatchedMessagesThenDrainsThem() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer();
        MessageRouter router = new MessageRouter(engine, buffer);

        router.onMessage("sess-1", multileg());          // nobody waiting yet
        assertEquals(1, buffer.size("sess-1"));

        CompletableFuture<FIXMessageData> future =
                engine.register("exec-3", "sess-1", new CorrelationRule(11, "n", 11, 0), "ORD-1");
        router.drain("sess-1");

        assertEquals("EUR/USD", future.get(1, TimeUnit.SECONDS).groupValue(555, 0, 600).orElseThrow());
        assertEquals(0, buffer.size("sess-1"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-engine test -Dtest=GroupPropagationTest`
Expected: FAIL — compilation error, `register` returns `CompletableFuture<Map<Integer,String>>` and `RoutedResult` has no `message()`.

- [ ] **Step 3: Write minimal implementation**

`CorrelationEngine.java` — substitute the type in the four records and in `onMessage`:

```java
    public record CorrelationWaiter(
            String executionId,
            String sessionId,
            CorrelationRule rule,
            String expectedValue,
            CompletableFuture<FIXMessageData> future) {}

    public record RoutedResult(FIXMessageData message, String matchedRuleId, String targetNodeId) {}

    public record MultiRouteWaiter(
            String executionId,
            String sessionId,
            List<RoutingRule> rules,
            CompletableFuture<RoutedResult> future) {}

    public CompletableFuture<FIXMessageData> register(String executionId,
                                                      String sessionId,
                                                      CorrelationRule rule,
                                                      String expectedValue) {
        CompletableFuture<FIXMessageData> future = new CompletableFuture<>();
        CorrelationWaiter waiter = new CorrelationWaiter(executionId, sessionId, rule, expectedValue, future);
        if (waiters.putIfAbsent(executionId, waiter) != null) {
            throw new IllegalStateException("duplicate executionId: " + executionId);
        }
        return future;
    }

    public boolean onMessage(String sessionId, FIXMessageData message) {
        Map<Integer, String> fields = message.flatFields();
        for (CorrelationWaiter w : waiters.values()) {
            if (!w.sessionId().equals(sessionId)) continue;
            String actual = fields.get(w.rule().sourceTag());
            if (actual != null && actual.equals(w.expectedValue())) {
                waiters.remove(w.executionId());
                w.future().complete(message);
                return true;
            }
        }
        for (MultiRouteWaiter w : multiWaiters.values()) {
            if (!w.sessionId().equals(sessionId)) continue;
            RoutingRule matched = null;
            RoutingRule defaultRule = null;
            for (RoutingRule rule : w.rules()) {
                if (rule.matchers().isEmpty()) { defaultRule = rule; continue; }
                boolean allMatch = rule.matchers().entrySet().stream()
                        .allMatch(e -> e.getValue().equals(fields.get(e.getKey())));
                if (allMatch) { matched = rule; break; }
            }
            if (matched == null) matched = defaultRule;
            if (matched != null) {
                multiWaiters.remove(w.executionId());
                w.future().complete(new RoutedResult(message, matched.ruleId(), matched.targetNodeId()));
                return true;
            }
        }
        return false;
    }

    /** Legacy flat-map entry point, kept for existing tests. */
    public boolean onMessage(String sessionId, Map<Integer, String> fields) {
        return onMessage(sessionId, FIXMessageData.ofFields(fields));
    }
```

Add `import com.fixflow.core.domain.execution.FIXMessageData;`.

`MessageBuffer.java` — substitute the type:

```java
    public record BufferedMessage(FIXMessageData message, Instant parkedAt) {}

    public void park(String sessionId, FIXMessageData message) {
        Deque<BufferedMessage> deque =
                buffers.computeIfAbsent(sessionId, k -> new ConcurrentLinkedDeque<>());
        deque.addFirst(new BufferedMessage(message, Instant.now()));
        while (deque.size() > capacity) deque.pollLast();
    }

    public Optional<FIXMessageData> poll(String sessionId, Predicate<FIXMessageData> matcher) {
        Deque<BufferedMessage> deque = buffers.get(sessionId);
        if (deque == null) return Optional.empty();

        Instant now = Instant.now();
        Iterator<BufferedMessage> it = deque.iterator();
        while (it.hasNext()) {
            BufferedMessage m = it.next();
            if (now.toEpochMilli() - m.parkedAt().toEpochMilli() > ttlMs) {
                it.remove();
                continue;
            }
            if (matcher.test(m.message())) {
                it.remove();
                return Optional.of(m.message());
            }
        }
        return Optional.empty();
    }
```

`FIXMessageData` is already immutable, so the previous `Map.copyOf(fields)` defensive copy in `park` is no longer needed. Add `import com.fixflow.core.domain.execution.FIXMessageData;` and drop the now-unused `java.util.Map` import if the compiler flags it.

`MessageRouter.java`:

```java
    @Override
    public void onMessage(String sessionId, FIXMessageData message) {
        if (buffer.isPaused()) {
            buffer.park(sessionId, message);
            return;
        }
        boolean consumed = correlation.onMessage(sessionId, message);
        if (!consumed) buffer.park(sessionId, message);
    }

    public void drain(String sessionId) {
        Optional<FIXMessageData> next;
        do {
            next = buffer.poll(sessionId, message -> correlation.onMessage(sessionId, message));
        } while (next.isPresent());
    }
```

`ExpectFIXHandler.java` — the future type changes; store the whole message:

```java
            CompletableFuture<FIXMessageData> future =
                    correlation.register(ctx.executionId().toString(), sessionIdStr, rule, expectedValue);
            if (ctx.sessionId() != null) router.drain(ctx.sessionId().toString());

            long timeoutMs = node.timeout() == null ? 5_000L : node.timeout().toMillis();

            FIXMessageData received = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            ctx.storeNodeMessage(node.id(), received);
            return NodeHandlerResult.success(node.onSuccess());
```

`RouteFIXHandler.java` — one line changes:

```java
            ctx.storeNodeMessage(node.id(), result.message());
```

Add `import com.fixflow.core.domain.execution.FIXMessageData;` to both handlers.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test`
Expected: PASS across all modules. Existing `CorrelationEngineTest`, `MessageBufferTest` and `MessageRouterTest` must pass untouched via the legacy `onMessage(String, Map)` overload; if one fails to compile because it calls `poll` with a `Predicate<Map<...>>`, adapt the *test's lambda parameter type only* — never weaken the production type.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/fix/ \
        fix-flow-engine/src/main/java/com/fixflow/engine/correlation/CorrelationEngine.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ExpectFIXHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RouteFIXHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/fix/GroupPropagationTest.java
git commit -m "feat(engine): propagate repeating groups through router, buffer and correlation"
```

---

## Task 7: `{{node:id:gNNN.i:tagM}}` placeholder

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolver.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/variable/GroupFieldPluginTest.java`

**Interfaces:**
- Consumes: `ExecutionContext.getNodeMessageData(String)` (Task 2).
- Produces: two placeholder forms usable in any `SEND_FIX` value, `ROUTE_FIX` matcher or `DECISION` condition:
  - `{{node:<nodeId>:g<counterTag>.<index>:tag<N>}}`
  - `{{node:<nodeId>:g<counterTag>.<index>:tag<N>:offset:+2d}}`

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.engine.variable;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GroupFieldPluginTest {

    private final VariableResolver resolver = new VariableResolver();

    private ExecutionContext ctxWithLegs() {
        Scenario s = new Scenario(UUID.randomUUID(), "s", "d", "1", "ref",
                null, List.of(), List.of(), List.of(), List.of(), Map.of(), null);
        ExecutionContext c = new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());

        FIXMessageData near = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "1", 588, "2026-08-26T00:00:00Z"));
        FIXMessageData far  = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 624, "2"));
        c.storeNodeMessage("order", new FIXMessageData(Map.of(35, "AB"), Map.of(555, List.of(near, far))));
        return c;
    }

    @Test
    void resolvesFieldFromFirstGroupEntry() {
        assertEquals("EUR/USD", resolver.resolveAll("{{node:order:g555.0:tag600}}", ctxWithLegs()));
    }

    @Test
    void resolvesFieldFromSecondGroupEntry() {
        assertEquals("2", resolver.resolveAll("{{node:order:g555.1:tag624}}", ctxWithLegs()));
    }

    @Test
    void interpolatesInsideALargerString() {
        assertEquals("leg=EUR/USD side=1",
                resolver.resolveAll("leg={{node:order:g555.0:tag600}} side={{node:order:g555.0:tag624}}", ctxWithLegs()));
    }

    @Test
    void missingTagResolvesToEmptyString() {
        assertEquals("", resolver.resolveAll("{{node:order:g555.0:tag9999}}", ctxWithLegs()));
    }

    @Test
    void outOfRangeIndexFailsLoudlyWithContext() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolveAll("{{node:order:g555.5:tag600}}", ctxWithLegs()));
        assertTrue(ex.getMessage().contains("555"));
        assertTrue(ex.getMessage().contains("order"));
    }

    @Test
    void unknownNodeFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolveAll("{{node:nope:g555.0:tag600}}", ctxWithLegs()));
    }

    @Test
    void offsetVariantShiftsTheDate() {
        assertEquals("2026-08-28T00:00:00Z",
                resolver.resolveAll("{{node:order:g555.0:tag588:offset:+2d}}", ctxWithLegs()));
    }

    @Test
    void plainNodeTagPlaceholderStillWorks() {
        assertEquals("AB", resolver.resolveAll("{{node:order:tag35}}", ctxWithLegs()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-engine test -Dtest=GroupFieldPluginTest`
Expected: FAIL — `IllegalArgumentException: No plugin handles expression: node:order:g555.0:tag600`.

- [ ] **Step 3: Write minimal implementation**

In `VariableResolver.java`, register the two new plugins **before** `DateOffsetPlugin` and `NodeFieldPlugin` in the constructor list:

```java
        this.plugins = List.of(
            new NowPlugin(),
            new NowOffsetPlugin(),
            new NowDateOffsetPlugin(),
            new NowDatePlugin(),
            new UuidPlugin(),
            new SeqPlugin(sequences),
            new EnvPlugin(),
            new GroupFieldOffsetPlugin(),
            new GroupFieldPlugin(),
            new DateOffsetPlugin(),
            new NodeFieldPlugin(),
            new VarPlugin()
        );
```

Add the two plugin classes at the end of `VariableResolver`, next to `NodeFieldPlugin`:

```java
    private static FIXMessageData requireMessage(ExecutionContext c, String nodeId) {
        FIXMessageData data = c.getNodeMessageData(nodeId);
        if (data == null) throw new IllegalStateException("No stored message for node: " + nodeId);
        return data;
    }

    private static String requireGroupValue(ExecutionContext c, String nodeId,
                                            int counterTag, int index, int tag) {
        FIXMessageData data = requireMessage(c, nodeId);
        if (index < 0 || index >= data.group(counterTag).size()) {
            throw new IllegalStateException(
                "Group " + counterTag + " on node " + nodeId + " has "
                + data.group(counterTag).size() + " entries; index " + index + " is out of range");
        }
        return data.groupValue(counterTag, index, tag).orElse("");
    }

    static final class GroupFieldPlugin implements VariableResolverPlugin {
        private static final Pattern P =
            Pattern.compile("^node:([^:]+):g(\\d+)\\.(\\d+):tag(\\d+)$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad group ref: " + e);
            return requireGroupValue(c, m.group(1),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        }
    }

    static final class GroupFieldOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P =
            Pattern.compile("^node:([^:]+):g(\\d+)\\.(\\d+):tag(\\d+):offset:([+\\-])(\\d+)([smhd])$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad group date offset: " + e);
            String raw = requireGroupValue(c, m.group(1),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            Instant base = Instant.parse(raw);
            long amount = Long.parseLong(m.group(6));
            ChronoUnit cu = switch (m.group(7)) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default  -> throw new IllegalArgumentException("Bad unit: " + m.group(7));
            };
            return (m.group(5).equals("+") ? base.plus(amount, cu) : base.minus(amount, cu)).toString();
        }
    }
```

Add `import com.fixflow.core.domain.execution.FIXMessageData;`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl fix-flow-engine test`
Expected: PASS — 8 new tests plus the existing `VariableResolverTest`.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolver.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/variable/GroupFieldPluginTest.java
git commit -m "feat(engine): add group-aware node field placeholders"
```

---

## Task 8: Group-aware `VALIDATE` rules

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRuleConfig.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationEngine.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ValidateHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/GroupValidationTest.java`

**Interfaces:**
- Consumes: `FIXMessageData` (Task 1), `ExecutionContext.getNodeMessageData(String)` (Task 2).
- Produces: `ValidationRuleConfig` gains `Integer groupTag` and `String index` (nullable, appended after `numericValue`); `ValidationEngine.validate(ValidationConfig, FIXMessageData, ExecutionContext, Instant)`. The swap template (Task 19) depends on this.

**DSL shape produced:**
```yaml
rules:
  - { tag: 609, groupTag: 555, index: 0,   rule: EQUALS, value: FXSPOT }
  - { tag: 600, groupTag: 555, index: '*', rule: FIELD_PRESENT }
  - { tag: 55,  rule: EQUALS, value: EUR/USD }     # no groupTag -> top level, as today
```

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.engine.validation;

import com.fixflow.core.domain.execution.FIXMessageData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroupValidationTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());

    private FIXMessageData swap() {
        FIXMessageData near = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 609, "FXSPOT"));
        FIXMessageData far  = FIXMessageData.ofFields(Map.of(600, "EUR/USD", 609, "FXFWD"));
        return new FIXMessageData(Map.of(35, "AB", 55, "EUR/USD"), Map.of(555, List.of(near, far)));
    }

    private ValidationRuleConfig rule(int tag, Integer groupTag, String index, String type, String value) {
        return new ValidationRuleConfig(tag, type, value, null, null, null, null, 0, groupTag, index);
    }

    private ValidationConfig cfg(ValidationRuleConfig... rules) {
        return new ValidationConfig(List.of(rules), Map.of(), false);
    }

    @Test
    void validatesEachLegByIndex() {
        ValidationSummary s = engine.validate(cfg(
                rule(609, 555, "0", "EQUALS", "FXSPOT"),
                rule(609, 555, "1", "EQUALS", "FXFWD")), swap(), null, Instant.now());
        assertTrue(s.passed());
    }

    @Test
    void failsWhenALegDoesNotMatch() {
        ValidationSummary s = engine.validate(cfg(
                rule(609, 555, "1", "EQUALS", "FXSPOT")), swap(), null, Instant.now());
        assertFalse(s.passed());
    }

    @Test
    void wildcardIndexAppliesToEveryEntry() {
        ValidationSummary pass = engine.validate(cfg(
                rule(600, 555, "*", "EQUALS", "EUR/USD")), swap(), null, Instant.now());
        assertTrue(pass.passed());

        ValidationSummary fail = engine.validate(cfg(
                rule(609, 555, "*", "EQUALS", "FXSPOT")), swap(), null, Instant.now());
        assertFalse(fail.passed(), "far leg is FXFWD, so a wildcard EQUALS FXSPOT must fail");
    }

    @Test
    void ruleWithoutGroupTagStillValidatesTopLevel() {
        ValidationSummary s = engine.validate(cfg(
                rule(55, null, null, "EQUALS", "EUR/USD")), swap(), null, Instant.now());
        assertTrue(s.passed());
    }

    @Test
    void missingGroupFailsRatherThanPassingVacuously() {
        ValidationSummary s = engine.validate(cfg(
                rule(600, 999, "0", "FIELD_PRESENT", null)), swap(), null, Instant.now());
        assertFalse(s.passed());
    }

    @Test
    void strictModeIgnoresGroupEntryFields() {
        ValidationConfig strict = new ValidationConfig(
                List.of(rule(35, null, null, "EQUALS", "AB"), rule(55, null, null, "EQUALS", "EUR/USD")),
                Map.of(), true);
        assertTrue(engine.validate(strict, swap(), null, Instant.now()).passed(),
                "strict mode checks top-level fields only; group content is structural");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-engine test -Dtest=GroupValidationTest`
Expected: FAIL — compilation error, `ValidationRuleConfig` has 8 components, not 10.

- [ ] **Step 3: Write minimal implementation**

`ValidationRuleConfig.java`:

```java
package com.fixflow.engine.validation;

import java.util.List;

public record ValidationRuleConfig(
    int tag,
    String rule,
    String value,
    List<String> values,
    String ref,
    String dateRule,
    String pattern,
    double numericValue,
    /** Repeating group counter tag, or null to validate a top-level field. */
    Integer groupTag,
    /** Group entry index as a string; "*" means every entry. Null defaults to "0". */
    String index
) {
    /** Legacy 8-arg form used by pre-existing callers and tests. */
    public ValidationRuleConfig(int tag, String rule, String value, List<String> values,
                                String ref, String dateRule, String pattern, double numericValue) {
        this(tag, rule, value, values, ref, dateRule, pattern, numericValue, null, null);
    }
}
```

`ValidationEngine.java` — change the entry point to take `FIXMessageData` and expand group rules:

```java
    public ValidationSummary validate(
        ValidationConfig config,
        FIXMessageData message,
        ExecutionContext ctx,
        Instant receivedAt
    ) {
        Map<Integer, String> topLevel = message.flatFields();
        List<ValidationResult> results = new ArrayList<>();
        Set<Integer> expectedTags = new HashSet<>();

        for (ValidationRuleConfig rc : config.validations()) {
            if (rc.groupTag() == null) {
                expectedTags.add(rc.tag());
                results.add(evaluate(rc, topLevel, config, ctx, receivedAt));
                continue;
            }

            List<FIXMessageData> entries = message.group(rc.groupTag());
            if (entries.isEmpty()) {
                results.add(ValidationResult.fail(rc.tag(), rc.rule(), "group " + rc.groupTag() + " present",
                        "absent", "repeating group " + rc.groupTag() + " not found"));
                continue;
            }

            String idx = rc.index() == null ? "0" : rc.index().trim();
            if ("*".equals(idx)) {
                for (FIXMessageData entry : entries) {
                    results.add(evaluate(rc, entry.flatFields(), config, ctx, receivedAt));
                }
            } else {
                int i = Integer.parseInt(idx);
                if (i < 0 || i >= entries.size()) {
                    results.add(ValidationResult.fail(rc.tag(), rc.rule(),
                            "group " + rc.groupTag() + " entry " + i,
                            entries.size() + " entries", "group entry index out of range"));
                } else {
                    results.add(evaluate(rc, entries.get(i).flatFields(), config, ctx, receivedAt));
                }
            }
        }

        if (config.strictMode()) {
            for (Integer tag : topLevel.keySet()) {
                if (!expectedTags.contains(tag) && !isHeaderTag(tag)) {
                    results.add(ValidationResult.fail(
                        tag, "STRICT", "not present", topLevel.get(tag), "unexpected field"
                    ));
                }
            }
        }

        boolean passed = results.stream().allMatch(ValidationResult::passed);
        return new ValidationSummary(passed, List.copyOf(results));
    }

    /** Legacy flat-map entry point, kept for existing tests and callers. */
    public ValidationSummary validate(ValidationConfig config, Map<Integer, String> fields,
                                      ExecutionContext ctx, Instant receivedAt) {
        return validate(config, FIXMessageData.ofFields(fields), ctx, receivedAt);
    }

    private ValidationResult evaluate(ValidationRuleConfig rc, Map<Integer, String> fields,
                                      ValidationConfig config, ExecutionContext ctx, Instant receivedAt) {
        ValidationRule rule = build(rc, config);
        return rule instanceof DateRuleValidator drv
                ? dateRuleEngine.validate(drv.rule(), rc.tag(), fields, ctx, receivedAt)
                : rule.validate(rc.tag(), fields, ctx);
    }
```

Add `import com.fixflow.core.domain.execution.FIXMessageData;`.

`ValidateHandler.java` — read the full message and the two new keys:

```java
        String sourceId = node.config().get("sourceNodeId") != null
                ? String.valueOf(node.config().get("sourceNodeId")) : null;
        FIXMessageData message = ctx.getNodeMessageData(sourceId != null ? sourceId : node.id());
        if (message == null) message = FIXMessageData.ofFields(Map.of());

        ValidationConfig cfg = toConfig(node.config());
        ValidationSummary summary = engine.validate(cfg, message, ctx, Instant.now());
```

and inside `toConfig`, replace the `rules.add(...)` line:

```java
            Integer groupTag = rr.get("groupTag") == null ? null : ((Number) rr.get("groupTag")).intValue();
            String index = rr.get("index") == null ? null : String.valueOf(rr.get("index"));
            rules.add(new ValidationRuleConfig(tag, rule, value, values, ref, dateRule, pattern, num,
                                               groupTag, index));
```

Add `import com.fixflow.core.domain.execution.FIXMessageData;`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test`
Expected: PASS across all modules — 6 new tests plus every pre-existing validation test via the legacy overloads.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ValidateHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/GroupValidationTest.java
git commit -m "feat(engine): validate fields inside repeating groups"
```

---

## Task 9: Extend the FIX tag dictionary

**Files:**
- Modify: `fix-flow-ui/src/lib/fixTags.ts`
- Modify: `fix-flow-ui/src/lib/fixTags.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `FIX_TAGS` covers every tag the templates use, plus `GROUP_COUNTER_TAGS: Record<number, string>` listing the repeating group counters offered in the editor. Task 11 imports `GROUP_COUNTER_TAGS` for the picker datalist. Task 13 keeps its own `GROUP_DELIMITERS` map in `parseFIXMessage.ts` because it needs counter → *delimiter* pairs, not counter → name; the two tables must stay key-aligned, which the Task 9 test asserts.

- [ ] **Step 1: Write the failing test**

Append to `fix-flow-ui/src/lib/fixTags.test.ts`:

```ts
import { FIX_TAGS, GROUP_COUNTER_TAGS, fixTagName } from './fixTags';

describe('FX and derivative tags', () => {
  it('names the instrument reference block', () => {
    expect(fixTagName(167)).toBe('SecurityType');
    expect(fixTagName(461)).toBe('CFICode');
    expect(fixTagName(460)).toBe('Product');
    expect(fixTagName(762)).toBe('SecuritySubType');
    expect(fixTagName(541)).toBe('MaturityDate');
    expect(fixTagName(207)).toBe('SecurityExchange');
  });

  it('names the leg tags used by the swap template', () => {
    expect(fixTagName(555)).toBe('NoLegs');
    expect(fixTagName(600)).toBe('LegSymbol');
    expect(fixTagName(609)).toBe('LegSecurityType');
    expect(fixTagName(624)).toBe('LegSide');
    expect(fixTagName(588)).toBe('LegSettlDate');
    expect(fixTagName(637)).toBe('LegLastPx');
    expect(fixTagName(1418)).toBe('LegLastQty');
  });

  it('names the option and position maintenance tags', () => {
    expect(fixTagName(201)).toBe('PutOrCall');
    expect(fixTagName(202)).toBe('StrikePrice');
    expect(fixTagName(1194)).toBe('ExerciseStyle');
    expect(fixTagName(1482)).toBe('OptPayoutType');
    expect(fixTagName(709)).toBe('PosTransType');
    expect(fixTagName(722)).toBe('PosMaintStatus');
  });

  it('names the FX settlement and trade capture tags', () => {
    expect(fixTagName(119)).toBe('SettlCurrAmt');
    expect(fixTagName(155)).toBe('SettlCurrFxRate');
    expect(fixTagName(571)).toBe('TradeReportID');
    expect(fixTagName(866)).toBe('EventDate');
  });

  it('exposes the group counter tags offered by the editor', () => {
    expect(GROUP_COUNTER_TAGS[555]).toBe('NoLegs');
    expect(GROUP_COUNTER_TAGS[864]).toBe('NoEvents');
    expect(GROUP_COUNTER_TAGS[702]).toBe('NoPositions');
    Object.keys(GROUP_COUNTER_TAGS).forEach((t) => {
      expect(FIX_TAGS[Number(t)]).toBeDefined();
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/lib/fixTags.test.ts`
Expected: FAIL — `GROUP_COUNTER_TAGS` is not exported and most tags return `null`.

- [ ] **Step 3: Write minimal implementation**

Append to the `FIX_TAGS` object in `fix-flow-ui/src/lib/fixTags.ts` (keep the existing entries; add any that are missing):

```ts
  // Order lifecycle
  41: 'OrigClOrdID', 59: 'TimeInForce', 60: 'TransactTime', 75: 'TradeDate',
  102: 'CxlRejReason', 103: 'OrdRejReason', 126: 'ExpireTime', 150: 'ExecType',
  151: 'LeavesQty', 372: 'RefMsgType', 378: 'ExecRestatementReason',
  432: 'ExpireDate', 434: 'CxlRejResponseTo', 442: 'MultiLegReportingType',
  584: 'MassStatusReqID',

  // Instrument reference block
  22: 'SecurityIDSource', 48: 'SecurityID', 107: 'SecurityDesc',
  167: 'SecurityType', 207: 'SecurityExchange', 460: 'Product',
  461: 'CFICode', 541: 'MaturityDate', 762: 'SecuritySubType',

  // FX settlement
  63: 'SettlType', 64: 'SettlDate', 119: 'SettlCurrAmt', 120: 'SettlCurrency',
  155: 'SettlCurrFxRate', 156: 'SettlCurrFxRateCalc', 193: 'SettlDate2',

  // Options
  200: 'MaturityMonthYear', 201: 'PutOrCall', 202: 'StrikePrice',
  231: 'ContractMultiplier', 947: 'StrikeCurrency', 1193: 'SettlMethod',
  1194: 'ExerciseStyle', 1482: 'OptPayoutType',

  // Legs (NoLegs group)
  555: 'NoLegs', 566: 'LegPrice', 587: 'LegSettlType', 588: 'LegSettlDate',
  600: 'LegSymbol', 608: 'LegCFICode', 609: 'LegSecurityType',
  623: 'LegRatioQty', 624: 'LegSide', 637: 'LegLastPx', 654: 'LegRefID',
  675: 'LegSettlCurrency', 687: 'LegQty', 1418: 'LegLastQty',

  // Parties (NoPartyIDs group)
  447: 'PartyIDSource', 448: 'PartyID', 452: 'PartyRole', 453: 'NoPartyIDs',

  // Events (NoEvents group)
  864: 'NoEvents', 865: 'EventType', 866: 'EventDate',

  // Underlyings (NoUnderlyings group)
  311: 'UnderlyingSymbol', 711: 'NoUnderlyings',

  // Position maintenance
  581: 'AccountType', 702: 'NoPositions', 703: 'PosType', 704: 'LongQty',
  705: 'ShortQty', 709: 'PosTransType', 710: 'PosReqID', 712: 'PosMaintAction',
  715: 'ClearingBusinessDate', 721: 'PosMaintRptID', 722: 'PosMaintStatus',
  723: 'PosMaintResult',

  // Trade capture
  487: 'TradeReportTransType', 571: 'TradeReportID', 828: 'TrdType',
  856: 'TradeReportType', 1003: 'TradeID', 1123: 'TradeReportStatus',

  // Allocations
  78: 'NoAllocs', 79: 'AllocAccount',
```

Then add the counter table at the end of the file:

```ts
/**
 * Repeating group counter tags offered in the SEND_FIX group editor, and used by
 * parseFIXMessage to rebuild group structure from a pasted raw message.
 * Every key must also exist in FIX_TAGS.
 */
export const GROUP_COUNTER_TAGS: Record<number, string> = {
  78: 'NoAllocs',
  453: 'NoPartyIDs',
  555: 'NoLegs',
  702: 'NoPositions',
  711: 'NoUnderlyings',
  864: 'NoEvents',
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd fix-flow-ui && npx vitest run src/lib/fixTags.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/lib/fixTags.ts fix-flow-ui/src/lib/fixTags.test.ts
git commit -m "feat(ui): extend FIX tag dictionary with FX, leg, option and position tags"
```

---

## Task 10: Extract the reusable `FieldTable` component

**Files:**
- Create: `fix-flow-ui/src/panels/right/NodeConfig/FieldTable.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/FieldTable.test.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`

**Interfaces:**
- Consumes: `fixTagName`, `FIX_TAGS` (Task 9), `ENGINE_TAGS` from `parseFIXMessage`.
- Produces:
  ```ts
  export interface FieldRow { tag: number; value: string }
  export interface FieldTableProps {
    fields: FieldRow[];
    onChange: (next: FieldRow[]) => void;
    label?: string;
    idPrefix?: string;   // makes test ids unique when several tables are on screen
  }
  export function FieldTable(props: FieldTableProps): JSX.Element
  ```
  Task 11 renders one `FieldTable` per group entry.

**This task is a pure refactor: `SendFIXConfig` must look and behave exactly as before.** The existing `SendFIXConfig.test.tsx` passing unchanged is the proof.

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { FieldTable } from './FieldTable';

describe('FieldTable', () => {
  it('renders a row per field with its resolved tag name', () => {
    render(<FieldTable fields={[{ tag: 55, value: 'EUR/USD' }, { tag: 167, value: 'FXSPOT' }]}
                       onChange={() => {}} idPrefix="t" />);
    expect(screen.getByText('Symbol')).toBeInTheDocument();
    expect(screen.getByText('SecurityType')).toBeInTheDocument();
    expect(screen.getByDisplayValue('EUR/USD')).toBeInTheDocument();
  });

  it('emits the whole array when a value changes', async () => {
    const onChange = vi.fn();
    render(<FieldTable fields={[{ tag: 55, value: 'EUR' }]} onChange={onChange} idPrefix="t" />);
    await userEvent.type(screen.getByDisplayValue('EUR'), '/USD');
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls.at(-1)![0][0].tag).toBe(55);
  });

  it('adds and removes rows', async () => {
    const onChange = vi.fn();
    render(<FieldTable fields={[{ tag: 55, value: 'EUR/USD' }]} onChange={onChange} idPrefix="t" />);

    await userEvent.click(screen.getByTestId('t-add-field'));
    expect(onChange).toHaveBeenLastCalledWith([{ tag: 55, value: 'EUR/USD' }, { tag: 0, value: '' }]);

    await userEvent.click(screen.getByTestId('t-remove-0'));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it('flags engine-managed session tags', () => {
    render(<FieldTable fields={[{ tag: 52, value: 'x' }]} onChange={() => {}} idPrefix="t" />);
    expect(screen.getByText('engine-managed')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/panels/right/NodeConfig/FieldTable.test.tsx`
Expected: FAIL — module `./FieldTable` not found.

- [ ] **Step 3: Write minimal implementation**

Create `FieldTable.tsx`, lifting the markup currently inline in `SendFIXConfig.tsx`:

```tsx
import { useTranslation } from 'react-i18next';
import { ENGINE_TAGS } from '../../../lib/parseFIXMessage';
import { fixTagName, FIX_TAGS } from '../../../lib/fixTags';

export interface FieldRow { tag: number; value: string }

export interface FieldTableProps {
  fields: FieldRow[];
  onChange: (next: FieldRow[]) => void;
  label?: string;
  idPrefix?: string;
}

export function FieldTable({ fields, onChange, label, idPrefix = 'ft' }: FieldTableProps) {
  const { t } = useTranslation();

  const updateField = (i: number, patch: Partial<FieldRow>) =>
    onChange(fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f)));
  const addField = () => onChange([...fields, { tag: 0, value: '' }]);
  const removeField = (i: number) => onChange(fields.filter((_, idx) => idx !== i));

  return (
    <div>
      <div className="flex items-center justify-between">
        <label className="text-[10px] text-gray-500">
          {label ?? t('nodeConfig.sendFix.fields')}
          <span
            title="FIX tag-value pairs. Value supports placeholders: {{now}}, {{uuid}}, {{seq:name}}, {{env:VAR}}, {{node:id:tagN}}, {{node:id:gNNN.i:tagM}}."
            className="ml-1 text-gray-600 cursor-help"
          >?</span>
        </label>
        <button
          type="button"
          data-testid={`${idPrefix}-add-field`}
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={addField}
        >
          {t('nodeConfig.sendFix.addField')}
        </button>
      </div>
      <datalist id="fix-tag-list">
        {Object.entries(FIX_TAGS).map(([tag, name]) => (
          <option key={tag} value={tag}>{`${tag} — ${name}`}</option>
        ))}
      </datalist>
      <table className="w-full mt-1">
        <thead className="text-[10px] text-gray-500">
          <tr>
            <th className="text-left w-16">{t('nodeConfig.tag')}</th>
            <th className="text-left">{t('nodeConfig.field')}</th>
            <th className="text-left">{t('nodeConfig.value')}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {fields.map((f, i) => {
            const isRestricted = ENGINE_TAGS.has(f.tag);
            const tagName = fixTagName(f.tag);
            return (
              <tr key={i}>
                <td className="pr-1 align-top">
                  <input
                    type="number"
                    list="fix-tag-list"
                    data-testid={`${idPrefix}-tag-${i}`}
                    className={`w-full bg-[#0f1117] border rounded px-1 py-0.5 ${isRestricted ? 'border-yellow-500' : 'border-[#2a2d3a]'}`}
                    value={f.tag}
                    onChange={(e) => updateField(i, { tag: Number(e.target.value) })}
                    title={isRestricted ? `Tag ${f.tag} is session-managed by QuickFIX/J and will be ignored` : undefined}
                  />
                  {isRestricted && (
                    <div className="text-yellow-400 text-[9px] leading-tight mt-0.5">engine-managed</div>
                  )}
                </td>
                <td className="pr-1 align-top">
                  <div className={`px-1 py-0.5 text-[10px] leading-tight ${tagName ? 'text-gray-400' : 'text-gray-600 italic'}`} title={tagName ?? undefined}>
                    {tagName ?? '—'}
                  </div>
                </td>
                <td className="pr-1 align-top">
                  <input
                    type="text"
                    data-testid={`${idPrefix}-value-${i}`}
                    className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.value}
                    onChange={(e) => updateField(i, { value: e.target.value })}
                  />
                </td>
                <td className="pl-1 align-top">
                  <button
                    type="button"
                    data-testid={`${idPrefix}-remove-${i}`}
                    className="text-red-400 hover:text-red-300 text-xs"
                    onClick={() => removeField(i)}
                  >x</button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
```

In `SendFIXConfig.tsx`: delete the local `FieldRow` interface, the `updateField` / `addField` / `removeField` helpers, the `<datalist>` and the whole fields `<div>`, and replace them with:

```tsx
import { FieldTable, FieldRow } from './FieldTable';
...
      <FieldTable
        fields={fields}
        onChange={(next) => patchConfig({ fields: next })}
        idPrefix="sendfix"
      />
```

Keep `interface SendCfg { msgType?: string; fields?: FieldRow[] }` importing `FieldRow` from `./FieldTable`. Drop the now-unused `fixTagName`, `FIX_TAGS` and `ENGINE_TAGS` imports from `SendFIXConfig.tsx`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd fix-flow-ui && npx vitest run src/panels/right/NodeConfig/`
Expected: PASS — the new `FieldTable.test.tsx` **and** the pre-existing `SendFIXConfig.test.tsx` unchanged. If `SendFIXConfig.test.tsx` fails, the refactor changed behaviour; fix the component, not the test.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/FieldTable.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/FieldTable.test.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx
git commit -m "refactor(ui): extract reusable FieldTable from SendFIXConfig"
```

---

## Task 11: Recursive `GroupEditor` wired into `SendFIXConfig`

**Files:**
- Create: `fix-flow-ui/src/panels/right/NodeConfig/GroupEditor.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/GroupEditor.test.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.test.tsx`

**Interfaces:**
- Consumes: `FieldTable`, `FieldRow` (Task 10); `GROUP_COUNTER_TAGS` (Task 9).
- Produces:
  ```ts
  export interface GroupEntry { fields: FieldRow[]; groups?: GroupSpec[] }
  export interface GroupSpec { counterTag: number; entries: GroupEntry[] }
  export interface GroupEditorProps {
    groups: GroupSpec[];
    onChange: (next: GroupSpec[]) => void;
    depth?: number;        // 0 at top level; the editor refuses to nest past 3
    idPrefix?: string;
  }
  export function GroupEditor(props: GroupEditorProps): JSX.Element
  ```
  Task 12 serialises exactly this `GroupSpec[]` shape to YAML.

**Behaviour required:**
- `+ Add group` reveals a counter-tag input with a datalist of `GROUP_COUNTER_TAGS`; confirming appends `{ counterTag, entries: [{ fields: [] }] }`.
- Each group is a collapsible block headed `555 — NoLegs (2 entries)`.
- The counter value is **read-only, derived from `entries.length`**.
- Per-entry actions: add field (via `FieldTable`), duplicate, delete, move up, move down, add sub-group.
- Sub-groups render the same component with `depth + 1`; at `depth >= 3` the add-sub-group control is replaced by a message.

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GroupEditor, GroupSpec } from './GroupEditor';

const twoLegs: GroupSpec[] = [{
  counterTag: 555,
  entries: [
    { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '1' }] },
    { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '2' }] },
  ],
}];

describe('GroupEditor', () => {
  it('heads each group with its counter tag, name and entry count', () => {
    render(<GroupEditor groups={twoLegs} onChange={() => {}} idPrefix="g" />);
    expect(screen.getByText(/555/)).toBeInTheDocument();
    expect(screen.getByText(/NoLegs/)).toBeInTheDocument();
    expect(screen.getByText(/2 entries/)).toBeInTheDocument();
  });

  it('derives the counter from entry count and never lets it be typed', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    const counter = screen.getByTestId('g-counter-0') as HTMLInputElement;
    expect(counter.value).toBe('2');
    expect(counter.readOnly).toBe(true);

    await userEvent.click(screen.getByTestId('g-add-entry-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries).toHaveLength(3);
  });

  it('adds a group with one empty entry', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={[]} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-add-group'));
    await userEvent.type(screen.getByTestId('g-new-counter'), '864');
    await userEvent.click(screen.getByTestId('g-confirm-group'));
    expect(onChange).toHaveBeenCalledWith([{ counterTag: 864, entries: [{ fields: [] }] }]);
  });

  it('edits a field inside an entry', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.clear(screen.getByTestId('g-0-1-value-1'));
    await userEvent.type(screen.getByTestId('g-0-1-value-1'), '2');
    expect(onChange).toHaveBeenCalled();
  });

  it('duplicates, deletes and reorders entries', async () => {
    const onChange = vi.fn();
    const { rerender } = render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);

    await userEvent.click(screen.getByTestId('g-dup-entry-0-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries).toHaveLength(3);

    rerender(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-del-entry-0-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries).toHaveLength(1);

    rerender(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-down-entry-0-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries[0].fields[1].value).toBe('2');
  });

  it('removes a whole group', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-del-group-0'));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it('nests a sub-group inside an entry', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-add-subgroup-0-0'));
    await userEvent.type(screen.getByTestId('g-0-0-sub-new-counter'), '864');
    await userEvent.click(screen.getByTestId('g-0-0-sub-confirm-group'));
    expect(onChange.mock.calls.at(-1)![0][0].entries[0].groups).toEqual(
      [{ counterTag: 864, entries: [{ fields: [] }] }]);
  });

  it('stops offering sub-groups past depth 3', () => {
    render(<GroupEditor groups={twoLegs} onChange={() => {}} idPrefix="g" depth={3} />);
    expect(screen.queryByTestId('g-add-subgroup-0-0')).toBeNull();
    expect(screen.getByText(/nesting limit/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/panels/right/NodeConfig/GroupEditor.test.tsx`
Expected: FAIL — module `./GroupEditor` not found.

- [ ] **Step 3: Write minimal implementation**

```tsx
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { FieldTable, FieldRow } from './FieldTable';
import { GROUP_COUNTER_TAGS } from '../../../lib/fixTags';

export interface GroupEntry { fields: FieldRow[]; groups?: GroupSpec[] }
export interface GroupSpec { counterTag: number; entries: GroupEntry[] }

export interface GroupEditorProps {
  groups: GroupSpec[];
  onChange: (next: GroupSpec[]) => void;
  depth?: number;
  idPrefix?: string;
}

const MAX_DEPTH = 3;

export function GroupEditor({ groups, onChange, depth = 0, idPrefix = 'grp' }: GroupEditorProps) {
  const { t } = useTranslation();
  const [adding, setAdding] = useState(false);
  const [newCounter, setNewCounter] = useState('');
  const [collapsed, setCollapsed] = useState<Record<number, boolean>>({});

  const patchGroup = (gi: number, patch: Partial<GroupSpec>) =>
    onChange(groups.map((g, i) => (i === gi ? { ...g, ...patch } : g)));

  const patchEntries = (gi: number, next: GroupEntry[]) => patchGroup(gi, { entries: next });

  const confirmAdd = () => {
    const tag = Number(newCounter);
    if (!Number.isInteger(tag) || tag <= 0) return;
    onChange([...groups, { counterTag: tag, entries: [{ fields: [] }] }]);
    setNewCounter('');
    setAdding(false);
  };

  const move = (gi: number, ei: number, delta: number) => {
    const entries = [...groups[gi].entries];
    const target = ei + delta;
    if (target < 0 || target >= entries.length) return;
    [entries[ei], entries[target]] = [entries[target], entries[ei]];
    patchEntries(gi, entries);
  };

  return (
    <div className={depth > 0 ? 'pl-2 border-l border-[#2a2d3a]' : ''}>
      <div className="flex items-center justify-between mt-2">
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.sendFix.groups')}
          <span
            title="FIX repeating groups. The counter tag (e.g. 555 NoLegs) is derived from the number of entries and written by the engine — never type it as a field."
            className="ml-1 text-gray-600 cursor-help"
          >?</span>
        </label>
        <button
          type="button"
          data-testid={`${idPrefix}-add-group`}
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={() => setAdding((v) => !v)}
        >
          {t('nodeConfig.sendFix.addGroup')}
        </button>
      </div>

      {adding && (
        <div className="flex gap-1 mt-1">
          <input
            type="number"
            list={`${idPrefix}-counter-list`}
            data-testid={`${idPrefix}-new-counter`}
            placeholder="555"
            className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 text-xs"
            value={newCounter}
            onChange={(e) => setNewCounter(e.target.value)}
          />
          <datalist id={`${idPrefix}-counter-list`}>
            {Object.entries(GROUP_COUNTER_TAGS).map(([tag, name]) => (
              <option key={tag} value={tag}>{`${tag} — ${name}`}</option>
            ))}
          </datalist>
          <button
            type="button"
            data-testid={`${idPrefix}-confirm-group`}
            className="px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded text-[10px]"
            onClick={confirmAdd}
          >OK</button>
        </div>
      )}

      {groups.map((g, gi) => (
        <div key={gi} className="border border-[#2a2d3a] rounded mt-1">
          <div className="flex items-center gap-1 px-2 py-1 bg-[#161922]">
            <button
              type="button"
              className="text-[10px] text-gray-400"
              onClick={() => setCollapsed((c) => ({ ...c, [gi]: !c[gi] }))}
            >{collapsed[gi] ? '▼' : '▲'}</button>
            <span className="text-[10px] text-gray-300">
              {g.counterTag} — {GROUP_COUNTER_TAGS[g.counterTag] ?? 'group'} ({g.entries.length} entries)
            </span>
            <input
              readOnly
              data-testid={`${idPrefix}-counter-${gi}`}
              className="w-10 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 text-[10px] text-gray-500"
              value={g.entries.length}
              title="Derived from entry count — maintained by the engine"
            />
            <div className="flex-1" />
            <button
              type="button"
              data-testid={`${idPrefix}-add-entry-${gi}`}
              className="text-[10px] px-1.5 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
              onClick={() => patchEntries(gi, [...g.entries, { fields: [] }])}
            >{t('nodeConfig.sendFix.addEntry')}</button>
            <button
              type="button"
              data-testid={`${idPrefix}-del-group-${gi}`}
              className="text-red-400 hover:text-red-300 text-xs px-1"
              onClick={() => onChange(groups.filter((_, i) => i !== gi))}
            >x</button>
          </div>

          {!collapsed[gi] && g.entries.map((entry, ei) => (
            <div key={ei} className="border-t border-[#2a2d3a] px-2 py-1">
              <div className="flex items-center gap-1">
                <span className="text-[10px] text-gray-500">#{ei + 1}</span>
                <div className="flex-1" />
                <button type="button" data-testid={`${idPrefix}-up-entry-${gi}-${ei}`}
                        className="text-[10px] text-gray-400 px-1" onClick={() => move(gi, ei, -1)}>↑</button>
                <button type="button" data-testid={`${idPrefix}-down-entry-${gi}-${ei}`}
                        className="text-[10px] text-gray-400 px-1" onClick={() => move(gi, ei, 1)}>↓</button>
                <button type="button" data-testid={`${idPrefix}-dup-entry-${gi}-${ei}`}
                        className="text-[10px] text-gray-400 px-1"
                        onClick={() => patchEntries(gi, [
                          ...g.entries.slice(0, ei + 1),
                          JSON.parse(JSON.stringify(entry)) as GroupEntry,
                          ...g.entries.slice(ei + 1),
                        ])}>⧉</button>
                <button type="button" data-testid={`${idPrefix}-del-entry-${gi}-${ei}`}
                        className="text-red-400 hover:text-red-300 text-xs px-1"
                        onClick={() => patchEntries(gi, g.entries.filter((_, i) => i !== ei))}>x</button>
              </div>

              <FieldTable
                fields={entry.fields}
                idPrefix={`${idPrefix}-${gi}-${ei}`}
                label={t('nodeConfig.sendFix.entryFields')}
                onChange={(next) => patchEntries(gi,
                  g.entries.map((e, i) => (i === ei ? { ...e, fields: next } : e)))}
              />

              {depth < MAX_DEPTH ? (
                <>
                  {(entry.groups?.length ?? 0) === 0 && (
                    <button
                      type="button"
                      data-testid={`${idPrefix}-add-subgroup-${gi}-${ei}`}
                      className="text-[10px] text-blue-400 hover:text-blue-300 mt-1"
                      onClick={() => patchEntries(gi,
                        g.entries.map((e, i) => (i === ei ? { ...e, groups: [] } : e)))}
                    >{t('nodeConfig.sendFix.addSubGroup')}</button>
                  )}
                  {entry.groups && (
                    <GroupEditor
                      groups={entry.groups}
                      depth={depth + 1}
                      idPrefix={`${idPrefix}-${gi}-${ei}-sub`}
                      onChange={(next) => patchEntries(gi,
                        g.entries.map((e, i) => (i === ei ? { ...e, groups: next } : e)))}
                    />
                  )}
                </>
              ) : (
                <div className="text-[10px] text-gray-600 italic mt-1">
                  {t('nodeConfig.sendFix.nestingLimit')}
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
```

Note the sub-group flow: clicking `add-subgroup` sets `entry.groups = []`, which renders a nested `GroupEditor` whose own `+ Add group` / counter input carry the `-sub` prefix the test asserts on.

Wire it into `SendFIXConfig.tsx` — extend the config interface and render the editor under the field table:

```tsx
import { GroupEditor, GroupSpec } from './GroupEditor';

interface SendCfg { msgType?: string; fields?: FieldRow[]; groups?: GroupSpec[] }
...
      <GroupEditor
        groups={cfg.groups ?? []}
        onChange={(next) => patchConfig({ groups: next })}
        idPrefix="sendfix-grp"
      />
```

Place it between `<FieldTable .../>` and `<VarRefPanel />`.

Add one case to `SendFIXConfig.test.tsx`:

```tsx
  it('shows the repeating groups section', () => {
    // render SendFIXConfig with a SEND_FIX node exactly as the existing tests do
    expect(screen.getByTestId('sendfix-grp-add-group')).toBeInTheDocument();
  });
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd fix-flow-ui && npx vitest run src/panels/right/NodeConfig/`
Expected: PASS — 8 `GroupEditor` cases plus `FieldTable` and `SendFIXConfig`.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/GroupEditor.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/GroupEditor.test.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.test.tsx
git commit -m "feat(ui): add recursive repeating group editor to SEND_FIX config"
```

---

## Task 12: YAML round-trip for groups

**Files:**
- Modify: `fix-flow-ui/src/lib/scenarioSerializer.ts`
- Modify: `fix-flow-ui/src/lib/scenarioSerializer.test.ts`

**Interfaces:**
- Consumes: `GroupSpec` / `GroupEntry` (Task 11).
- Produces: `serializeToYaml` writes `config.groups` as `[{ counterTag, entries: [{ fields: {tag: value}, groups: [...] }] }]`; `parseFromYaml` converts entry `fields` back to `FieldRow[]`. Recursion through nested groups both ways.

**Why this matters:** `serializeConfig` currently rewrites only `fields` and spreads the rest of `config`. Group entries would survive as-is with their inner `fields` still in array form, producing YAML the Java `SendFIXHandler` accepts but which does not match the map form used for top-level fields. Make it explicit and symmetric.

- [ ] **Step 1: Write the failing test**

Append to `fix-flow-ui/src/lib/scenarioSerializer.test.ts`:

```ts
describe('repeating groups', () => {
  const nodes = [{
    id: 'send-swap',
    name: 'Send Multileg',
    type: 'SEND_FIX' as const,
    config: {
      msgType: 'AB',
      fields: [{ tag: 11, value: 'ORD-1' }],
      groups: [{
        counterTag: 555,
        entries: [
          { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '1' }] },
          { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '2' }] },
        ],
      }],
    },
    position: { x: 0, y: 0 },
  }];
  const meta = { id: 'x', name: 'n', description: 'd', version: '1.0', sessionRef: 's' };

  it('serialises entry fields as tag->value maps', () => {
    const yamlStr = serializeToYaml(nodes as never, [], meta);
    expect(yamlStr).toContain('counterTag: 555');
    expect(yamlStr).toContain('600: EUR/USD');
    expect(yamlStr).not.toContain('- tag: 600');
  });

  it('round-trips groups back to arrays', () => {
    const back = parseFromYaml(serializeToYaml(nodes as never, [], meta));
    const cfg = back.nodes[0].config as {
      groups: { counterTag: number; entries: { fields: { tag: number; value: string }[] }[] }[];
    };
    expect(cfg.groups[0].counterTag).toBe(555);
    expect(cfg.groups[0].entries).toHaveLength(2);
    expect(cfg.groups[0].entries[1].fields).toEqual([
      { tag: 600, value: 'EUR/USD' },
      { tag: 624, value: '2' },
    ]);
  });

  it('round-trips nested groups', () => {
    const nested = [{
      ...nodes[0],
      config: {
        msgType: 'AB',
        groups: [{
          counterTag: 555,
          entries: [{
            fields: [{ tag: 600, value: 'EUR/USD' }],
            groups: [{ counterTag: 864, entries: [{ fields: [{ tag: 865, value: '13' }] }] }],
          }],
        }],
      },
    }];
    const back = parseFromYaml(serializeToYaml(nested as never, [], meta));
    const cfg = back.nodes[0].config as never as {
      groups: { entries: { groups: { counterTag: number; entries: { fields: unknown[] }[] }[] }[] }[];
    };
    expect(cfg.groups[0].entries[0].groups[0].counterTag).toBe(864);
    expect(cfg.groups[0].entries[0].groups[0].entries[0].fields).toEqual([{ tag: 865, value: '13' }]);
  });

  it('leaves a SEND_FIX config without groups untouched', () => {
    const plain = [{ ...nodes[0], config: { msgType: 'D', fields: [{ tag: 11, value: 'ORD-1' }] } }];
    const back = parseFromYaml(serializeToYaml(plain as never, [], meta));
    expect((back.nodes[0].config as { groups?: unknown }).groups).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/lib/scenarioSerializer.test.ts`
Expected: FAIL — the serialised YAML still contains `- tag: 600`.

- [ ] **Step 3: Write minimal implementation**

In `scenarioSerializer.ts`, add the two recursive helpers and call them from `serializeConfig` / `parseConfig`:

```ts
interface YamlGroupEntry { fields?: Record<string, string>; groups?: YamlGroupSpec[] }
interface YamlGroupSpec { counterTag: number; entries: YamlGroupEntry[] }

function serializeGroups(groups: Array<Record<string, unknown>>): YamlGroupSpec[] {
  return groups.map((g) => ({
    counterTag: Number(g.counterTag),
    entries: ((g.entries ?? []) as Array<Record<string, unknown>>).map((e) => {
      const out: YamlGroupEntry = {
        fields: fieldsArrayToMap((e.fields ?? []) as Array<{ tag: number; value: string }>) as unknown as Record<string, string>,
      };
      if (Array.isArray(e.groups) && e.groups.length > 0) {
        out.groups = serializeGroups(e.groups as Array<Record<string, unknown>>);
      }
      return out;
    }),
  }));
}

function parseGroups(groups: YamlGroupSpec[]): Array<Record<string, unknown>> {
  return groups.map((g) => ({
    counterTag: Number(g.counterTag),
    entries: (g.entries ?? []).map((e) => {
      const out: Record<string, unknown> = {
        fields: fieldsMapToArray((e.fields ?? {}) as Record<string, string>),
      };
      if (Array.isArray(e.groups) && e.groups.length > 0) out.groups = parseGroups(e.groups);
      return out;
    }),
  }));
}
```

Then in `serializeConfig`:

```ts
function serializeConfig(type: NodeType, config: Record<string, unknown>): Record<string, unknown> {
  if (type === 'SEND_FIX') {
    const out = { ...config };
    if (Array.isArray(config.fields)) {
      out.fields = fieldsArrayToMap(config.fields as Array<{ tag: number; value: string }>);
    }
    if (Array.isArray(config.groups) && config.groups.length > 0) {
      out.groups = serializeGroups(config.groups as Array<Record<string, unknown>>);
    } else {
      delete out.groups;
    }
    return out;
  }
  // ... ROUTE_FIX branch unchanged
  return config;
}
```

and in `parseConfig`:

```ts
function parseConfig(type: NodeType, config: Record<string, unknown>): Record<string, unknown> {
  if (type === 'SEND_FIX') {
    const out = { ...config };
    if (config.fields != null && !Array.isArray(config.fields)) {
      out.fields = fieldsMapToArray(config.fields as Record<string, string>);
    }
    if (Array.isArray(config.groups) && config.groups.length > 0) {
      out.groups = parseGroups(config.groups as unknown as YamlGroupSpec[]);
    } else {
      delete out.groups;
    }
    return out;
  }
  // ... ROUTE_FIX branch unchanged
  return config;
}
```

`fieldsArrayToMap` returns `Record<number, string>`; js-yaml emits the numeric keys as `600: EUR/USD`, which Jackson deserialises into the `Map<String,Object>` node config and `SendFIXHandler.resolveFields` reads via its map branch. No Java change needed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd fix-flow-ui && npm test`
Expected: PASS — the whole UI suite, including the pre-existing serializer cases.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/lib/scenarioSerializer.ts fix-flow-ui/src/lib/scenarioSerializer.test.ts
git commit -m "feat(ui): round-trip repeating groups through the YAML serializer"
```

---

## Task 13: Rebuild groups when pasting a raw FIX message

**Files:**
- Modify: `fix-flow-ui/src/lib/parseFIXMessage.ts`
- Modify: `fix-flow-ui/src/lib/parseFIXMessage.test.ts`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`

**Interfaces:**
- Consumes: `GROUP_COUNTER_TAGS` (Task 9), `GroupSpec` (Task 11).
- Produces: `ParsedFIX` gains `groups: GroupSpec[]` and `unknownCounters: number[]`. `SendFIXConfig.handleParse` writes both `fields` and `groups` into the node config and surfaces a warning naming any counter tag it could not structure.

**Algorithm:** walk the segments left to right. On a tag in `GROUP_DELIMITERS`, read its value as the expected entry count and enter group mode: the *next* segment's tag becomes the delimiter, and each later occurrence of that delimiter starts a new entry. Group mode ends when the declared number of entries is complete and a tag appears that is not in the set of tags already seen inside the group. A counter tag absent from `GROUP_DELIMITERS` is reported in `unknownCounters` and its content stays flat, exactly as today.

- [ ] **Step 1: Write the failing test**

Append to `fix-flow-ui/src/lib/parseFIXMessage.test.ts`:

```ts
describe('repeating groups on paste', () => {
  const multileg =
    '8=FIXT.1.1|35=AB|49=CLIENT|56=SERVER|11=ORD-1|55=EUR/USD|167=FXSWAP|' +
    '555=2|600=EUR/USD|624=1|587=0|600=EUR/USD|624=2|587=6|60=20260824-10:00:00|';

  it('rebuilds two leg entries', () => {
    const r = parseFIXMessage(multileg);
    expect(r.msgType).toBe('AB');
    expect(r.groups).toHaveLength(1);
    expect(r.groups[0].counterTag).toBe(555);
    expect(r.groups[0].entries).toHaveLength(2);
    expect(r.groups[0].entries[0].fields).toEqual([
      { tag: 600, value: 'EUR/USD' }, { tag: 624, value: '1' }, { tag: 587, value: '0' },
    ]);
    expect(r.groups[0].entries[1].fields[1]).toEqual({ tag: 624, value: '2' });
  });

  it('keeps fields before and after the group at top level', () => {
    const r = parseFIXMessage(multileg);
    const tags = r.fields.map((f) => f.tag);
    expect(tags).toContain(11);
    expect(tags).toContain(167);
    expect(tags).toContain(60);
    expect(tags).not.toContain(600);
    expect(tags).not.toContain(555);
  });

  it('reports unknown counter tags and leaves their content flat', () => {
    const r = parseFIXMessage('8=FIXT.1.1|35=8|9999=2|8001=a|8001=b|11=ORD-1|');
    expect(r.unknownCounters).toEqual([]);
    expect(r.groups).toHaveLength(0);
    expect(r.fields.map((f) => f.tag)).toContain(8001);
  });

  it('flags a known counter whose declared count does not match', () => {
    const r = parseFIXMessage('8=FIXT.1.1|35=AB|555=3|600=EUR/USD|624=1|11=ORD-1|');
    expect(r.unknownCounters).toContain(555);
  });

  it('leaves a message without groups exactly as before', () => {
    const r = parseFIXMessage('8=FIX.4.4|35=D|49=C|56=S|11=ORD-1|55=AAPL|38=100|');
    expect(r.groups).toEqual([]);
    expect(r.fields).toEqual([
      { tag: 11, value: 'ORD-1' }, { tag: 55, value: 'AAPL' }, { tag: 38, value: '100' },
    ]);
    expect(r.skipped).toBe(3);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/lib/parseFIXMessage.test.ts`
Expected: FAIL — `r.groups` is undefined.

- [ ] **Step 3: Write minimal implementation**

Replace `parseFIXMessage.ts`:

```ts
import { GroupSpec, GroupEntry } from '../panels/right/NodeConfig/GroupEditor';

export interface ParsedFIX {
  msgType?: string;
  fields: Array<{ tag: number; value: string }>;
  groups: GroupSpec[];
  unknownCounters: number[];
  skipped: number;
}

// Tags managed by QuickFIX/J engine — skip on paste
export const ENGINE_TAGS = new Set([8, 9, 10, 34, 49, 52, 56]);

/** Counter tag -> delimiter tag (the first field of every entry). */
export const GROUP_DELIMITERS: Record<number, number> = {
  78: 79,     // NoAllocs   -> AllocAccount
  453: 448,   // NoPartyIDs -> PartyID
  555: 600,   // NoLegs     -> LegSymbol
  702: 703,   // NoPositions-> PosType
  711: 311,   // NoUnderlyings -> UnderlyingSymbol
  864: 865,   // NoEvents   -> EventType
};

export function parseFIXMessage(raw: string): ParsedFIX {
  const normalized = raw.replace(/\x01/g, '|');
  const segments = normalized.split('|').map((s) => s.trim()).filter(Boolean);

  let msgType: string | undefined;
  const fields: Array<{ tag: number; value: string }> = [];
  const groups: GroupSpec[] = [];
  const unknownCounters: number[] = [];
  let skipped = 0;

  let i = 0;
  while (i < segments.length) {
    const seg = segments[i];
    const eq = seg.indexOf('=');
    if (eq < 0) { skipped++; i++; continue; }

    const tag = parseInt(seg.slice(0, eq).trim(), 10);
    const value = seg.slice(eq + 1).trim();
    if (isNaN(tag) || tag <= 0) { skipped++; i++; continue; }
    if (tag === 35) { msgType = value; i++; continue; }
    if (ENGINE_TAGS.has(tag)) { skipped++; i++; continue; }

    const delimiter = GROUP_DELIMITERS[tag];
    if (delimiter !== undefined) {
      const declared = parseInt(value, 10);
      const consumed = readGroup(segments, i + 1, tag, delimiter, declared);
      if (consumed.entries.length === declared && declared > 0) {
        groups.push({ counterTag: tag, entries: consumed.entries });
        i = consumed.nextIndex;
        continue;
      }
      // Declared count and reconstructed entries disagree: fall back to flat,
      // and tell the user rather than silently producing a wrong message.
      unknownCounters.push(tag);
      i++;
      continue;
    }

    fields.push({ tag, value });
    i++;
  }

  return { msgType, fields, groups, unknownCounters, skipped };
}

function readGroup(
  segments: string[],
  start: number,
  counterTag: number,
  delimiter: number,
  declared: number,
): { entries: GroupEntry[]; nextIndex: number } {
  const entries: GroupEntry[] = [];
  const seenTags = new Set<number>();
  let current: GroupEntry | null = null;
  let i = start;

  for (; i < segments.length; i++) {
    const eq = segments[i].indexOf('=');
    if (eq < 0) break;
    const tag = parseInt(segments[i].slice(0, eq).trim(), 10);
    const value = segments[i].slice(eq + 1).trim();
    if (isNaN(tag)) break;

    if (tag === delimiter) {
      if (entries.length === declared) break;      // group complete
      current = { fields: [] };
      entries.push(current);
      seenTags.add(tag);
      current.fields.push({ tag, value });
      continue;
    }
    if (current === null) break;                   // first tag was not the delimiter
    if (entries.length === declared && !seenTags.has(tag)) break;
    if (!seenTags.has(tag) && entries.length > 1) break;
    seenTags.add(tag);
    current.fields.push({ tag, value });
  }

  return { entries, nextIndex: i };
}
```

Then in `SendFIXConfig.tsx`, extend `handleParse` to carry groups and report unknown counters:

```tsx
  const handleParse = () => {
    if (!pasteRaw.trim()) { setParseError('Paste a FIX message first'); return; }
    const result = parseFIXMessage(pasteRaw);
    const updates: Partial<SendCfg> = { fields: result.fields, groups: result.groups };
    if (result.msgType) updates.msgType = result.msgType;
    patchConfig(updates);
    setPasteRaw('');
    const notes: string[] = [];
    if (result.skipped > 0) notes.push(`${result.skipped} segment(s) skipped (engine-managed or malformed)`);
    if (result.unknownCounters.length > 0) {
      notes.push(`repeating group(s) ${result.unknownCounters.join(', ')} left flat — build them by hand below`);
    }
    setParseError(notes.length ? `Parsed OK — ${notes.join('; ')}` : '');
    setShowPaste(false);
  };
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd fix-flow-ui && npm test`
Expected: PASS — the whole UI suite.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/lib/parseFIXMessage.ts fix-flow-ui/src/lib/parseFIXMessage.test.ts \
        fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx
git commit -m "feat(ui): reconstruct repeating groups when pasting a raw FIX message"
```

---

## Task 14: `groupTag` / `index` inputs in the VALIDATE editor

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.test.tsx`

**Interfaces:**
- Consumes: the rule shape accepted by `ValidateHandler.toConfig` (Task 8).
- Produces: rules carrying optional `groupTag: number` and `index: string`. Both are omitted from the emitted config when blank, so existing scenarios serialise byte-identically.

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, beforeEach } from 'vitest';
import { ValidateConfig } from './ValidateConfig';
import { useScenarioStore } from '../../../store/scenarioStore';

const node = {
  id: 'v1', name: 'Validate', type: 'VALIDATE' as const,
  config: { rules: [{ tag: 609, rule: 'EQUALS', value: 'FXSPOT' }] },
  position: { x: 0, y: 0 },
};

describe('ValidateConfig group inputs', () => {
  beforeEach(() => {
    useScenarioStore.setState({ nodes: [node], edges: [] });
  });

  it('renders a group tag and index input per rule', () => {
    render(<ValidateConfig node={node} />);
    expect(screen.getByTestId('validate-grouptag-0')).toBeInTheDocument();
    expect(screen.getByTestId('validate-index-0')).toBeInTheDocument();
  });

  it('writes groupTag and index into the rule', async () => {
    render(<ValidateConfig node={node} />);
    await userEvent.type(screen.getByTestId('validate-grouptag-0'), '555');
    await userEvent.type(screen.getByTestId('validate-index-0'), '1');

    const rules = (useScenarioStore.getState().nodes[0].config as
      { rules: Array<Record<string, unknown>> }).rules;
    expect(rules[0].groupTag).toBe(555);
    expect(rules[0].index).toBe('1');
  });

  it('omits both keys when the inputs are cleared', async () => {
    useScenarioStore.setState({
      nodes: [{ ...node, config: { rules: [{ tag: 609, rule: 'EQUALS', value: 'FXSPOT', groupTag: 555, index: '0' }] } }],
      edges: [],
    });
    render(<ValidateConfig node={useScenarioStore.getState().nodes[0]} />);
    await userEvent.clear(screen.getByTestId('validate-grouptag-0'));

    const rules = (useScenarioStore.getState().nodes[0].config as
      { rules: Array<Record<string, unknown>> }).rules;
    expect(rules[0]).not.toHaveProperty('groupTag');
  });

  it('accepts the wildcard index', async () => {
    render(<ValidateConfig node={node} />);
    await userEvent.type(screen.getByTestId('validate-index-0'), '*');
    const rules = (useScenarioStore.getState().nodes[0].config as
      { rules: Array<Record<string, unknown>> }).rules;
    expect(rules[0].index).toBe('*');
  });
});
```

If `ValidateConfig` is a default export, adjust the import accordingly — check the file before writing the test.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/panels/right/NodeConfig/ValidateConfig.test.tsx`
Expected: FAIL — `validate-grouptag-0` not found.

- [ ] **Step 3: Write minimal implementation**

In `ValidateConfig.tsx`, extend the rule row. Add to the rule interface `groupTag?: number; index?: string`, then render two narrow inputs next to the tag input:

```tsx
                  <input
                    type="number"
                    data-testid={`validate-grouptag-${i}`}
                    placeholder="grp"
                    title="Repeating group counter tag (e.g. 555 NoLegs). Leave blank to validate a top-level field."
                    className="w-14 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={r.groupTag ?? ''}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      updateRule(i, v === ''
                        ? { groupTag: undefined }
                        : { groupTag: Number(v) });
                    }}
                  />
                  <input
                    type="text"
                    data-testid={`validate-index-${i}`}
                    placeholder="idx"
                    title="Group entry index, 0-based. Use * to apply the rule to every entry."
                    className="w-10 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={r.index ?? ''}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      updateRule(i, v === '' ? { index: undefined } : { index: v });
                    }}
                  />
```

`updateRule` must strip keys whose value is `undefined` before writing to the store, so a cleared input removes the key rather than serialising `groupTag: null`:

```tsx
  const updateRule = (i: number, patch: Record<string, unknown>) => {
    const next = rules.map((r, idx) => {
      if (idx !== i) return r;
      const merged: Record<string, unknown> = { ...r, ...patch };
      Object.keys(merged).forEach((k) => { if (merged[k] === undefined) delete merged[k]; });
      return merged;
    });
    patchConfig({ rules: next });
  };
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd fix-flow-ui && npm test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.test.tsx
git commit -m "feat(ui): validate rules can target repeating group entries"
```

---

## Task 15: i18n keys for the new UI

**Files:**
- Modify: `fix-flow-ui/src/i18n/locales/en.json`
- Modify: `fix-flow-ui/src/i18n/locales/it.json`
- Modify: `fix-flow-ui/src/i18n/locales/fr.json`
- Create: `fix-flow-ui/src/i18n/locales.test.ts`

**Interfaces:**
- Consumes: the `t('...')` keys used in Tasks 11 and 16.
- Produces: the six new `nodeConfig.sendFix.*` keys and the four new `topbar.shutdown*` keys, present in all three locales.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest';
import en from './locales/en.json';
import it from './locales/it.json';
import fr from './locales/fr.json';

const REQUIRED = [
  'nodeConfig.sendFix.groups',
  'nodeConfig.sendFix.addGroup',
  'nodeConfig.sendFix.addEntry',
  'nodeConfig.sendFix.entryFields',
  'nodeConfig.sendFix.addSubGroup',
  'nodeConfig.sendFix.nestingLimit',
  'topbar.shutdown',
  'topbar.shutdownConfirm',
  'topbar.shutdownDone',
  'topbar.shutdownFailed',
];

const at = (obj: unknown, path: string) =>
  path.split('.').reduce<unknown>((acc, k) => (acc as Record<string, unknown>)?.[k], obj);

describe('locales', () => {
  it.each([['en', en], ['it', it], ['fr', fr]])('%s defines every required key', (_name, bundle) => {
    REQUIRED.forEach((key) => {
      expect(at(bundle, key), `missing ${key}`).toBeTruthy();
    });
  });

  it('all three locales have identical key sets', () => {
    const keys = (o: unknown, prefix = ''): string[] =>
      Object.entries(o as Record<string, unknown>).flatMap(([k, v]) =>
        typeof v === 'object' && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`]);
    expect(keys(it).sort()).toEqual(keys(en).sort());
    expect(keys(fr).sort()).toEqual(keys(en).sort());
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fix-flow-ui && npx vitest run src/i18n/locales.test.ts`
Expected: FAIL — missing keys.

- [ ] **Step 3: Write minimal implementation**

Add under `nodeConfig.sendFix` and `topbar` in each bundle.

`en.json`:
```json
"groups": "Repeating groups",
"addGroup": "+ Group",
"addEntry": "+ Entry",
"entryFields": "Entry fields",
"addSubGroup": "+ Nested group",
"nestingLimit": "Nesting limit reached (3 levels)"
```
```json
"shutdown": "Shutdown",
"shutdownConfirm": "Stop the simulator and terminate the process?",
"shutdownDone": "Simulator stopped. You can close this tab.",
"shutdownFailed": "Shutdown request failed"
```

`it.json`:
```json
"groups": "Gruppi ripetuti",
"addGroup": "+ Gruppo",
"addEntry": "+ Occorrenza",
"entryFields": "Campi occorrenza",
"addSubGroup": "+ Gruppo annidato",
"nestingLimit": "Limite di annidamento raggiunto (3 livelli)"
```
```json
"shutdown": "Arresta",
"shutdownConfirm": "Arrestare il simulatore e terminare il processo?",
"shutdownDone": "Simulatore arrestato. Puoi chiudere questa scheda.",
"shutdownFailed": "Richiesta di arresto fallita"
```

`fr.json`:
```json
"groups": "Groupes répétitifs",
"addGroup": "+ Groupe",
"addEntry": "+ Occurrence",
"entryFields": "Champs de l'occurrence",
"addSubGroup": "+ Groupe imbriqué",
"nestingLimit": "Limite d'imbrication atteinte (3 niveaux)"
```
```json
"shutdown": "Arrêter",
"shutdownConfirm": "Arrêter le simulateur et terminer le processus ?",
"shutdownDone": "Simulateur arrêté. Vous pouvez fermer cet onglet.",
"shutdownFailed": "Échec de la demande d'arrêt"
```

If the "identical key sets" assertion fails on pre-existing drift between bundles, fix the drift — a missing translation is a real defect, not test noise.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd fix-flow-ui && npm test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-ui/src/i18n/
git commit -m "feat(ui): add i18n keys for group editor and shutdown button"
```

---

## Task 16: Shutdown button — stop the simulator from the toolbar

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/SystemController.java`
- Create: `fix-flow-api/src/test/java/com/fixflow/api/rest/SystemControllerTest.java`
- Create: `fix-flow-ui/src/api/system.ts`
- Modify: `fix-flow-ui/src/components/TopBar.tsx`
- Modify: `fix-flow-ui/src/components/TopBar.test.tsx`

**Interfaces:**
- Consumes: `topbar.shutdown*` i18n keys (Task 15).
- Produces: `POST /api/v1/system/shutdown` returning `202 Accepted`; `shutdownSimulator(): Promise<void>` in `fix-flow-ui/src/api/system.ts`.

**Why this is separate from the rest of the plan:** it is an unrelated usability request — today the only way to stop the simulator is killing the process from Task Manager, because QuickFIX/J acceptor threads are non-daemon and keep the JVM alive after the browser is closed.

**Design:** the endpoint returns immediately and hands off to a short-lived thread that sleeps briefly (so the HTTP response flushes), closes the Spring context via `SpringApplication.exit`, then calls `System.exit`. `System.exit` is what actually guarantees the JVM dies despite the non-daemon QuickFIX/J threads.

- [ ] **Step 1: Write the failing test**

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemController.class)
@Import(TestWebConfig.class)
class SystemControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void shutdownReturnsAcceptedImmediately() throws Exception {
        mvc.perform(post("/api/v1/system/shutdown")).andExpect(status().isAccepted());
    }

    @Test
    void shutdownHookIsInvokedExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SystemController controller = new SystemController(null, calls::set);

        controller.shutdown();
        controller.shutdown();
        Thread.sleep(600);

        assertEquals(0, calls.get(), "exit code 0");
        assertEquals(1, SystemController.shutdownCount(), "a second request must not start a second exit");
    }
}
```

`TestWebConfig` already exists in the same package and is used by the other controller tests; reuse it so the `@WebMvcTest` slice wires up.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl fix-flow-api test -Dtest=SystemControllerTest`
Expected: FAIL — `SystemController` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.fixflow.api.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Lets the UI stop the simulator without reaching for Task Manager.
 *
 * <p>QuickFIX/J acceptor threads are non-daemon, so closing the Spring context is not enough
 * to end the JVM — {@code System.exit} is. The response is sent first and the exit happens on
 * a separate thread after a short delay, so the browser sees 202 rather than a dropped socket.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private static final AtomicInteger SHUTDOWNS_STARTED = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean(false);

    private final ApplicationContext context;
    private final IntConsumer exit;

    public SystemController(ApplicationContext context) {
        this(context, System::exit);
    }

    SystemController(ApplicationContext context, IntConsumer exit) {
        this.context = context;
        this.exit = exit;
    }

    static int shutdownCount() { return SHUTDOWNS_STARTED.get(); }

    @PostMapping("/shutdown")
    public ResponseEntity<Void> shutdown() {
        if (started.compareAndSet(false, true)) {
            SHUTDOWNS_STARTED.incrementAndGet();
            Thread t = new Thread(this::closeAndExit, "fixflow-shutdown");
            t.setDaemon(false);
            t.start();
        }
        return ResponseEntity.accepted().build();
    }

    private void closeAndExit() {
        try {
            Thread.sleep(400);   // let the 202 flush to the browser
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int code = context == null ? 0 : SpringApplication.exit(context, () -> 0);
        exit.accept(code);
    }
}
```

`fix-flow-ui/src/api/system.ts`:

```ts
import { postJson } from './client';

export const shutdownSimulator = () =>
  postJson<Record<string, never>, void>('/system/shutdown', {});
```

In `TopBar.tsx`, add the mutation and the button. Put it at the far right, after Export, behind a separator so it cannot be hit by accident:

```tsx
import { shutdownSimulator } from '../api/system';
...
  const [shutdownDone, setShutdownDone] = useState(false);

  const shutdownMutation = useMutation({
    mutationFn: shutdownSimulator,
    onSuccess: () => setShutdownDone(true),
    onError: () => setErrorMsg(t('topbar.shutdownFailed')),
  });
...
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <button
        data-testid="topbar-shutdown"
        className="px-3 py-1 rounded bg-red-800 hover:bg-red-700 disabled:opacity-40 text-sm"
        title={t('topbar.shutdownConfirm')}
        disabled={shutdownMutation.isPending || shutdownDone}
        onClick={() => {
          if (window.confirm(t('topbar.shutdownConfirm'))) shutdownMutation.mutate();
        }}
      >
        {t('topbar.shutdown')}
      </button>
```

and render the terminal notice as a full-screen overlay so the user is not left staring at a UI whose backend has gone:

```tsx
      {shutdownDone && (
        <div
          data-testid="shutdown-overlay"
          className="fixed inset-0 z-50 bg-[#0f1117]/95 flex items-center justify-center text-gray-300"
        >
          {t('topbar.shutdownDone')}
        </div>
      )}
```

Add to `TopBar.test.tsx`:

```tsx
  it('asks for confirmation before shutting down', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<TopBar />, { wrapper });        // reuse the wrapper the other TopBar tests use
    await userEvent.click(screen.getByTestId('topbar-shutdown'));
    expect(confirm).toHaveBeenCalled();
    expect(screen.queryByTestId('shutdown-overlay')).toBeNull();
    confirm.mockRestore();
  });
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl fix-flow-api test && cd fix-flow-ui && npm test`
Expected: PASS on both sides.

- [ ] **Step 5: Commit**

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/SystemController.java \
        fix-flow-api/src/test/java/com/fixflow/api/rest/SystemControllerTest.java \
        fix-flow-ui/src/api/system.ts fix-flow-ui/src/components/TopBar.tsx \
        fix-flow-ui/src/components/TopBar.test.tsx
git commit -m "feat: add toolbar shutdown button that terminates the simulator process"
```

**Known limitation to document, not fix here:** on Windows the relaunch path in `FixFlowApplication.relaunchInTerminal` uses `cmd /k`, so the console window stays open after the JVM exits. The user closes it, which is still better than hunting the process in Task Manager.

---

## Task 17: `fx-spot-lifecycle` template — the archetype

**Files:**
- Create: `<project>/fx-templates/fx-spot-lifecycle.yaml`
- Create: `<project>/fx-templates/README.md`

where `<project>` is the folder containing the `fix-flow-simulator` checkout
(`C:\Users\giorgio\Desktop\fix simulator` on the workstation used here). **These
files are outside the git repository and must not be committed to it.**

**Interfaces:**
- Consumes: `SEND_FIX` `groups` (Task 3), group-aware `VALIDATE` (Task 8), the
  group placeholder (Task 7). The spot template itself uses no groups — it is the
  archetype the other four are deltas against.
- Produces: the shared node graph reused by Tasks 18-21.

**Engine facts this template relies on — do not deviate:**
- `WAIT` takes its duration from the node's `timeout` block, not from `config`.
- `DECISION` reads `config.condition`, supports `==`, `!=`, `contains` only, and
  routes true to `onSuccess`, false to `onFailure`.
- `VALIDATE` reads the message stored by the node named in `config.sourceNodeId`.
- `ROUTE_FIX` rules are evaluated top to bottom, first match wins; a rule with
  empty `matchers` is the default.
- Scenario `id` must be a UUID.
- A message arriving while no node is waiting is parked in `MessageBuffer` and
  replayed when the next `ROUTE_FIX`/`EXPECT_FIX` registers, so returning to
  `dispatch` after each branch loses nothing.

- [ ] **Step 1: Write the template**

```yaml
# FX Spot — full order lifecycle, venue side, FIX 5.0 SP2
#
# The simulator plays the venue: it waits for orders from your application and
# answers with ExecutionReports. Point it at a FIXT.1.1 session with
# DefaultApplVerID=9.
#
# Instrument reference values used below:
#   167 SecurityType = FXSPOT
#   461 CFICode      = IFXXXP   (I Spot / F Foreign exchange / X X X / P Physical)
#   460 Product      = 4        (CURRENCY)
#    63 SettlType    = 0        (Regular — T+2 for most pairs)
#
# Covered: create -> ack -> fill, amend, cancel, expiry of resting limit orders.
# Market orders (40=1) fill immediately; limit orders (40=2) rest until cancelled,
# amended or expired.
#
# Tune for your environment:
#   - EUR/USD, the 1.0850 rate and the 20-second resting window
#   - the idle timeout on `dispatch` (120s) if your test sessions run longer
id: 7f1c0a10-0001-4a00-9c00-000000000001
name: FX Spot Lifecycle
description: Venue-side FX spot order lifecycle - new, ack, fill, amend, cancel, expiry
version: '1.0'
sessionRef: fx-venue

nodes:
  - id: start
    name: Start
    type: START
    config: {}
    onSuccess: dispatch
    position: { x: 40, y: 40 }

  - id: dispatch
    name: Await client message
    type: ROUTE_FIX
    config:
      rules:
        - ruleId: r-new
          label: NewOrderSingle
          matchers: { 35: D }
          targetNodeId: validate-new
        - ruleId: r-amend
          label: OrderCancelReplaceRequest
          matchers: { 35: G }
          targetNodeId: validate-amend
        - ruleId: r-cancel
          label: OrderCancelRequest
          matchers: { 35: F }
          targetNodeId: validate-cancel
        - ruleId: r-other
          label: Unsupported
          matchers: {}
          targetNodeId: reject-unsupported
    timeout: { value: 120, unit: SECONDS, onTimeout: JUMP, jumpTo: end-idle }
    position: { x: 40, y: 140 }

  # ---------------------------------------------------------------- new order

  - id: validate-new
    name: Validate NewOrderSingle
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: dispatch
      rules:
        - { tag: 11,  rule: FIELD_PRESENT }
        - { tag: 55,  rule: FIELD_PRESENT }
        - { tag: 60,  rule: FIELD_PRESENT }
        - { tag: 167, rule: EQUALS, value: FXSPOT }
        - { tag: 461, rule: EQUALS, value: IFXXXP }
        - { tag: 54,  rule: ENUM, values: ['1', '2'] }
        - { tag: 40,  rule: ENUM, values: ['1', '2'] }
        - { tag: 38,  rule: NUMERIC_MIN, numericValue: 1 }
    onSuccess: ack-new
    onFailure: reject-new
    position: { x: 320, y: 140 }

  - id: ack-new
    name: ER - New (150=0)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: '0' }
        - { tag: 39,  value: '0' }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 38,  value: '{{node:dispatch:tag38}}' }
        - { tag: 40,  value: '{{node:dispatch:tag40}}' }
        - { tag: 44,  value: '{{node:dispatch:tag44}}' }
        - { tag: 59,  value: '{{node:dispatch:tag59}}' }
        - { tag: 14,  value: '0' }
        - { tag: 151, value: '{{node:dispatch:tag38}}' }
        - { tag: 6,   value: '0' }
        - { tag: 60,  value: '{{now}}' }
        # instrument reference block
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 48,  value: '{{node:dispatch:tag55}}' }
        - { tag: 22,  value: '8' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 762, value: SPOT }
        - { tag: 107, value: 'FX Spot {{node:dispatch:tag55}}' }
        - { tag: 15,  value: '{{node:dispatch:tag15}}' }
        - { tag: 120, value: '{{node:dispatch:tag120}}' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
        - { tag: 207, value: XOFF }
    onSuccess: decide-ordtype
    position: { x: 600, y: 140 }

  - id: decide-ordtype
    name: Market or limit?
    type: DECISION
    config:
      condition: '{{node:dispatch:tag40}} == "1"'
    onSuccess: fill
    onFailure: rest-limit
    position: { x: 880, y: 140 }

  - id: fill
    name: ER - Trade (150=F)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: F }
        - { tag: 39,  value: '2' }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 38,  value: '{{node:dispatch:tag38}}' }
        - { tag: 31,  value: '1.0850' }
        - { tag: 32,  value: '{{node:dispatch:tag38}}' }
        - { tag: 14,  value: '{{node:dispatch:tag38}}' }
        - { tag: 151, value: '0' }
        - { tag: 6,   value: '1.0850' }
        - { tag: 75,  value: '{{nowdate}}' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 155, value: '1.0850' }
        - { tag: 156, value: M }
        # instrument reference block
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 48,  value: '{{node:dispatch:tag55}}' }
        - { tag: 22,  value: '8' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 762, value: SPOT }
        - { tag: 107, value: 'FX Spot {{node:dispatch:tag55}}' }
        - { tag: 15,  value: '{{node:dispatch:tag15}}' }
        - { tag: 120, value: '{{node:dispatch:tag120}}' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
        - { tag: 207, value: XOFF }
    onSuccess: dispatch
    position: { x: 880, y: 40 }

  - id: reject-new
    name: ER - Rejected (150=8)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: '8' }
        - { tag: 39,  value: '8' }
        - { tag: 103, value: '11' }
        - { tag: 58,  value: 'Instrument or order attributes not accepted for FX spot' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: dispatch
    position: { x: 320, y: 260 }

  # ------------------------------------------------- resting limit order

  - id: rest-limit
    name: Limit order resting
    type: ROUTE_FIX
    config:
      rules:
        - ruleId: r-cancel-resting
          label: Cancel while resting
          matchers: { 35: F }
          targetNodeId: validate-cancel
        - ruleId: r-amend-resting
          label: Amend while resting
          matchers: { 35: G }
          targetNodeId: validate-amend
        - ruleId: r-new-resting
          label: New order while resting
          matchers: { 35: D }
          targetNodeId: validate-new
    timeout: { value: 20, unit: SECONDS, onTimeout: JUMP, jumpTo: expire }
    position: { x: 1160, y: 140 }

  - id: expire
    name: ER - Expired (150=C)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: C }
        - { tag: 39,  value: C }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 38,  value: '{{node:dispatch:tag38}}' }
        - { tag: 14,  value: '0' }
        - { tag: 151, value: '0' }
        - { tag: 6,   value: '0' }
        - { tag: 58,  value: 'Order expired - time in force elapsed' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
    onSuccess: dispatch
    position: { x: 1160, y: 40 }

  # ---------------------------------------------------------------- amend

  - id: validate-amend
    name: Validate replace request
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: dispatch
      rules:
        - { tag: 41, rule: FIELD_PRESENT }
        - { tag: 11, rule: FIELD_PRESENT }
        - { tag: 38, rule: NUMERIC_MIN, numericValue: 1 }
        - { tag: 40, rule: ENUM, values: ['1', '2'] }
    onSuccess: ack-amend
    onFailure: reject-amend
    position: { x: 320, y: 380 }

  - id: ack-amend
    name: ER - Replaced (150=5)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 41,  value: '{{node:dispatch:tag41}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: '5' }
        - { tag: 39,  value: '0' }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 38,  value: '{{node:dispatch:tag38}}' }
        - { tag: 44,  value: '{{node:dispatch:tag44}}' }
        - { tag: 14,  value: '0' }
        - { tag: 151, value: '{{node:dispatch:tag38}}' }
        - { tag: 6,   value: '0' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
    onSuccess: dispatch
    position: { x: 600, y: 380 }

  - id: reject-amend
    name: OrderCancelReject - replace
    type: SEND_FIX
    config:
      msgType: '9'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 41,  value: '{{node:dispatch:tag41}}' }
        - { tag: 39,  value: '8' }
        - { tag: 434, value: '2' }
        - { tag: 102, value: '0' }
        - { tag: 58,  value: 'Replace rejected - unknown or ineligible order' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: dispatch
    position: { x: 600, y: 500 }

  # --------------------------------------------------------------- cancel

  - id: validate-cancel
    name: Validate cancel request
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: dispatch
      rules:
        - { tag: 41, rule: FIELD_PRESENT }
        - { tag: 11, rule: FIELD_PRESENT }
        - { tag: 55, rule: FIELD_PRESENT }
    onSuccess: ack-cancel
    onFailure: reject-cancel
    position: { x: 320, y: 620 }

  - id: ack-cancel
    name: ER - Canceled (150=4)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 41,  value: '{{node:dispatch:tag41}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: '4' }
        - { tag: 39,  value: '4' }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 14,  value: '0' }
        - { tag: 151, value: '0' }
        - { tag: 6,   value: '0' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
    onSuccess: dispatch
    position: { x: 600, y: 620 }

  - id: reject-cancel
    name: OrderCancelReject - cancel
    type: SEND_FIX
    config:
      msgType: '9'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 41,  value: '{{node:dispatch:tag41}}' }
        - { tag: 39,  value: '8' }
        - { tag: 434, value: '1' }
        - { tag: 102, value: '1' }
        - { tag: 58,  value: 'Cancel rejected - unknown order' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: dispatch
    position: { x: 600, y: 740 }

  # ------------------------------------------------------------ fallbacks

  - id: reject-unsupported
    name: BusinessMessageReject
    type: SEND_FIX
    config:
      msgType: j
      fields:
        - { tag: 45,  value: '0' }
        - { tag: 372, value: '{{node:dispatch:tag35}}' }
        - { tag: 380, value: '3' }
        - { tag: 58,  value: 'Message type not supported by the FX spot venue template' }
    onSuccess: dispatch
    position: { x: 40, y: 860 }

  - id: end-idle
    name: Idle timeout
    type: END_PASS
    config: {}
    position: { x: 40, y: 980 }

edges:
  - { from: start,           to: dispatch,          label: success }
  - { from: dispatch,        to: validate-new,      label: success }
  - { from: validate-new,    to: ack-new,           label: success }
  - { from: validate-new,    to: reject-new,        label: failure }
  - { from: ack-new,         to: decide-ordtype,    label: success }
  - { from: decide-ordtype,  to: fill,              label: success }
  - { from: decide-ordtype,  to: rest-limit,        label: failure }
  - { from: fill,            to: dispatch,          label: success }
  - { from: rest-limit,      to: expire,            label: timeout }
  - { from: expire,          to: dispatch,          label: success }
  - { from: reject-new,      to: dispatch,          label: success }
  - { from: validate-amend,  to: ack-amend,         label: success }
  - { from: validate-amend,  to: reject-amend,      label: failure }
  - { from: ack-amend,       to: dispatch,          label: success }
  - { from: reject-amend,    to: dispatch,          label: success }
  - { from: validate-cancel, to: ack-cancel,        label: success }
  - { from: validate-cancel, to: reject-cancel,     label: failure }
  - { from: ack-cancel,      to: dispatch,          label: success }
  - { from: reject-cancel,   to: dispatch,          label: success }
  - { from: reject-unsupported, to: dispatch,       label: success }
  - { from: dispatch,        to: end-idle,          label: timeout }
```

Transcribe the YAML exactly. Note `156=M` (SettlCurrFxRateCalc = Multiply) and
`207=XOFF` (off-exchange MIC) are bare scalars, not quoted — that is correct
YAML and correct FIX.

- [ ] **Step 2: Verify the template parses and imports**

```bash
cd "<project>/fx-templates"
node -e "const y=require('js-yaml'),f=require('fs');const d=y.load(f.readFileSync('fx-spot-lifecycle.yaml','utf8'));console.log('nodes',d.nodes.length,'edges',d.edges.length);const ids=new Set(d.nodes.map(n=>n.id));for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])if(n[k]&&!ids.has(n[k]))throw new Error(n.id+'.'+k+' -> '+n[k]);}for(const e of d.edges){if(!ids.has(e.from)||!ids.has(e.to))throw new Error('edge '+e.from+'->'+e.to);}console.log('OK');"
```

Expected: `nodes 15 edges 21` then `OK`. Run `npm i js-yaml` in that folder first
if node cannot resolve it, or point `NODE_PATH` at `fix-flow-simulator/fix-flow-ui/node_modules`.

Then, with the simulator running:

```bash
curl -sS -X POST -F "file=@fx-spot-lifecycle.yaml" http://localhost:8080/api/v1/scenarios/import
```

Expected: HTTP 201 and a JSON body whose `id` is `7f1c0a10-0001-4a00-9c00-000000000001`.

- [ ] **Step 3: Write the README**

```markdown
# FX Lifecycle Templates — FIX Flow Simulator

Venue-side FIX 5.0 SP2 scenario templates. The simulator plays the sell-side:
it waits for orders from your application and answers with ExecutionReports.

These files live outside the `fix-flow-simulator` repository on purpose — they
are instrument content, not part of the tool.

## Import

UI: **Import** in the top bar, pick the `.yaml`.

API:
```bash
curl -X POST -F "file=@fx-spot-lifecycle.yaml" \
  http://localhost:8080/api/v1/scenarios/import
```

Each template carries a fixed UUID, so re-importing replaces rather than
duplicates.

## Session

Create a FIXT.1.1 session with `DefaultApplVerID=9`, connect it, then run the
scenario against it. The templates never write session tags
(8, 9, 10, 34, 49, 52, 56) — QuickFIX/J owns those.

## Templates

| File | Product | 167 SecurityType | 461 CFICode |
|---|---|---|---|
| `fx-spot-lifecycle.yaml` | FX Spot | `FXSPOT` | `IFXXXP` |
| `fx-forward-deliverable-lifecycle.yaml` | Deliverable FX Forward | `FXFWD` | `JFTXFP` |
| `fx-ndf-lifecycle.yaml` | Non-Deliverable Forward | `FXNDF` | `JFTXFN` |
| `fx-swap-lifecycle.yaml` | FX Swap | `FXSWAP` | `SFAXXP` |
| `fx-option-vanilla-lifecycle.yaml` | FX Vanilla Option | `OPT` + `460=4` | `HFRAVP` / `HFRDVP` |

CFI breakdown, per ISO 10962:2021:

- `IFXXXP` — I Spot, F Foreign exchange, attribute 4 P Physical (the only value the standard allows for spot FX).
- `JFTXFP` — J Forwards, F FX, T Spot underlying, X, F Forward price of underlying, P Physical.
- `JFTXFN` — as above but attribute 4 N Non-Deliverable.
- `SFAXXP` — S Swaps, F FX, A Spot-Forward swap, X, X, P Physical.
- `HFRAVP` — H Non-listed options, F FX, R Forward underlying, A European Call, V Vanilla, P Physical. Puts use `HFRDVP` (D European Put).

Two judgement calls worth checking against your own reference data:

1. FIX 5.0 SP2 has no `SecurityType` value for FX options — only `FXSPOT`,
   `FXFWD`, `FXNDF`, `FXSWAP` exist. The option template uses `167=OPT` with
   `460=4` (Currency) and lets the CFI carry the precision.
2. CFI group `HF` has no "spot" value for attribute 1. `R` (Forwards) is used,
   since a vanilla FX option prices off the outright forward. `M` (Others) is
   the alternative.

## Lifecycle covered

| Branch | Inbound | Outbound |
|---|---|---|
| Create | `D` NewOrderSingle (`AB` for swap) | ER `150=0/39=0`, then ER `150=F/39=2` |
| Amend | `G` OrderCancelReplaceRequest | ER `150=5/39=0`, or `9` OrderCancelReject `434=2` |
| Cancel | `F` OrderCancelRequest | ER `150=4/39=4`, or `9` OrderCancelReject `434=1` |
| Expiry | resting window elapses | ER `150=C/39=C` |
| NDF fixing | — | `AE` TradeCaptureReport, expects `AR` ack |
| Option exercise | `AL` PositionMaintenanceRequest | `AM` PositionMaintenanceReport |

Market orders (`40=1`) fill immediately. Limit orders (`40=2`) rest in a
`ROUTE_FIX` node that listens for cancel and amend, and expires on timeout.

## Knobs

Per template, edit in one place:

- Currency pair — the `55` values, and `600` inside the swap legs.
- Rates — `31`/`6`/`155` in the fill nodes, `202` strike in the option.
- Resting window — `timeout` on the `rest-limit` node (default 20s).
- Idle timeout — `timeout` on `dispatch` (default 120s), after which the
  execution ends PASSED.
- Dates — `{{nowdate:offset:+2d}}` style placeholders resolve at run time, so
  the templates never go stale.

## Known behaviour

A message that arrives while no node is waiting is parked in the engine's
`MessageBuffer` and replayed when the next `ROUTE_FIX` registers. This is why
every branch loops back to `dispatch`. One consequence: a cancel that arrives in
the same instant a limit order expires is processed *after* the expiry report.
```

- [ ] **Step 4: Verify end to end**

Start the simulator, import the template, create and connect a loopback FIXT.1.1
session pair, run the scenario, and from the other side send a
`35=D` market order with `167=FXSPOT`, `461=IFXXXP`, `40=1`. Expect two inbound
ExecutionReports: `150=0` then `150=F`, both carrying tags 55, 167, 461, 460, 63, 64.

- [ ] **Step 5: Do not commit**

These files are outside the repository. Confirm the repo is clean:

```bash
cd fix-flow-simulator && git status --short
```
Expected: no output — nothing from `fx-templates/` may appear here.

---

## Task 18: `fx-forward-deliverable-lifecycle` template

**Files:**
- Create: `<project>/fx-templates/fx-forward-deliverable-lifecycle.yaml`

**Interfaces:**
- Consumes: the node graph from Task 17.
- Produces: nothing later depends on it.

**Method:** copy `fx-spot-lifecycle.yaml`, then apply every change below. The node
graph, edge list and node ids are identical — only the header, the instrument
values and two extra fields differ.

- [ ] **Step 1: Copy and apply the deltas**

```bash
cd "<project>/fx-templates"
cp fx-spot-lifecycle.yaml fx-forward-deliverable-lifecycle.yaml
```

Header:

```yaml
id: 7f1c0a10-0002-4a00-9c00-000000000002
name: FX Forward (Deliverable) Lifecycle
description: Venue-side deliverable FX forward lifecycle - new, ack, fill, amend, cancel, expiry
```

Comment block: replace the instrument reference lines with

```
#   167 SecurityType = FXFWD
#   461 CFICode      = JFTXFP   (J Forwards / F FX / T Spot underlying / X / F Forward price / P Physical)
#   460 Product      = 4        (CURRENCY)
#    63 SettlType    = 6        (Future - outright forward)
#   541 MaturityDate = forward value date; 64 SettlDate matches it
```

Value substitutions, applied in **every** node that carries them
(`validate-new`, `ack-new`, `fill`, `reject-new`, `expire`, `ack-amend`, `ack-cancel`):

| Old | New |
|---|---|
| `value: FXSPOT` | `value: FXFWD` |
| `value: IFXXXP` | `value: JFTXFP` |
| `value: SPOT` (tag 762) | `value: FWD` |
| `'FX Spot {{node:dispatch:tag55}}'` | `'FX Forward {{node:dispatch:tag55}}'` |
| `{ tag: 63,  value: '0' }` | `{ tag: 63,  value: '6' }` |
| `{ tag: 64,  value: '{{nowdate:offset:+2d}}' }` | `{ tag: 64,  value: '{{nowdate:offset:+92d}}' }` |

In `validate-new`, the two instrument rules become:

```yaml
        - { tag: 167, rule: EQUALS, value: FXFWD }
        - { tag: 461, rule: EQUALS, value: JFTXFP }
        - { tag: 64,  rule: FIELD_PRESENT }
```

In `ack-new`, `fill`, `expire`, `ack-amend` and `ack-cancel`, add the maturity
date next to tag 64:

```yaml
        - { tag: 541, value: '{{nowdate:offset:+92d}}' }
```

In `fill` only, the forward rate replaces the spot rate — change tags 31, 6 and
155 from `'1.0850'` to `'1.0920'`, and add the spot reference used to derive it:

```yaml
        - { tag: 194, value: '1.0850' }
        - { tag: 195, value: '0.0070' }
```

(194 LastSpotRate, 195 LastForwardPoints — the standard FIX way to express an
outright forward's price decomposition.)

- [ ] **Step 2: Verify it parses and the graph is intact**

```bash
node -e "const y=require('js-yaml'),f=require('fs');const d=y.load(f.readFileSync('fx-forward-deliverable-lifecycle.yaml','utf8'));const ids=new Set(d.nodes.map(n=>n.id));for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])if(n[k]&&!ids.has(n[k]))throw new Error(n.id+'.'+k);}console.log('nodes',d.nodes.length,'OK');"
```

Expected: `nodes 15 OK`.

Then confirm no spot values survived the copy:

```bash
grep -n "FXSPOT\|IFXXXP\|FX Spot" fx-forward-deliverable-lifecycle.yaml
```
Expected: no output.

- [ ] **Step 3: Import and smoke test**

```bash
curl -sS -X POST -F "file=@fx-forward-deliverable-lifecycle.yaml" \
  http://localhost:8080/api/v1/scenarios/import
```
Expected: HTTP 201, id `7f1c0a10-0002-4a00-9c00-000000000002`.

Send a `35=D` with `167=FXFWD`, `461=JFTXFP`, `40=1`; expect ER `150=0` then
`150=F` carrying 541, 194 and 195.

- [ ] **Step 4: Do not commit** — the file is outside the repository.

---

## Task 19: `fx-ndf-lifecycle` template — adds the fixing branch

**Files:**
- Create: `<project>/fx-templates/fx-ndf-lifecycle.yaml`

**Interfaces:**
- Consumes: the node graph from Task 17; `NoEvents` group support (Tasks 3-5).
- Produces: nothing later depends on it.

- [ ] **Step 1: Copy the forward template and apply the instrument deltas**

```bash
cp fx-forward-deliverable-lifecycle.yaml fx-ndf-lifecycle.yaml
```

Header:

```yaml
id: 7f1c0a10-0003-4a00-9c00-000000000003
name: FX NDF Lifecycle
description: Venue-side non-deliverable forward lifecycle - new, ack, fill, amend, cancel, expiry, fixing
```

Substitutions everywhere: `FXFWD` -> `FXNDF`, `JFTXFP` -> `JFTXFN`,
`FWD` (tag 762) -> `NDF`, `'FX Forward ...'` -> `'FX NDF ...'`.

`validate-new` instrument rules become:

```yaml
        - { tag: 167, rule: EQUALS, value: FXNDF }
        - { tag: 461, rule: EQUALS, value: JFTXFN }
        - { tag: 120, rule: FIELD_PRESENT }
```

In every ExecutionReport, pin the settlement currency rather than echoing it —
an NDF settles in the deliverable leg:

```yaml
        - { tag: 120, value: USD }
```

- [ ] **Step 2: Rewire `fill` into the fixing branch**

Change `fill`'s successor:

```yaml
    onSuccess: wait-fixing
```

and add its `119` SettlCurrAmt so the fill already states the notional in the
settlement currency:

```yaml
        - { tag: 119, value: '{{node:dispatch:tag38}}' }
```

Add three nodes before `reject-new`:

```yaml
  - id: wait-fixing
    name: Wait for fixing date
    type: WAIT
    config: {}
    timeout: { value: 10, unit: SECONDS, onTimeout: CONTINUE }
    onSuccess: send-fixing
    position: { x: 1160, y: 260 }

  - id: send-fixing
    name: TradeCaptureReport - NDF fixing
    type: SEND_FIX
    config:
      msgType: AE
      fields:
        - { tag: 571,  value: 'FIX-{{seq:fixingId}}' }
        - { tag: 487,  value: '0' }
        - { tag: 856,  value: '0' }
        - { tag: 828,  value: '54' }
        - { tag: 1003, value: '{{node:dispatch:tag11}}' }
        - { tag: 17,   value: '{{uuid}}' }
        - { tag: 570,  value: 'N' }
        - { tag: 54,   value: '{{node:dispatch:tag54}}' }
        - { tag: 32,   value: '{{node:dispatch:tag38}}' }
        - { tag: 31,   value: '1.0905' }
        - { tag: 75,   value: '{{nowdate}}' }
        - { tag: 60,   value: '{{now}}' }
        - { tag: 64,   value: '{{nowdate:offset:+92d}}' }
        - { tag: 541,  value: '{{nowdate:offset:+92d}}' }
        - { tag: 119,  value: '{{node:dispatch:tag38}}' }
        - { tag: 120,  value: USD }
        - { tag: 155,  value: '1.0905' }
        - { tag: 156,  value: M }
        - { tag: 55,   value: '{{node:dispatch:tag55}}' }
        - { tag: 48,   value: '{{node:dispatch:tag55}}' }
        - { tag: 22,   value: '8' }
        - { tag: 167,  value: FXNDF }
        - { tag: 461,  value: JFTXFN }
        - { tag: 460,  value: '4' }
        - { tag: 762,  value: NDF }
        - { tag: 107,  value: 'FX NDF {{node:dispatch:tag55}}' }
        - { tag: 15,   value: '{{node:dispatch:tag15}}' }
        - { tag: 207,  value: XOFF }
      groups:
        - counterTag: 864
          entries:
            - fields:
                - { tag: 865, value: '13' }
                - { tag: 866, value: '{{nowdate:offset:+90d}}' }
                - { tag: 1578, value: 'NDF fixing date' }
    onSuccess: expect-fixing-ack
    position: { x: 1440, y: 260 }

  - id: expect-fixing-ack
    name: Expect TradeCaptureReportAck
    type: EXPECT_FIX
    config:
      msgType: AR
      correlation:
        sourceTag: 571
        fromNode: send-fixing
        targetTag: 571
    timeout: { value: 15, unit: SECONDS, onTimeout: JUMP, jumpTo: dispatch }
    onSuccess: dispatch
    position: { x: 1720, y: 260 }
```

`865=13` is EventType "First Delivery Date"; FIX 5.0 SP2 has no dedicated
"fixing" EventType, so the fixing date is carried as a dated event with the
free-text `1578` EventText naming it. Swap in your venue's convention if it
differs — that is the one place to change.

The `EXPECT_FIX` timeout jumps back to `dispatch` rather than failing, so a
counterparty that does not acknowledge fixings does not kill the run.

Add the edges:

```yaml
  - { from: wait-fixing,       to: send-fixing,       label: success }
  - { from: send-fixing,       to: expect-fixing-ack, label: success }
  - { from: expect-fixing-ack, to: dispatch,          label: success }
```

and change the existing `fill -> dispatch` edge to `fill -> wait-fixing`.

- [ ] **Step 3: Verify**

```bash
node -e "const y=require('js-yaml'),f=require('fs');const d=y.load(f.readFileSync('fx-ndf-lifecycle.yaml','utf8'));const ids=new Set(d.nodes.map(n=>n.id));for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])if(n[k]&&!ids.has(n[k]))throw new Error(n.id+'.'+k+' -> '+n[k]);}const g=d.nodes.find(n=>n.id==='send-fixing').config.groups;if(g[0].counterTag!==864)throw new Error('NoEvents group missing');console.log('nodes',d.nodes.length,'OK');"
```

Expected: `nodes 18 OK`.

- [ ] **Step 4: Import and smoke test**

Import, run, send a `35=D` NDF market order. Expect ER `150=0`, ER `150=F`, then
about ten seconds later a `35=AE` whose raw FIX contains `864=1|865=13|866=...`.
That group in the raw message is the proof Tasks 3-4 work end to end.

- [ ] **Step 5: Do not commit** — outside the repository.

---

## Task 20: `fx-swap-lifecycle` template — two legs, real `NoLegs`

**Files:**
- Create: `<project>/fx-templates/fx-swap-lifecycle.yaml`

**Interfaces:**
- Consumes: `SEND_FIX` groups (Task 3), outbound/inbound group support (Tasks 4-5),
  group-aware `VALIDATE` (Task 8), the `{{node:id:gNNN.i:tagM}}` placeholder (Task 7).
- Produces: nothing later depends on it. **This is the template the engine
  extension exists for.**

- [ ] **Step 1: Copy the archetype and change the dispatcher**

```bash
cp fx-spot-lifecycle.yaml fx-swap-lifecycle.yaml
```

Header:

```yaml
id: 7f1c0a10-0004-4a00-9c00-000000000004
name: FX Swap Lifecycle
description: Venue-side FX swap lifecycle - multileg new, ack, fill, amend, cancel, expiry
```

Comment block instrument lines:

```
#   167 SecurityType = FXSWAP
#   461 CFICode      = SFAXXP   (S Swaps / F FX / A Spot-Forward swap / X / X / P Physical)
#   460 Product      = 4        (CURRENCY)
#   Order in  : 35=AB NewOrderMultileg with NoLegs=2 (near = spot, far = forward)
#   Report out: 35=8 ExecutionReport with 442=3 and a NoLegs execution group
```

In `dispatch`, the new-order rule matches multileg instead of single:

```yaml
        - ruleId: r-new
          label: NewOrderMultileg
          matchers: { 35: AB }
          targetNodeId: validate-new
```

- [ ] **Step 2: Validate both legs**

`validate-new` rules become — note the group-aware rules from Task 8:

```yaml
      rules:
        - { tag: 11,  rule: FIELD_PRESENT }
        - { tag: 60,  rule: FIELD_PRESENT }
        - { tag: 167, rule: EQUALS, value: FXSWAP }
        - { tag: 461, rule: EQUALS, value: SFAXXP }
        - { tag: 54,  rule: ENUM, values: ['1', '2'] }
        - { tag: 38,  rule: NUMERIC_MIN, numericValue: 1 }
        - { tag: 600, groupTag: 555, index: '*', rule: FIELD_PRESENT }
        - { tag: 609, groupTag: 555, index: 0,   rule: EQUALS, value: FXSPOT }
        - { tag: 609, groupTag: 555, index: 1,   rule: EQUALS, value: FXFWD }
        - { tag: 624, groupTag: 555, index: 0,   rule: ENUM, values: ['1', '2'] }
        - { tag: 624, groupTag: 555, index: 1,   rule: ENUM, values: ['1', '2'] }
```

A swap whose near leg is not spot, or whose far leg is not a forward, is
rejected. That is exactly the check the flat-map engine could not express.

- [ ] **Step 3: Echo both legs on every report**

In `ack-new`, replace `{ tag: 40, ... }` and `{ tag: 44, ... }` (multileg orders
price at the leg level) with `{ tag: 442, value: '3' }`, keep the rest of the
instrument block with `FXSWAP` / `SFAXXP` / `762: SWAP`, and append the group:

```yaml
      groups:
        - counterTag: 555
          entries:
            - fields:
                - { tag: 600, value: '{{node:dispatch:g555.0:tag600}}' }
                - { tag: 624, value: '{{node:dispatch:g555.0:tag624}}' }
                - { tag: 609, value: FXSPOT }
                - { tag: 608, value: IFXXXP }
                - { tag: 587, value: '0' }
                - { tag: 588, value: '{{nowdate:offset:+2d}}' }
                - { tag: 687, value: '{{node:dispatch:g555.0:tag687}}' }
                - { tag: 556, value: '{{node:dispatch:g555.0:tag556}}' }
            - fields:
                - { tag: 600, value: '{{node:dispatch:g555.1:tag600}}' }
                - { tag: 624, value: '{{node:dispatch:g555.1:tag624}}' }
                - { tag: 609, value: FXFWD }
                - { tag: 608, value: JFTXFP }
                - { tag: 587, value: '6' }
                - { tag: 588, value: '{{nowdate:offset:+92d}}' }
                - { tag: 687, value: '{{node:dispatch:g555.1:tag687}}' }
                - { tag: 556, value: '{{node:dispatch:g555.1:tag556}}' }
```

In `fill`, use the same group but add the per-leg execution fields and prices:

```yaml
            - fields:
                - { tag: 600, value: '{{node:dispatch:g555.0:tag600}}' }
                - { tag: 624, value: '{{node:dispatch:g555.0:tag624}}' }
                - { tag: 609, value: FXSPOT }
                - { tag: 608, value: IFXXXP }
                - { tag: 587, value: '0' }
                - { tag: 588, value: '{{nowdate:offset:+2d}}' }
                - { tag: 687, value: '{{node:dispatch:g555.0:tag687}}' }
                - { tag: 566, value: '1.0850' }
                - { tag: 637, value: '1.0850' }
                - { tag: 1418, value: '{{node:dispatch:g555.0:tag687}}' }
            - fields:
                - { tag: 600, value: '{{node:dispatch:g555.1:tag600}}' }
                - { tag: 624, value: '{{node:dispatch:g555.1:tag624}}' }
                - { tag: 609, value: FXFWD }
                - { tag: 608, value: JFTXFP }
                - { tag: 587, value: '6' }
                - { tag: 588, value: '{{nowdate:offset:+92d}}' }
                - { tag: 687, value: '{{node:dispatch:g555.1:tag687}}' }
                - { tag: 566, value: '1.0920' }
                - { tag: 637, value: '1.0920' }
                - { tag: 1418, value: '{{node:dispatch:g555.1:tag687}}' }
```

and add `{ tag: 442, value: '3' }`, `{ tag: 195, value: '0.0070' }` to its
top-level fields. Drop tags 31, 32, 6 and 44 from `fill` — on a multileg report
the price lives on the legs.

`decide-ordtype` has no meaning for a swap (there is no top-level `40`), so
point `ack-new` straight at `fill` and delete the `decide-ordtype` and
`rest-limit` / `expire` nodes **only if you also drop the expiry branch**. Keep
them instead: change `decide-ordtype`'s condition to test the near leg's
settlement type, which is the swap analogue of "immediate or resting":

```yaml
      condition: '{{node:dispatch:g555.0:tag587}} == "0"'
```

- [ ] **Step 4: Verify**

```bash
node -e "const y=require('js-yaml'),f=require('fs');const d=y.load(f.readFileSync('fx-swap-lifecycle.yaml','utf8'));const ids=new Set(d.nodes.map(n=>n.id));for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])if(n[k]&&!ids.has(n[k]))throw new Error(n.id+'.'+k+' -> '+n[k]);}for(const id of ['ack-new','fill']){const g=d.nodes.find(n=>n.id===id).config.groups;if(!g||g[0].counterTag!==555||g[0].entries.length!==2)throw new Error(id+' must carry two legs');}console.log('nodes',d.nodes.length,'OK');"
```

Expected: `nodes 15 OK`.

- [ ] **Step 5: Import and smoke test — the decisive check**

Import and run. From the client side send:

```
35=AB|11=SWP-1|55=EUR/USD|167=FXSWAP|461=SFAXXP|460=4|54=1|38=1000000|60=<now>|
555=2|600=EUR/USD|624=1|609=FXSPOT|687=1000000|556=EUR|600=EUR/USD|624=2|609=FXFWD|687=1000000|556=EUR|
```

Expect ER `150=0` then `150=F`, each with `442=3` and `555=2` followed by two
complete leg blocks. Read the raw FIX in the bottom panel and confirm both legs
are present and well formed — a single leg, or `555=2` with one block, means
Task 4 is wrong.

- [ ] **Step 6: Do not commit** — outside the repository.

---

## Task 21: `fx-option-vanilla-lifecycle` template — adds exercise / abandon

**Files:**
- Create: `<project>/fx-templates/fx-option-vanilla-lifecycle.yaml`

**Interfaces:**
- Consumes: the node graph from Task 17; `NoPositions` group support (Tasks 3-5).
- Produces: nothing later depends on it.

- [ ] **Step 1: Copy the archetype and apply the instrument deltas**

```bash
cp fx-spot-lifecycle.yaml fx-option-vanilla-lifecycle.yaml
```

Header:

```yaml
id: 7f1c0a10-0005-4a00-9c00-000000000005
name: FX Vanilla Option Lifecycle
description: Venue-side FX vanilla option lifecycle - new, ack, fill, amend, cancel, expiry, exercise, abandon
```

Comment block:

```
#   167 SecurityType = OPT      (FIX 5.0 SP2 defines no FX-specific option value)
#   460 Product      = 4        (CURRENCY)
#   461 CFICode      = HFRAVP   European call, physical
#                      HFRDVP   European put,  physical
#                      (H Non-listed options / F FX / R Forward underlying /
#                       A|D European Call|Put / V Vanilla / P Physical)
#  1194 ExerciseStyle= 0        (European)
#  1482 OptPayoutType= 1        (Vanilla)
#    44 Price        = option premium
#
# Exercise and abandon arrive as 35=AL PositionMaintenanceRequest and are
# answered with 35=AM PositionMaintenanceReport.
```

Substitutions: `FXSPOT` -> `OPT`, `IFXXXP` -> `HFRAVP`, `SPOT` (762) -> `VANILLA`,
`'FX Spot ...'` -> `'FX Vanilla Option ...'`.

`validate-new` instrument rules become:

```yaml
        - { tag: 167, rule: EQUALS, value: OPT }
        - { tag: 460, rule: EQUALS, value: '4' }
        - { tag: 461, rule: ENUM, values: [HFRAVP, HFRDVP] }
        - { tag: 201, rule: ENUM, values: ['0', '1'] }
        - { tag: 202, rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 541, rule: FIELD_PRESENT }
```

In `ack-new`, `fill`, `expire`, `ack-amend` and `ack-cancel`, extend the
instrument block with the option terms:

```yaml
        - { tag: 201,  value: '{{node:dispatch:tag201}}' }
        - { tag: 202,  value: '{{node:dispatch:tag202}}' }
        - { tag: 947,  value: '{{node:dispatch:tag947}}' }
        - { tag: 200,  value: '{{nowdate:offset:+90d}}' }
        - { tag: 541,  value: '{{nowdate:offset:+90d}}' }
        - { tag: 1193, value: P }
        - { tag: 1194, value: '0' }
        - { tag: 1482, value: '1' }
        - { tag: 231,  value: '1' }
```

In `fill`, tags 31, 6 and 155 carry the **premium**, not an FX rate — set them to
`'0.0125'` and drop tag 155/156 (a premium is not a settlement FX rate).

- [ ] **Step 2: Add the exercise branch**

In `dispatch`, insert a fourth rule before the default:

```yaml
        - ruleId: r-exercise
          label: PositionMaintenanceRequest
          matchers: { 35: AL }
          targetNodeId: validate-exercise
```

Add four nodes:

```yaml
  - id: validate-exercise
    name: Validate exercise request
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: dispatch
      rules:
        - { tag: 710, rule: FIELD_PRESENT }
        - { tag: 709, rule: ENUM, values: ['1', '2'] }
        - { tag: 712, rule: EQUALS, value: '1' }
        - { tag: 55,  rule: FIELD_PRESENT }
        - { tag: 167, rule: EQUALS, value: OPT }
    onSuccess: decide-exercise
    onFailure: reject-exercise
    position: { x: 320, y: 860 }

  - id: decide-exercise
    name: Exercise or abandon?
    type: DECISION
    config:
      condition: '{{node:dispatch:tag709}} == "1"'
    onSuccess: report-exercise
    onFailure: report-abandon
    position: { x: 600, y: 860 }

  - id: report-exercise
    name: PositionMaintenanceReport - exercised
    type: SEND_FIX
    config:
      msgType: AM
      fields:
        - { tag: 721, value: 'PMR-{{seq:posMaintId}}' }
        - { tag: 710, value: '{{node:dispatch:tag710}}' }
        - { tag: 709, value: '1' }
        - { tag: 712, value: '1' }
        - { tag: 722, value: '0' }
        - { tag: 723, value: '0' }
        - { tag: 1,   value: '{{node:dispatch:tag1}}' }
        - { tag: 581, value: '{{node:dispatch:tag581}}' }
        - { tag: 715, value: '{{nowdate}}' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 58,  value: 'Option exercised - physical delivery of the underlying will follow' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 48,  value: '{{node:dispatch:tag55}}' }
        - { tag: 22,  value: '8' }
        - { tag: 167, value: OPT }
        - { tag: 461, value: '{{node:dispatch:tag461}}' }
        - { tag: 460, value: '4' }
        - { tag: 762, value: VANILLA }
        - { tag: 201, value: '{{node:dispatch:tag201}}' }
        - { tag: 202, value: '{{node:dispatch:tag202}}' }
        - { tag: 947, value: '{{node:dispatch:tag947}}' }
        - { tag: 541, value: '{{node:dispatch:tag541}}' }
        - { tag: 1194, value: '0' }
        - { tag: 1482, value: '1' }
        - { tag: 15,  value: '{{node:dispatch:tag15}}' }
        - { tag: 207, value: XOFF }
      groups:
        - counterTag: 702
          entries:
            - fields:
                - { tag: 703, value: OPT }
                - { tag: 704, value: '{{node:dispatch:g702.0:tag704}}' }
                - { tag: 705, value: '0' }
    onSuccess: dispatch
    position: { x: 880, y: 860 }

  - id: report-abandon
    name: PositionMaintenanceReport - abandoned
    type: SEND_FIX
    config:
      msgType: AM
      fields:
        - { tag: 721, value: 'PMR-{{seq:posMaintId}}' }
        - { tag: 710, value: '{{node:dispatch:tag710}}' }
        - { tag: 709, value: '2' }
        - { tag: 712, value: '1' }
        - { tag: 722, value: '0' }
        - { tag: 723, value: '0' }
        - { tag: 1,   value: '{{node:dispatch:tag1}}' }
        - { tag: 715, value: '{{nowdate}}' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 58,  value: 'Option abandoned - expired without exercise' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: OPT }
        - { tag: 461, value: '{{node:dispatch:tag461}}' }
        - { tag: 460, value: '4' }
        - { tag: 201, value: '{{node:dispatch:tag201}}' }
        - { tag: 202, value: '{{node:dispatch:tag202}}' }
        - { tag: 541, value: '{{node:dispatch:tag541}}' }
        - { tag: 1194, value: '0' }
    onSuccess: dispatch
    position: { x: 880, y: 980 }

  - id: reject-exercise
    name: PositionMaintenanceReport - rejected
    type: SEND_FIX
    config:
      msgType: AM
      fields:
        - { tag: 721, value: 'PMR-{{seq:posMaintId}}' }
        - { tag: 710, value: '{{node:dispatch:tag710}}' }
        - { tag: 709, value: '{{node:dispatch:tag709}}' }
        - { tag: 712, value: '1' }
        - { tag: 722, value: '2' }
        - { tag: 723, value: '99' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 58,  value: 'Position maintenance request rejected - invalid attributes' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: OPT }
        - { tag: 460, value: '4' }
    onSuccess: dispatch
    position: { x: 600, y: 980 }
```

Add the edges:

```yaml
  - { from: dispatch,          to: validate-exercise, label: success }
  - { from: validate-exercise, to: decide-exercise,   label: success }
  - { from: validate-exercise, to: reject-exercise,   label: failure }
  - { from: decide-exercise,   to: report-exercise,   label: success }
  - { from: decide-exercise,   to: report-abandon,    label: failure }
  - { from: report-exercise,   to: dispatch,          label: success }
  - { from: report-abandon,    to: dispatch,          label: success }
  - { from: reject-exercise,   to: dispatch,          label: success }
```

- [ ] **Step 3: Verify**

```bash
node -e "const y=require('js-yaml'),f=require('fs');const d=y.load(f.readFileSync('fx-option-vanilla-lifecycle.yaml','utf8'));const ids=new Set(d.nodes.map(n=>n.id));for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])if(n[k]&&!ids.has(n[k]))throw new Error(n.id+'.'+k+' -> '+n[k]);}const r=d.nodes.find(n=>n.id==='dispatch').config.rules;if(!r.some(x=>x.matchers['35']==='AL'))throw new Error('missing AL rule');console.log('nodes',d.nodes.length,'OK');"
```

Expected: `nodes 19 OK`.

- [ ] **Step 4: Import and smoke test**

Send `35=D` with `167=OPT`, `460=4`, `461=HFRAVP`, `201=1`, `202=1.10`, `947=USD`,
`541=<+90d>`, `44=0.0125`, `40=1`. Expect ER `150=0` then `150=F` with the full
option instrument block.

Then send `35=AL` with `710=PMR-1`, `709=1`, `712=1`, `55=EUR/USD`, `167=OPT`
plus a `702=1|703=OPT|704=1000000|705=0` group. Expect `35=AM` with `722=0`.
Repeat with `709=2` and expect the abandon report.

- [ ] **Step 5: Do not commit** — outside the repository.

---

## Task 21b: Business reject workflow — the trader declines to price

**Files:**
- Create: `<project>/fx-templates/fx-business-reject.yaml`
- Create: `<project>/fx-templates/prime/prime-business-reject-driver.yaml`

**Interfaces:**
- Consumes: the node graph from Task 17, the group-aware `VALIDATE` from Task 8.
- Produces: nothing later depends on it; Task 26 runs it as a sixth pair.

**Why this is a separate workflow, not a branch of the product templates.**
There are two distinct reasons Master Finance rejects an order, and conflating
them hides real defects:

| | Technical reject | Business reject |
|---|---|---|
| Cause | The order is malformed or misclassified | The order is **correct**; the trader will not price it |
| Detected by | `validate-new` failing | A pricing decision *after* validation passes |
| `103` OrdRejReason | `11` unsupported characteristic, `1` unknown symbol, `13` incorrect quantity | `0` broker/exchange option, `3` order exceeds limit, `2` exchange closed |
| Prime's reading | "I sent a bad message — fix my application" | "My message was fine — no liquidity, retry or route elsewhere" |

Tasks 17-21 and 24 cover the technical reject. This task covers the business one.
Keeping them apart means a run can prove Master Finance rejects a valid order
*for the right reason*, with `103` telling Prime which of the two happened.

**Design:** the order passes `validate-new` unchanged, then a `DECISION` chain
decides whether the desk prices it. The decision is driven by fields already on
the order, so the template is deterministic and repeatable:

- quantity above the desk limit (`38 > 5000000`) → `103=3` order exceeds limit
- an instrument outside appetite (`55` not in the quoted set) → `103=0` broker/exchange option
- otherwise → normal ack and fill

`DECISION` only supports `==`, `!=` and `contains`, so express the size test as a
`contains` check on the quantity's leading digits, or — clearer and preferred —
put the appetite test first on `55` and drive the size case from an explicit
`ExecInst`/`Account` marker Prime sets. Choose the `55` route: it is honest about
what the engine can evaluate rather than smuggling arithmetic into a string test.

- [ ] **Step 1: Write the venue template**

Copy `fx-spot-lifecycle.yaml` to `fx-business-reject.yaml` and change:

```yaml
id: 7f1c0a10-0006-4a00-9c00-000000000006
name: FX Business Reject
description: Master Finance accepts a technically valid order then declines to price it
```

Header comment:

```
# Business reject, venue side, FIX 5.0 SP2.
#
# The order is TECHNICALLY CORRECT — it passes every validation rule. Master
# Finance declines it anyway, because the trading desk will not price that
# instrument or that size. This is deliberately kept apart from the technical
# reject in the product templates: 103 OrdRejReason is what tells Prime which
# of the two happened.
#
#   103=0  broker/exchange option  - no appetite for this instrument
#   103=3  order exceeds limit     - size beyond the desk limit
#   103=2  exchange closed         - outside quoting hours
#
# Appetite is expressed by tag 55 in `decide-appetite` below: edit that
# condition to match the pairs your desk actually quotes.
```

Insert a decision chain between `validate-new` and `ack-new` — change
`validate-new`'s `onSuccess` to `decide-appetite` and add:

```yaml
  - id: decide-appetite
    name: Does the desk quote this pair?
    type: DECISION
    config:
      condition: '{{node:dispatch:tag55}} == "EUR/USD"'
    onSuccess: decide-size
    onFailure: reject-no-appetite
    position: { x: 320, y: 200 }

  - id: decide-size
    name: Is the size within the desk limit?
    type: DECISION
    config:
      # Prime marks an oversized order with ExecInst=o (deliberate desk-limit probe).
      # Arithmetic is not available in DECISION, so the size case is flagged
      # explicitly rather than inferred from tag 38.
      condition: '{{node:dispatch:tag18}} != "o"'
    onSuccess: ack-new
    onFailure: reject-exceeds-limit
    position: { x: 320, y: 320 }

  - id: reject-no-appetite
    name: ER - Rejected, no appetite (103=0)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: '8' }
        - { tag: 39,  value: '8' }
        - { tag: 103, value: '0' }
        - { tag: 58,  value: 'Order is valid but the desk is not quoting this instrument' }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 38,  value: '{{node:dispatch:tag38}}' }
        - { tag: 14,  value: '0' }
        - { tag: 151, value: '0' }
        - { tag: 6,   value: '0' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 48,  value: '{{node:dispatch:tag55}}' }
        - { tag: 22,  value: '8' }
        - { tag: 167, value: '{{node:dispatch:tag167}}' }
        - { tag: 461, value: '{{node:dispatch:tag461}}' }
        - { tag: 460, value: '4' }
        - { tag: 15,  value: '{{node:dispatch:tag15}}' }
        - { tag: 120, value: '{{node:dispatch:tag120}}' }
        - { tag: 207, value: XOFF }
    onSuccess: dispatch
    position: { x: 40, y: 320 }

  - id: reject-exceeds-limit
    name: ER - Rejected, exceeds desk limit (103=3)
    type: SEND_FIX
    config:
      msgType: '8'
      fields:
        - { tag: 37,  value: 'EX-{{seq:orderId}}' }
        - { tag: 11,  value: '{{node:dispatch:tag11}}' }
        - { tag: 17,  value: '{{uuid}}' }
        - { tag: 150, value: '8' }
        - { tag: 39,  value: '8' }
        - { tag: 103, value: '3' }
        - { tag: 58,  value: 'Order is valid but the size exceeds the desk limit' }
        - { tag: 54,  value: '{{node:dispatch:tag54}}' }
        - { tag: 38,  value: '{{node:dispatch:tag38}}' }
        - { tag: 14,  value: '0' }
        - { tag: 151, value: '0' }
        - { tag: 6,   value: '0' }
        - { tag: 60,  value: '{{now}}' }
        - { tag: 55,  value: '{{node:dispatch:tag55}}' }
        - { tag: 167, value: '{{node:dispatch:tag167}}' }
        - { tag: 461, value: '{{node:dispatch:tag461}}' }
        - { tag: 460, value: '4' }
        - { tag: 207, value: XOFF }
    onSuccess: dispatch
    position: { x: 40, y: 440 }
```

Note both reject nodes echo `167` and `461` **from the inbound order** rather
than hardcoding spot values: this template is product-agnostic on purpose, so it
can decline a forward, an NDF or an option just as well.

Edges to add, replacing `validate-new -> ack-new`:

```yaml
  - { from: validate-new,        to: decide-appetite,      label: success }
  - { from: decide-appetite,     to: decide-size,          label: success }
  - { from: decide-appetite,     to: reject-no-appetite,   label: failure }
  - { from: decide-size,         to: ack-new,              label: success }
  - { from: decide-size,         to: reject-exceeds-limit, label: failure }
  - { from: reject-no-appetite,  to: dispatch,             label: success }
  - { from: reject-exceeds-limit,to: dispatch,             label: success }
```

- [ ] **Step 2: Write the Prime driver**

`prime/prime-business-reject-driver.yaml`, id
`7f1c0a10-1006-4a00-9c00-000000001006`. It sends three orders, all technically
valid, and asserts a different outcome for each:

1. **Unquoted pair** — `55=GBP/JPY`, everything else correct. Expect `150=8`,
   `39=8`, `103=0`. Assert `461` and `167` are echoed back unchanged: a business
   reject must still identify the instrument it declined.
2. **Oversized** — `55=EUR/USD`, `18=o`, `38=25000000`. Expect `150=8`, `39=8`,
   `103=3`.
3. **Accepted** — `55=EUR/USD`, no `18`, `38=1000000`. Expect `150=0` then
   `150=F`. This is the control: it proves the two rejects above were decisions,
   not the template rejecting everything.

Each assertion follows the Task 24 pattern, and each reject assertion must
include:

```yaml
        - { tag: 150, rule: EQUALS, value: '8' }
        - { tag: 39,  rule: EQUALS, value: '8' }
        - { tag: 103, rule: EQUALS, value: '0' }   # or '3' for the oversized case
        - { tag: 58,  rule: FIELD_PRESENT }
        - { tag: 167, rule: EQUALS, ref: 'node:send-unquoted:tag167' }
        - { tag: 461, rule: EQUALS, ref: 'node:send-unquoted:tag461' }
        - { tag: 31,  rule: FIELD_ABSENT }
        - { tag: 32,  rule: FIELD_ABSENT }
```

The `103` equality is the point of the whole task: asserting merely that the
order was rejected would pass even if Master Finance had rejected it for the
wrong reason. If the `ref:` form turns out not to resolve (see the caveat in
Task 24), fall back to hardcoding the expected values.

The control order must come **last**, so a template that rejects everything fails
the run rather than passing on the two reject assertions alone.

- [ ] **Step 3: Verify both parse**

```bash
cd "<project>/fx-templates"
for f in fx-business-reject.yaml prime/prime-business-reject-driver.yaml; do
  node -e "const y=require('js-yaml'),fs=require('fs');const d=y.load(fs.readFileSync('$f','utf8'));const ids=new Set(d.nodes.map(n=>n.id));for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])if(n[k]&&!ids.has(n[k]))throw new Error('$f '+n.id+'.'+k+' -> '+n[k]);}console.log('$f',d.nodes.length,'nodes OK');"
done
```

- [ ] **Step 4: Add the pair to `verify.sh`**

Extend the `VENUE` and `DRIVER` maps in Task 26's script with
`[business]=7f1c0a10-0006-...0006` and `[business]=7f1c0a10-1006-...1006`, and add
`business` to the product loop.

- [ ] **Step 5: Do not commit** — outside the repository.

---

## Task 22: Documentation

**Files:**
- Modify: `fix-flow-simulator/docs/dsl-reference.md`
- Modify: `fix-flow-simulator/docs/api-reference.md`
- Modify: `fix-flow-simulator/docs/user-guide.md` (720 lines — find the SEND_FIX
  node config section and the feature list)
- Modify: `fix-flow-simulator/docs/developer-guide.md` (1163 lines — find the
  domain model, ports and node handler sections)
- Modify: `fix-flow-simulator/README.md`
- Modify: `fix-flow-simulator/CLAUDE.md`

**Interfaces:**
- Consumes: everything built in Tasks 1-16.
- Produces: nothing.

**This task gates the PR.** The user asked explicitly that the PR carry updated
documentation, so Task 25 must not open a PR until every file above is done.
Read each target section before editing — these are long documents and the
repeating group material has to land where a reader would look for it, not in an
appendix.

- [ ] **Step 1: Document the `groups` DSL**

In `docs/dsl-reference.md`, after the `SEND_FIX config` section, add:

````markdown
### Repeating groups

```yaml
config:
  msgType: AB
  fields:
    - { tag: 11, value: "{{uuid}}" }
  groups:
    - counterTag: 555            # NoLegs
      entries:
        - fields:
            - { tag: 600, value: EUR/USD }   # first field is the group delimiter
            - { tag: 624, value: "1" }
        - fields:
            - { tag: 600, value: EUR/USD }
            - { tag: 624, value: "2" }
          groups: []                          # entries may nest, same shape
```

The counter tag is never written by hand — QuickFIX/J maintains it from the
number of entries. The **first field of an entry is the group delimiter**, so
entry field order matters.
````

Add to the variable table:

| placeholder | meaning |
|---|---|
| `{{node:id:gNNN.i:tagM}}` | tag M of entry `i` (0-based) of group NNN on node `id` |
| `{{node:id:gNNN.i:tagM:offset:+2d}}` | same, with a date offset applied |

Add to the VALIDATE section:

```yaml
rules:
  - { tag: 609, groupTag: 555, index: 0,   rule: EQUALS, value: FXSPOT }
  - { tag: 600, groupTag: 555, index: '*', rule: FIELD_PRESENT }
```

`groupTag` absent means a top-level field. `index` defaults to `0`; `*` applies
the rule to every entry.

- [ ] **Step 2: Document the shutdown endpoint**

In `docs/api-reference.md`, add a `## System` section:

````markdown
### Shutdown the simulator
```
POST /api/v1/system/shutdown
```
Returns `202 Accepted` and terminates the JVM shortly after. Used by the
Shutdown button in the top bar. Repeated calls are idempotent — only the first
starts the exit.
````

- [ ] **Step 3: Add the Gotchas entries**

In `CLAUDE.md`, under Gotchas:

````markdown
**Repeating groups** — `FIXMessageData` carries `fields` plus `groups`
(`counterTag -> entries`), recursively. The flat `Map<Integer,String>` remains as
a top-level projection so correlation, ROUTE_FIX and DECISION are unchanged.
Never write a counter tag as a plain field: `Message.addGroup()` maintains it.
The **first field of an entry is the delimiter tag** — entry field order is
load-bearing, so use `LinkedHashMap`, never `HashMap`.

**Inbound group parsing needs the data dictionary** — `AppDataDictionary=FIX50SP2.xml`
is set for FIXT.1.1 sessions in `QuickFIXAdapter.buildSettings`. Without it
QuickFIX/J parses repeated tags flat and `getGroups()` returns empty.
`ValidateIncomingMessage=N` disables validation, not group parsing.

**Group counter tags are stripped from the flat projection** —
`QuickFIXApplicationAdapter.extractMessage` deliberately omits 555, 864 and the
rest from `flatFields()`. A ROUTE_FIX matcher on a counter tag will never match.
````

Also correct the stale line in the YAML DSL gotcha: `fields` in `SEND_FIX` accepts
**both** `Map<Integer,String>` and a list of `{tag, value}` — `SendFIXHandler`
has handled both since before this change, and the UI serialiser emits the map form.

- [ ] **Step 4: Update the user guide**

In `docs/user-guide.md`:

- Feature list / overview: add "FIX repeating groups — build and edit `NoLegs`,
  `NoEvents`, `NoPositions` and other groups visually" and "Shutdown button —
  stop the simulator from the toolbar".
- The SEND_FIX node section: document the **Repeating groups** panel — `+ Group`
  and its counter-tag picker, entry cards with add field / duplicate / reorder /
  delete, the read-only derived counter, and nested groups up to three levels.
  State plainly that the counter is never typed by hand.
- The paste-raw-FIX section: note that groups are reconstructed automatically for
  known counter tags, and that unknown counters stay flat with a warning.
- The VALIDATE node section: document the `grp` and `idx` inputs, including `*`.
- The toolbar section: document the Shutdown button and its confirmation.

- [ ] **Step 5: Update the developer guide**

In `docs/developer-guide.md`:

- Domain model: add `FIXMessageData` — its two components, the flat projection,
  and why the projection exists (backward compatibility with every persisted
  scenario).
- Ports: show the new `FIXSessionPort.sendMessage(UUID, FIXMessageData)` and
  `InboundMessageListener.onMessage(String, FIXMessageData)` signatures and note
  that the `Map` forms remain as `default` methods.
- Adapter: describe `buildMessage` / `extractMessage`, the delimiter-is-first-field
  rule, and the data dictionary requirement for inbound parsing.
- Node handlers: document the `groups` config shape for `SEND_FIX` and the
  `groupTag` / `index` keys for `VALIDATE`.
- Variable resolver: add `GroupFieldPlugin` and `GroupFieldOffsetPlugin` to the
  plugin table, noting they must be registered *before* `NodeFieldPlugin`.
- UI components: add `FieldTable` and `GroupEditor` with their props.
- REST: add the `/system/shutdown` endpoint.

- [ ] **Step 6: Update the README**

In `README.md`, extend the Features list:

```markdown
- FIX repeating groups: build, edit and validate `NoLegs`, `NoEvents`,
  `NoPositions` and nested groups from the visual editor
- Group-aware placeholders: `{{node:id:g555.0:tag600}}`
- Shutdown from the toolbar — no more killing the process
```

- [ ] **Step 7: Add the Gotchas entries and fix the stale one**

(as described in Step 3 above)

- [ ] **Step 8: Check for stale version references**

```bash
grep -rn "0\.4\.0-beta\|0\.2\.5-beta\|0\.2\.6-beta" --include=*.md --include=*.json . | grep -v node_modules
```

Note what you find but do **not** change it here — the version bump belongs to
Task 25, in one commit, so the release change is reviewable on its own.

- [ ] **Step 9: Commit**

```bash
git add docs/dsl-reference.md docs/api-reference.md docs/user-guide.md \
        docs/developer-guide.md README.md CLAUDE.md
git commit -m "docs: document repeating groups, group editor, placeholders and shutdown endpoint"
```

---

## Task 23: Full build and end-to-end verification

**Files:** none created; this task proves the previous 22.

- [ ] **Step 1: Full test suite, both stacks**

```bash
cd fix-flow-simulator
mvn -q test
cd fix-flow-ui && npm test && npx tsc --noEmit
```
Expected: all green. `tsc --noEmit` catches type drift the vitest run would miss.

- [ ] **Step 2: Build the fat JAR**

```bash
mvn clean package -DskipTests
```
Expected: `fix-flow-api/target/fix-flow-api-*.jar` produced, UI bundled.

- [ ] **Step 3: Clean-environment run**

```bash
rm -rf ./data/fixflow.*
java -Dfixflow.browser.auto-open=false -Dfixflow.no-relaunch=true \
  -jar fix-flow-api/target/fix-flow-api-*.jar
```

Per `CLAUDE.md`, never verify against a database with leftover state.

- [ ] **Step 4: Loopback session pair**

Create an ACCEPTOR (SenderCompID `SERVER`, TargetCompID `CLIENT`, port 9001,
FIXT.1.1, `DefaultApplVerID=9`) and an INITIATOR (`CLIENT`/`SERVER`, same port).
Connect the acceptor **first**, then the initiator. The acceptor showing
`connected=false` while waiting for logon is expected.

- [ ] **Step 5: Import all five templates and drive each lifecycle**

```bash
cd "<project>/fx-templates"
for f in fx-*.yaml; do
  echo "== $f"
  curl -sS -X POST -F "file=@$f" http://localhost:8080/api/v1/scenarios/import \
    -o /dev/null -w "%{http_code}\n"
done
```
Expected: `201` five times.

Then, for each template, run the scenario on the acceptor session and send the
corresponding client messages from the initiator side. Record for each:

| Template | Must observe |
|---|---|
| spot | ER 150=0, 150=F; an invalid order gives 150=8/39=8 with 103 and no 31/32; limit order expires with 150=C; cancel gives 150=4; amend gives 150=5 |
| forward | as spot, plus 541, 194, 195 on the fill |
| ndf | as forward, plus 35=AE with `864=1|865=13|866=` in the raw FIX |
| swap | ER with `442=3` and `555=2` followed by two complete leg blocks |
| option | ER with 201/202/947/1194/1482; 35=AM with 722=0 for both exercise and abandon |

- [ ] **Step 6: Verify the group editor in the browser**

Open a swap scenario, select the `fill` node, and confirm the right panel shows
`555 — NoLegs (2 entries)` with two entry cards. Change a leg's `587` value, save,
export the YAML, and confirm the edit survived the round trip. Add a third entry,
confirm the header reads `(3 entries)`, then delete it again. This is the check
the user specifically asked for: the graphical editor must be able to modify a
FIX message containing repeating groups.

- [ ] **Step 7: Verify the shutdown button**

Click **Shutdown** in the top bar, confirm the dialog, and check that the JVM
process is gone — no Task Manager needed.

- [ ] **Step 8: Final commit**

```bash
cd fix-flow-simulator
git status --short          # must show nothing from fx-templates/
git log --oneline master..HEAD
```

Then open the PR:

```bash
git push -u origin feat/fx-lifecycle-templates
gh pr create --title "FIX repeating groups + FX lifecycle template support" \
  --body "See docs/superpowers/specs/2026-08-24-fx-lifecycle-templates-design.md"
```

---

## Task 24: Prime-side counterpart templates — the complementary workflow

**Files:**
- Create: `<project>/fx-templates/prime/prime-fx-spot-driver.yaml`
- Create: `<project>/fx-templates/prime/prime-fx-forward-driver.yaml`
- Create: `<project>/fx-templates/prime/prime-fx-ndf-driver.yaml`
- Create: `<project>/fx-templates/prime/prime-fx-swap-driver.yaml`
- Create: `<project>/fx-templates/prime/prime-fx-option-driver.yaml`

**Interfaces:**
- Consumes: the venue templates from Tasks 17-21.
- Produces: five client-side scenarios that drive a full lifecycle and **assert**
  the venue's replies. Task 26 runs these against the venue templates on a second
  simulator instance.

**Naming, fixed by the user and used consistently from here on:**

| Party | Role | SenderCompID |
|---|---|---|
| **Prime** | sends the order (buy-side, client) | `PRIME` |
| **Master Finance** | receives the order and reports (sell-side, venue) | `MASTERFIN` |

The Prime scenarios run on simulator instance A; the Master Finance (venue)
scenarios from Tasks 17-21 run on instance B. Every `VALIDATE` in a Prime
template is an assertion: if Master Finance replies with the wrong `150`, `39`,
`167` or `461`, the Prime run ends FAILED and names the offending tag. That is
what makes the templates *verified* rather than merely *runnable*.

- [ ] **Step 1: Write the spot driver**

```yaml
# Prime -> Master Finance : FX Spot lifecycle driver, FIX 5.0 SP2
#
# Prime sends the orders; Master Finance answers. Every VALIDATE node here is an
# assertion on Master Finance's reply — a wrong ExecType, OrdStatus, SecurityType
# or CFICode fails the run and names the tag.
#
# Run this on the Prime simulator, against a session with
# SenderCompID=PRIME, TargetCompID=MASTERFIN.
id: 7f1c0a10-1001-4a00-9c00-000000001001
name: Prime - FX Spot Driver
description: Prime drives a full FX spot lifecycle against Master Finance and asserts every reply
version: '1.0'
sessionRef: prime

nodes:
  - id: start
    name: Start
    type: START
    config: {}
    onSuccess: send-new
    position: { x: 40, y: 40 }

  - id: send-new
    name: Prime sends NewOrderSingle
    type: SEND_FIX
    config:
      msgType: D
      fields:
        - { tag: 11,  value: 'PRIME-{{seq:clordid}}' }
        - { tag: 1,   value: PRIME-ACC-1 }
        - { tag: 55,  value: EUR/USD }
        - { tag: 48,  value: EUR/USD }
        - { tag: 22,  value: '8' }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 762, value: SPOT }
        - { tag: 15,  value: EUR }
        - { tag: 120, value: USD }
        - { tag: 54,  value: '1' }
        - { tag: 38,  value: '1000000' }
        - { tag: 40,  value: '1' }
        - { tag: 59,  value: '0' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: expect-ack
    position: { x: 320, y: 40 }

  - id: expect-ack
    name: Expect ER - New
    type: EXPECT_FIX
    config:
      msgType: '8'
      correlation: { sourceTag: 11, fromNode: send-new, targetTag: 11 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-ack
    onFailure: end-fail
    position: { x: 600, y: 40 }

  - id: assert-ack
    name: Assert ack is 150=0 / 39=0 with instrument block
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-ack
      rules:
        - { tag: 150, rule: EQUALS, value: '0' }
        - { tag: 39,  rule: EQUALS, value: '0' }
        - { tag: 37,  rule: FIELD_PRESENT }
        - { tag: 17,  rule: FIELD_PRESENT }
        - { tag: 167, rule: EQUALS, value: FXSPOT }
        - { tag: 461, rule: EQUALS, value: IFXXXP }
        - { tag: 460, rule: EQUALS, value: '4' }
        - { tag: 55,  rule: EQUALS, value: EUR/USD }
        - { tag: 64,  rule: FIELD_PRESENT }
        - { tag: 63,  rule: EQUALS, value: '0' }
    onSuccess: expect-fill
    onFailure: end-fail
    position: { x: 880, y: 40 }

  - id: expect-fill
    name: Expect ER - Trade
    type: EXPECT_FIX
    config:
      msgType: '8'
      correlation: { sourceTag: 11, fromNode: send-new, targetTag: 11 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-fill
    onFailure: end-fail
    position: { x: 1160, y: 40 }

  - id: assert-fill
    name: Assert fill is 150=F / 39=2
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-fill
      rules:
        - { tag: 150, rule: EQUALS, value: F }
        - { tag: 39,  rule: EQUALS, value: '2' }
        - { tag: 31,  rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 32,  rule: NUMERIC_MIN, numericValue: 1 }
        - { tag: 151, rule: EQUALS, value: '0' }
        - { tag: 167, rule: EQUALS, value: FXSPOT }
        - { tag: 461, rule: EQUALS, value: IFXXXP }
        - { tag: 75,  rule: FIELD_PRESENT }
    onSuccess: send-limit
    onFailure: end-fail
    position: { x: 1440, y: 40 }

  # ---- a resting limit order, so amend / cancel / expiry can be exercised ----

  - id: send-limit
    name: Prime sends a resting limit order
    type: SEND_FIX
    config:
      msgType: D
      fields:
        - { tag: 11,  value: 'PRIME-{{seq:clordid}}' }
        - { tag: 1,   value: PRIME-ACC-1 }
        - { tag: 55,  value: EUR/USD }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 15,  value: EUR }
        - { tag: 120, value: USD }
        - { tag: 54,  value: '1' }
        - { tag: 38,  value: '500000' }
        - { tag: 40,  value: '2' }
        - { tag: 44,  value: '1.0700' }
        - { tag: 59,  value: '0' }
        - { tag: 63,  value: '0' }
        - { tag: 64,  value: '{{nowdate:offset:+2d}}' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: expect-limit-ack
    position: { x: 320, y: 200 }

  - id: expect-limit-ack
    name: Expect ER - New (limit)
    type: EXPECT_FIX
    config:
      msgType: '8'
      correlation: { sourceTag: 11, fromNode: send-limit, targetTag: 11 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: send-amend
    onFailure: end-fail
    position: { x: 600, y: 200 }

  - id: send-amend
    name: Prime amends the resting order
    type: SEND_FIX
    config:
      msgType: G
      fields:
        - { tag: 41,  value: '{{node:send-limit:tag11}}' }
        - { tag: 11,  value: 'PRIME-{{seq:clordid}}' }
        - { tag: 37,  value: '{{node:expect-limit-ack:tag37}}' }
        - { tag: 55,  value: EUR/USD }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 54,  value: '1' }
        - { tag: 38,  value: '750000' }
        - { tag: 40,  value: '2' }
        - { tag: 44,  value: '1.0750' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: expect-replaced
    position: { x: 880, y: 200 }

  - id: expect-replaced
    name: Expect ER - Replaced
    type: EXPECT_FIX
    config:
      msgType: '8'
      correlation: { sourceTag: 11, fromNode: send-amend, targetTag: 11 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-replaced
    onFailure: end-fail
    position: { x: 1160, y: 200 }

  - id: assert-replaced
    name: Assert replace is 150=5 and echoes OrigClOrdID
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-replaced
      rules:
        - { tag: 150, rule: EQUALS, value: '5' }
        - { tag: 39,  rule: EQUALS, value: '0' }
        - { tag: 41,  rule: EQUALS, ref: 'node:send-amend:tag41' }
        - { tag: 38,  rule: EQUALS, value: '750000' }
        - { tag: 167, rule: EQUALS, value: FXSPOT }
    onSuccess: send-cancel
    onFailure: end-fail
    position: { x: 1440, y: 200 }

  - id: send-cancel
    name: Prime cancels the order
    type: SEND_FIX
    config:
      msgType: F
      fields:
        - { tag: 41,  value: '{{node:send-amend:tag11}}' }
        - { tag: 11,  value: 'PRIME-{{seq:clordid}}' }
        - { tag: 37,  value: '{{node:expect-replaced:tag37}}' }
        - { tag: 55,  value: EUR/USD }
        - { tag: 167, value: FXSPOT }
        - { tag: 461, value: IFXXXP }
        - { tag: 54,  value: '1' }
        - { tag: 38,  value: '750000' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: expect-canceled
    position: { x: 320, y: 360 }

  - id: expect-canceled
    name: Expect ER - Canceled
    type: EXPECT_FIX
    config:
      msgType: '8'
      correlation: { sourceTag: 11, fromNode: send-cancel, targetTag: 11 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-canceled
    onFailure: end-fail
    position: { x: 600, y: 360 }

  - id: assert-canceled
    name: Assert cancel is 150=4 / 39=4
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-canceled
      rules:
        - { tag: 150, rule: EQUALS, value: '4' }
        - { tag: 39,  rule: EQUALS, value: '4' }
        - { tag: 41,  rule: EQUALS, ref: 'node:send-cancel:tag41' }
        - { tag: 167, rule: EQUALS, value: FXSPOT }
        - { tag: 461, rule: EQUALS, value: IFXXXP }
    onSuccess: end-pass
    onFailure: end-fail
    position: { x: 880, y: 360 }

  - id: end-pass
    name: Lifecycle verified
    type: END_PASS
    config: {}
    position: { x: 1160, y: 360 }

  - id: end-fail
    name: Verification failed
    type: END_FAIL
    config: {}
    position: { x: 1160, y: 480 }

edges:
  - { from: start,            to: send-new,         label: success }
  - { from: send-new,         to: expect-ack,       label: success }
  - { from: expect-ack,       to: assert-ack,       label: success }
  - { from: expect-ack,       to: end-fail,         label: failure }
  - { from: assert-ack,       to: expect-fill,      label: success }
  - { from: assert-ack,       to: end-fail,         label: failure }
  - { from: expect-fill,      to: assert-fill,      label: success }
  - { from: expect-fill,      to: end-fail,         label: failure }
  - { from: assert-fill,      to: send-limit,       label: success }
  - { from: assert-fill,      to: end-fail,         label: failure }
  - { from: send-limit,       to: expect-limit-ack, label: success }
  - { from: expect-limit-ack, to: send-amend,       label: success }
  - { from: expect-limit-ack, to: end-fail,         label: failure }
  - { from: send-amend,       to: expect-replaced,  label: success }
  - { from: expect-replaced,  to: assert-replaced,  label: success }
  - { from: expect-replaced,  to: end-fail,         label: failure }
  - { from: assert-replaced,  to: send-cancel,      label: success }
  - { from: assert-replaced,  to: end-fail,         label: failure }
  - { from: send-cancel,      to: expect-canceled,  label: success }
  - { from: expect-canceled,  to: assert-canceled,  label: success }
  - { from: expect-canceled,  to: end-fail,         label: failure }
  - { from: assert-canceled,  to: end-pass,         label: success }
  - { from: assert-canceled,  to: end-fail,         label: failure }
```

- [ ] **Step 1b: Add the reject leg to the spot driver**

An ack-only driver never exercises `validate-new -> reject-new`, so the reject
path would ship unverified. Prime deliberately sends a **bad** order and asserts
the rejection, before moving on to the resting-limit leg.

Change `assert-fill`'s successor from `send-limit` to `send-bad`, and insert:

```yaml
  - id: send-bad
    name: Prime sends a deliberately invalid order
    type: SEND_FIX
    config:
      msgType: D
      fields:
        - { tag: 11,  value: 'PRIME-BAD-{{seq:clordid}}' }
        - { tag: 1,   value: PRIME-ACC-1 }
        - { tag: 55,  value: EUR/USD }
        # wrong instrument classification for an FX spot order:
        # SecurityType says forward, CFI says spot. Master Finance must reject.
        - { tag: 167, value: FXFWD }
        - { tag: 461, value: IFXXXP }
        - { tag: 460, value: '4' }
        - { tag: 15,  value: EUR }
        - { tag: 120, value: USD }
        - { tag: 54,  value: '1' }
        - { tag: 38,  value: '1000000' }
        - { tag: 40,  value: '1' }
        - { tag: 59,  value: '0' }
        - { tag: 60,  value: '{{now}}' }
    onSuccess: expect-reject
    position: { x: 320, y: 520 }

  - id: expect-reject
    name: Expect ER - Rejected
    type: EXPECT_FIX
    config:
      msgType: '8'
      correlation: { sourceTag: 11, fromNode: send-bad, targetTag: 11 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-reject
    onFailure: end-fail
    position: { x: 600, y: 520 }

  - id: assert-reject
    name: Assert reject is 150=8 / 39=8 with a reason
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-reject
      rules:
        - { tag: 150, rule: EQUALS, value: '8' }
        - { tag: 39,  rule: EQUALS, value: '8' }
        - { tag: 11,  rule: FIELD_PRESENT }
        - { tag: 103, rule: FIELD_PRESENT }
        - { tag: 58,  rule: FIELD_PRESENT }
        # a rejected order must not report a fill
        - { tag: 31,  rule: FIELD_ABSENT }
        - { tag: 32,  rule: FIELD_ABSENT }
    onSuccess: send-limit
    onFailure: end-fail
    position: { x: 880, y: 520 }
```

and the edges:

```yaml
  - { from: assert-fill,    to: send-bad,      label: success }
  - { from: send-bad,       to: expect-reject, label: success }
  - { from: expect-reject,  to: assert-reject, label: success }
  - { from: expect-reject,  to: end-fail,      label: failure }
  - { from: assert-reject,  to: send-limit,    label: success }
  - { from: assert-reject,  to: end-fail,      label: failure }
```

removing the old `assert-fill -> send-limit` edge.

The two `FIELD_ABSENT` rules matter: a venue that rejects an order but still
echoes `LastPx`/`LastQty` is reporting a fill on a rejected order. Asserting the
reject code alone would miss that.

**Per-product reject trigger** — each driver needs an invalid order its own venue
template will actually refuse, matched to that template's `validate-new` rules:

| Driver | Invalid order sent | Rule it trips |
|---|---|---|
| spot | `167=FXFWD` with `461=IFXXXP` | `167 EQUALS FXSPOT` |
| forward | `461=JFTXFN` (NDF CFI on a forward) | `461 EQUALS JFTXFP` |
| ndf | `38=0` (zero quantity) | `38 NUMERIC_MIN 1` |
| swap | one leg only, `609=FXSPOT`, no far leg | `609 groupTag 555 index 1 EQUALS FXFWD` |
| option | `201=2` (neither put nor call) | `201 ENUM ['0','1']` |

The swap case is the most valuable: it proves a malformed **repeating group** is
caught by the group-aware validation from Task 8, which is exactly the capability
the whole engine extension exists for. Build that one by sending a `NoLegs` group
with a single entry.

**Check before relying on `ref:`** — `EqualsRule` takes `(value, ref)` and
`ValidateHandler` reads a `ref` key, but confirm `EqualsRule` resolves `ref`
through `VariableResolver` with the `node:...:tagN` syntax. If it does not,
replace those two rules with `FIELD_PRESENT` and add a `DECISION` node comparing
`{{node:expect-replaced:tag41}} == {{node:send-amend:tag41}}`. Do not leave a
rule that silently passes.

- [ ] **Step 2: Write the four remaining drivers as deltas**

Each is the spot driver with the same substitutions applied to the *outbound*
order and the *assertions*:

| Driver | id | Order out | Assertion deltas |
|---|---|---|---|
| `prime-fx-forward-driver.yaml` | `...-1002-...001002` | `167=FXFWD`, `461=JFTXFP`, `63=6`, `64`/`541` at +92d | assert `167=FXFWD`, `461=JFTXFP`, `541` present, `194`/`195` present on the fill |
| `prime-fx-ndf-driver.yaml` | `...-1003-...001003` | `167=FXNDF`, `461=JFTXFN`, `120=USD` | as forward, plus the fixing exchange below |
| `prime-fx-swap-driver.yaml` | `...-1004-...001004` | `35=AB` with the two-leg `NoLegs` group | assert `442=3`, and group rules on the reply |
| `prime-fx-option-driver.yaml` | `...-1005-...001005` | `167=OPT`, `460=4`, `461=HFRAVP`, `201`, `202`, `947`, `541`, `44` premium | assert the option block, plus the exercise exchange below |

**NDF driver — extra nodes after `assert-fill`:**

```yaml
  - id: expect-fixing
    name: Expect TradeCaptureReport - fixing
    type: EXPECT_FIX
    config:
      msgType: AE
    timeout: { value: 30, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-fixing
    onFailure: end-fail

  - id: assert-fixing
    name: Assert fixing report carries the rate and the event group
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-fixing
      rules:
        - { tag: 571, rule: FIELD_PRESENT }
        - { tag: 487, rule: EQUALS, value: '0' }
        - { tag: 856, rule: EQUALS, value: '0' }
        - { tag: 31,  rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 155, rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 120, rule: EQUALS, value: USD }
        - { tag: 167, rule: EQUALS, value: FXNDF }
        - { tag: 461, rule: EQUALS, value: JFTXFN }
        - { tag: 865, groupTag: 864, index: 0, rule: FIELD_PRESENT }
        - { tag: 866, groupTag: 864, index: 0, rule: FIELD_PRESENT }
    onSuccess: send-fixing-ack
    onFailure: end-fail

  - id: send-fixing-ack
    name: Prime acknowledges the fixing
    type: SEND_FIX
    config:
      msgType: AR
      fields:
        - { tag: 571,  value: '{{node:expect-fixing:tag571}}' }
        - { tag: 487,  value: '0' }
        - { tag: 856,  value: '0' }
        - { tag: 939,  value: '0' }
        - { tag: 1003, value: '{{node:expect-fixing:tag1003}}' }
        - { tag: 60,   value: '{{now}}' }
    onSuccess: send-limit
```

The two `groupTag: 864` rules are the assertion that inbound group extraction
(Task 5) works: they read a field that only exists inside a repeating group.

**Swap driver — the order out carries the legs:**

```yaml
      groups:
        - counterTag: 555
          entries:
            - fields:
                - { tag: 600, value: EUR/USD }
                - { tag: 624, value: '1' }
                - { tag: 609, value: FXSPOT }
                - { tag: 608, value: IFXXXP }
                - { tag: 587, value: '0' }
                - { tag: 588, value: '{{nowdate:offset:+2d}}' }
                - { tag: 687, value: '1000000' }
                - { tag: 556, value: EUR }
            - fields:
                - { tag: 600, value: EUR/USD }
                - { tag: 624, value: '2' }
                - { tag: 609, value: FXFWD }
                - { tag: 608, value: JFTXFP }
                - { tag: 587, value: '6' }
                - { tag: 588, value: '{{nowdate:offset:+92d}}' }
                - { tag: 687, value: '1000000' }
                - { tag: 556, value: EUR }
```

and `assert-fill` asserts the echoed legs:

```yaml
        - { tag: 442,  rule: EQUALS, value: '3' }
        - { tag: 609,  groupTag: 555, index: 0, rule: EQUALS, value: FXSPOT }
        - { tag: 609,  groupTag: 555, index: 1, rule: EQUALS, value: FXFWD }
        - { tag: 637,  groupTag: 555, index: '*', rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 1418, groupTag: 555, index: '*', rule: NUMERIC_MIN, numericValue: 1 }
```

This single assertion exercises the whole chain: Prime builds a group (Task 3),
serialises it (Task 4), Master Finance parses it (Task 5), validates it (Task 8),
echoes it back through the same path, and Prime validates the echo.

**Option driver — extra nodes after `assert-fill`:**

```yaml
  - id: send-exercise
    name: Prime exercises the option
    type: SEND_FIX
    config:
      msgType: AL
      fields:
        - { tag: 710, value: 'PRIME-PMR-{{seq:pmr}}' }
        - { tag: 709, value: '1' }
        - { tag: 712, value: '1' }
        - { tag: 1,   value: PRIME-ACC-1 }
        - { tag: 581, value: '1' }
        - { tag: 715, value: '{{nowdate}}' }
        - { tag: 55,  value: EUR/USD }
        - { tag: 167, value: OPT }
        - { tag: 461, value: HFRAVP }
        - { tag: 460, value: '4' }
        - { tag: 201, value: '1' }
        - { tag: 202, value: '1.10' }
        - { tag: 947, value: USD }
        - { tag: 541, value: '{{nowdate:offset:+90d}}' }
        - { tag: 60,  value: '{{now}}' }
      groups:
        - counterTag: 702
          entries:
            - fields:
                - { tag: 703, value: OPT }
                - { tag: 704, value: '1000000' }
                - { tag: 705, value: '0' }
    onSuccess: expect-pos-report

  - id: expect-pos-report
    name: Expect PositionMaintenanceReport
    type: EXPECT_FIX
    config:
      msgType: AM
      correlation: { sourceTag: 710, fromNode: send-exercise, targetTag: 710 }
    timeout: { value: 15, unit: SECONDS, onTimeout: FAIL }
    onSuccess: assert-pos-report
    onFailure: end-fail

  - id: assert-pos-report
    name: Assert exercise accepted
    type: VALIDATE
    config:
      strictMode: false
      sourceNodeId: expect-pos-report
      rules:
        - { tag: 721, rule: FIELD_PRESENT }
        - { tag: 709, rule: EQUALS, value: '1' }
        - { tag: 722, rule: EQUALS, value: '0' }
        - { tag: 167, rule: EQUALS, value: OPT }
        - { tag: 461, rule: EQUALS, value: HFRAVP }
        - { tag: 1194, rule: EQUALS, value: '0' }
    onSuccess: send-limit
    onFailure: end-fail
```

- [ ] **Step 3: Verify all five parse and every branch terminates**

```bash
cd "<project>/fx-templates/prime"
for f in prime-*.yaml; do
  node -e "
    const y=require('js-yaml'),f=require('fs');
    const d=y.load(f.readFileSync('$f','utf8'));
    const ids=new Set(d.nodes.map(n=>n.id));
    for(const n of d.nodes){for(const k of ['onSuccess','onFailure'])
      if(n[k]&&!ids.has(n[k]))throw new Error('$f '+n.id+'.'+k+' -> '+n[k]);}
    if(!d.nodes.some(n=>n.type==='END_PASS'))throw new Error('$f has no END_PASS');
    if(!d.nodes.some(n=>n.type==='END_FAIL'))throw new Error('$f has no END_FAIL');
    console.log('$f', d.nodes.length, 'nodes OK');
  "
done
```

Expected: five `OK` lines.

- [ ] **Step 4: Do not commit** — these live outside the repository alongside the
      venue templates.

---

## Task 25: Pull request to `master` and a new beta build

**Files:**
- Modify: `pom.xml` and each module `pom.xml` (version bump)
- Modify: `fix-flow-ui/package.json` (version bump)
- Modify: `CLAUDE.md` (the run commands quote the version)

**Interfaces:**
- Consumes: Tasks 1-16 and 22-23, all green.
- Produces: a merged PR and `fix-flow-api/target/fix-flow-api-0.5.0-beta.jar`, the
  artefact Task 26 runs twice.

**Order matters, and it is the user's:** PR first, then the beta build, then
template verification against that build.

- [ ] **Step 1: Confirm docs are updated, the tree is clean and tests are green**

The user's requirement: **the PR must carry the documentation update.** Verify
Task 22 actually landed before going further:

```bash
cd fix-flow-simulator
git log --oneline master..HEAD -- docs/ README.md CLAUDE.md
grep -l "FIXMessageData\|repeating group\|Repeating groups" \
  docs/dsl-reference.md docs/api-reference.md docs/user-guide.md \
  docs/developer-guide.md README.md CLAUDE.md
```
Expected: a docs commit in the log, and all six files listed by `grep -l`. A
missing file means Task 22 is incomplete — finish it before opening the PR.

```bash
git status --short          # nothing from fx-templates/ may appear
mvn -q test
cd fix-flow-ui && npm test && npx tsc --noEmit
```

- [ ] **Step 2: Open the PR**

```bash
git push -u origin feat/fx-lifecycle-templates
gh pr create \
  --base master \
  --title "FIX repeating groups: engine, adapter and graphical editor + toolbar shutdown" \
  --body "$(cat <<'BODY'
## What

Adds end-to-end FIX repeating group support and a toolbar shutdown button.

- `FIXMessageData` carries top-level fields plus recursive `counterTag -> entries`
  groups through ports, correlation, buffer, router and handlers. The flat
  `Map<Integer,String>` survives as a projection, so every existing scenario and
  test is untouched.
- QuickFIX/J adapter builds real `quickfix.Group` objects outbound and walks
  them inbound.
- `SEND_FIX` gains a `groups` block; `VALIDATE` gains `groupTag` / `index`;
  a new `{{node:id:gNNN.i:tagM}}` placeholder reads group values.
- The graphical `SEND_FIX` editor gets full parity: a reusable `FieldTable`, a
  recursive `GroupEditor` with add / duplicate / reorder / delete per entry, a
  derived read-only counter, and group reconstruction when pasting raw FIX.
- `POST /api/v1/system/shutdown` plus a Shutdown button, so stopping the
  simulator no longer means killing the process from Task Manager.

## Documentation

Updated in the same PR: `docs/dsl-reference.md` (the `groups` block, the group
placeholders, `groupTag`/`index` validation), `docs/api-reference.md` (the
shutdown endpoint), `docs/user-guide.md` (the group editor panel, paste
behaviour, the Shutdown button), `docs/developer-guide.md` (`FIXMessageData`,
the port signatures, adapter build/extract, the new resolver plugins, the
`FieldTable` and `GroupEditor` components), `README.md` and `CLAUDE.md` gotchas.

## Why

FX swaps are multileg: `NoLegs` with two entries cannot be expressed in a flat
tag map. This unblocks venue-side FX lifecycle scenarios.

## Verification

`mvn test` and `npm test` green, including every pre-existing test unmodified —
that is the backward-compatibility proof. Design and plan:
`docs/superpowers/specs/2026-08-24-fx-lifecycle-templates-design.md`.
BODY
)"
```

`master` is this repository's default branch; `--base master` is deliberate. If
`gh` reports the base does not exist, check `gh repo view --json defaultBranchRef`
and use what it reports rather than guessing.

- [ ] **Step 3: Merge after review**

```bash
gh pr checks --watch
gh pr merge --squash --delete-branch
git checkout master && git pull
```

Do not merge while checks are red.

- [ ] **Step 4: Bump to the new beta version**

Current version is `0.4.0-beta` (per `CLAUDE.md`); this is a feature release, so
`0.5.0-beta`.

```bash
git checkout -b chore/release-0.5.0-beta
mvn versions:set -DnewVersion=0.5.0-beta -DgenerateBackupPoms=false
```

Then set `"version": "0.5.0-beta"` in `fix-flow-ui/package.json`, and update the
three `java -jar fix-flow-api-0.4.0-beta.jar` / `0.2.5-beta` references in
`CLAUDE.md` to `0.5.0-beta`.

- [ ] **Step 5: Build and verify the artefact**

```bash
mvn clean package -DskipTests
ls -l fix-flow-api/target/fix-flow-api-0.5.0-beta.jar
java -Dfixflow.browser.auto-open=false -Dfixflow.no-relaunch=true \
     -jar fix-flow-api/target/fix-flow-api-0.5.0-beta.jar &
sleep 20 && curl -sS http://localhost:8080/api/v1/scenarios -o /dev/null -w "%{http_code}\n"
```
Expected: `200`. Then stop it with the new Shutdown button — which also verifies Task 16 against the real build.

- [ ] **Step 6: Commit, PR and tag**

```bash
git commit -am "chore: release 0.5.0-beta"
git push -u origin chore/release-0.5.0-beta
gh pr create --base master --title "Release 0.5.0-beta" \
  --body "Version bump for the repeating group release."
gh pr merge --squash --delete-branch
git checkout master && git pull
git tag -a v0.5.0-beta -m "0.5.0-beta - FIX repeating groups + toolbar shutdown"
git push origin v0.5.0-beta
```

---

## Task 26: Two-simulator verification — Prime against Master Finance

**Files:**
- Create: `<project>/fx-templates/run-verification.md` (the runbook)
- Create: `<project>/fx-templates/verify.sh` (driver script)

**Interfaces:**
- Consumes: the `0.5.0-beta` JAR (Task 25), the venue templates (Tasks 17-21) and
  the Prime drivers (Task 24).
- Produces: a pass/fail matrix that Task 27 renders into the PDF.

**Topology:**

```
  Simulator A  "Prime"            Simulator B  "Master Finance"
  port 8080                       port 8081
  FIX INITIATOR                   FIX ACCEPTOR
  SenderCompID PRIME              SenderCompID MASTERFIN
  TargetCompID MASTERFIN          TargetCompID PRIME
             \___________ TCP 9001 ___________/
```

Two JVMs, one JAR. Two ports must differ (`--server.port`) and each needs its own
H2 file (`--spring.datasource.url`), or the second instance fails to lock the DB.

- [ ] **Step 1: Start both instances**

```bash
JAR=fix-flow-simulator/fix-flow-api/target/fix-flow-api-0.5.0-beta.jar
COMMON="-Dfixflow.browser.auto-open=false -Dfixflow.no-relaunch=true"

java $COMMON -jar "$JAR" \
  --server.port=8080 --spring.datasource.url=jdbc:h2:file:./data/prime &
java $COMMON -jar "$JAR" \
  --server.port=8081 --spring.datasource.url=jdbc:h2:file:./data/masterfin &
```

Wipe `./data/prime.*` and `./data/masterfin.*` first — `CLAUDE.md` is explicit
that leftover state invalidates a verification run.

- [ ] **Step 2: Create and connect the sessions — acceptor first**

On **Master Finance** (8081), an ACCEPTOR:
```json
{ "name": "MasterFinance", "mode": "ACCEPTOR", "fixVersion": "FIXT_11",
  "defaultApplVerID": "9", "senderCompID": "MASTERFIN", "targetCompID": "PRIME",
  "host": "localhost", "port": 9001, "heartbeatInterval": 30,
  "resetOnLogon": true, "resetOnLogout": false }
```

On **Prime** (8080), an INITIATOR:
```json
{ "name": "Prime", "mode": "INITIATOR", "fixVersion": "FIXT_11",
  "defaultApplVerID": "9", "senderCompID": "PRIME", "targetCompID": "MASTERFIN",
  "host": "localhost", "port": 9001, "heartbeatInterval": 30,
  "resetOnLogon": true, "resetOnLogout": false }
```

Connect Master Finance first, then Prime. The acceptor reporting
`connected=false` until logon completes is expected, per `CLAUDE.md`.

- [ ] **Step 3: Write `verify.sh`**

```bash
#!/usr/bin/env bash
# Prime (8080) drives Master Finance (8081) through every FX lifecycle.
# Exit code 0 only if all five products report PASSED on the Prime side.
set -uo pipefail

PRIME=http://localhost:8080/api/v1
MASTER=http://localhost:8081/api/v1
PRIME_SESSION="${PRIME_SESSION:?export PRIME_SESSION=<uuid>}"
MASTER_SESSION="${MASTER_SESSION:?export MASTER_SESSION=<uuid>}"

declare -A VENUE=(
  [spot]=7f1c0a10-0001-4a00-9c00-000000000001
  [forward]=7f1c0a10-0002-4a00-9c00-000000000002
  [ndf]=7f1c0a10-0003-4a00-9c00-000000000003
  [swap]=7f1c0a10-0004-4a00-9c00-000000000004
  [option]=7f1c0a10-0005-4a00-9c00-000000000005
)
declare -A DRIVER=(
  [spot]=7f1c0a10-1001-4a00-9c00-000000001001
  [forward]=7f1c0a10-1002-4a00-9c00-000000001002
  [ndf]=7f1c0a10-1003-4a00-9c00-000000001003
  [swap]=7f1c0a10-1004-4a00-9c00-000000001004
  [option]=7f1c0a10-1005-4a00-9c00-000000001005
)

echo "== importing templates"
for f in fx-*.yaml;        do curl -sS -X POST -F "file=@$f"       "$MASTER/scenarios/import" -o /dev/null -w "  $f -> %{http_code}\n"; done
for f in prime/prime-*.yaml; do curl -sS -X POST -F "file=@$f"     "$PRIME/scenarios/import"  -o /dev/null -w "  $f -> %{http_code}\n"; done

fail=0
for product in spot forward ndf swap option; do
  echo "== $product"

  venue_exec=$(curl -sS -X POST -H 'Content-Type: application/json' \
    -d "{\"sessionId\":\"$MASTER_SESSION\"}" \
    "$MASTER/scenarios/${VENUE[$product]}/execute" | sed -n 's/.*"executionId":"\([^"]*\)".*/\1/p')
  sleep 2   # let Master Finance reach its ROUTE_FIX before Prime sends

  prime_exec=$(curl -sS -X POST -H 'Content-Type: application/json' \
    -d "{\"sessionId\":\"$PRIME_SESSION\"}" \
    "$PRIME/scenarios/${DRIVER[$product]}/execute" | sed -n 's/.*"executionId":"\([^"]*\)".*/\1/p')

  status=RUNNING
  for _ in $(seq 1 60); do
    sleep 2
    status=$(curl -sS "$PRIME/executions/$prime_exec" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')
    [ "$status" = RUNNING ] || break
  done

  echo "  Prime         : $status"
  echo "  MasterFinance : $(curl -sS "$MASTER/executions/$venue_exec" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')"
  echo "  report        : $PRIME/executions/$prime_exec/report"

  if [ "$status" != PASSED ]; then
    fail=1
    curl -sS "$PRIME/executions/$prime_exec/report" > "failed-$product.json"
    echo "  !! report saved to failed-$product.json"
  fi

  curl -sS -X POST "$MASTER/executions/$venue_exec/stop" -o /dev/null
done

exit $fail
```

The Master Finance scenario is started **first** and given two seconds: it must
be parked on its `ROUTE_FIX` dispatcher before Prime sends. The engine's
`MessageBuffer` would park an early message anyway, but relying on that would
hide a real ordering bug.

- [ ] **Step 4: Run it**

```bash
cd "<project>/fx-templates"
chmod +x verify.sh
PRIME_SESSION=<uuid> MASTER_SESSION=<uuid> ./verify.sh
```

Expected: `PASSED` on the Prime side for all five, exit 0.

Any `FAILED` leaves `failed-<product>.json`; the report's `nodeResults` names the
node and the validation rule that failed, which is the whole point of putting the
assertions in the driver rather than reading raw FIX by eye.

- [ ] **Step 5: Capture the evidence for the PDF**

For each product, save the full message log from the Prime side:

```bash
for p in spot forward ndf swap option; do
  curl -sS "$PRIME/executions/<exec-id>/messages" > "messages-$p.json"
done
```

Task 27 turns these into the message tables.

- [ ] **Step 6: Write `run-verification.md`**

Capture the topology diagram, the two session payloads, the exact start commands
and the pass/fail matrix from the run. This is the runbook someone repeats after
changing a template.

---

## Task 27: PDF — messages, workflow and diagram

**Files:**
- Create: `<project>/fx-templates/doc/fx-lifecycle-reference.html`
- Create: `<project>/fx-templates/doc/build-pdf.mjs`
- Produce: `<project>/fx-templates/doc/FX-Lifecycle-Reference.pdf`

**Interfaces:**
- Consumes: the templates (Tasks 17-21, 24) and the captured message logs (Task 26).
- Produces: the deliverable PDF.

**Naming throughout the document, per the user:** **Prime** sends the order,
**Master Finance** receives it and reports.

- [ ] **Step 1: Write the HTML source**

A single self-contained file, print-styled, with:

1. **Cover** — title, date, FIX 5.0 SP2, the two parties.
2. **Parties and topology** — the Prime / Master Finance table and the connection diagram.
3. **Instrument reference** — the SecurityType / CFI table from the spec section 9,
   with the CFI breakdown and the two flagged judgement calls.
4. **One chapter per product** (spot, deliverable forward, NDF, swap, vanilla option), each with:
   - an **inline SVG sequence diagram**, Prime on the left, Master Finance on the right,
     one labelled arrow per message in time order;
   - a **message table** per message: direction, MsgType, and every tag with its
     name and value, taken from the captured logs;
   - the repeating group content rendered as an indented sub-table for swap legs,
     NDF events and option positions.
5. **Workflow diagram** — the venue node graph as an SVG flowchart:
   `dispatch` at the centre with the four branches (new / amend / cancel / exercise)
   and the loop back.
6. **Verification results** — the pass/fail matrix from Task 26.

Use `@page { size: A4; margin: 18mm }`, `page-break-before: always` on each
chapter, and `page-break-inside: avoid` on tables. Draw the diagrams as inline
`<svg>` with plain `<line>`, `<rect>`, `<text>` — no external libraries, since the
renderer has no network access.

For the sequence diagrams, the arrow set per product is exactly:

| # | From | To | Message |
|---|---|---|---|
| 1 | Prime | Master Finance | `35=D` NewOrderSingle (`35=AB` NewOrderMultileg for swap) |
| 2 | Master Finance | Prime | `35=8` ExecutionReport `150=0/39=0` — New |
| 3 | Master Finance | Prime | `35=8` ExecutionReport `150=F/39=2` — Trade |
| 4 | Master Finance | Prime | *(NDF only)* `35=AE` TradeCaptureReport — fixing |
| 5 | Prime | Master Finance | *(NDF only)* `35=AR` TradeCaptureReportAck |
| 5a | Prime | Master Finance | `35=D` **invalid** order (wrong instrument classification) |
| 5b | Master Finance | Prime | `35=8` ExecutionReport `150=8/39=8` with `103` OrdRejReason — **Rejected** |
| 6 | Prime | Master Finance | `35=D` resting limit order |
| 7 | Master Finance | Prime | `35=8` `150=0/39=0` |
| 8 | Prime | Master Finance | `35=G` OrderCancelReplaceRequest |
| 9 | Master Finance | Prime | `35=8` `150=5/39=0` — Replaced |
| 10 | Prime | Master Finance | `35=F` OrderCancelRequest |
| 11 | Master Finance | Prime | `35=8` `150=4/39=4` — Canceled |
| 12 | Master Finance | Prime | *(expiry path)* `35=8` `150=C/39=C` — Expired |
| 13 | Prime | Master Finance | *(option only)* `35=AL` PositionMaintenanceRequest `709=1` |
| 14 | Master Finance | Prime | *(option only)* `35=AM` PositionMaintenanceReport `722=0` |

- [ ] **Step 2: Write the PDF builder**

```js
// build-pdf.mjs — renders the HTML to PDF with headless Chromium.
import puppeteer from 'puppeteer';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const dir = path.dirname(fileURLToPath(import.meta.url));
const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
const page = await browser.newPage();
await page.goto('file://' + path.join(dir, 'fx-lifecycle-reference.html'),
                { waitUntil: 'networkidle0' });
await page.pdf({
  path: path.join(dir, 'FX-Lifecycle-Reference.pdf'),
  format: 'A4',
  printBackground: true,
  margin: { top: '18mm', bottom: '18mm', left: '16mm', right: '16mm' },
  displayHeaderFooter: true,
  headerTemplate: '<div style="font-size:8px;width:100%;text-align:center;color:#888">FX Lifecycle Reference — Prime / Master Finance</div>',
  footerTemplate: '<div style="font-size:8px;width:100%;text-align:center;color:#888"><span class="pageNumber"></span> / <span class="totalPages"></span></div>',
});
await browser.close();
console.log('PDF written');
```

```bash
cd "<project>/fx-templates/doc"
npm init -y && npm i puppeteer      # downloads its own Chromium
node build-pdf.mjs
```

The repo root already has `puppeteer-core`, but that expects a system Chromium
(`/usr/bin/chromium-browser` per `CLAUDE.md`) which does not exist on this
Windows workstation — hence full `puppeteer`, which brings its own binary.

**If the Chromium download is blocked**, fall back to opening
`fx-lifecycle-reference.html` in the browser and printing to PDF with the same A4
margins. Deliver the HTML alongside the PDF either way, so the document stays
editable.

- [ ] **Step 3: Verify the output**

```bash
node -e "const b=require('fs').readFileSync('FX-Lifecycle-Reference.pdf');
if(b.subarray(0,5).toString()!=='%PDF-')throw new Error('not a PDF');
console.log('pages ~', (b.toString('latin1').match(/\/Type\s*\/Page[^s]/g)||[]).length,
            'size', (b.length/1024).toFixed(0)+'KB');"
```

Then open it and check by eye: every chapter starts on a new page, no table is
split mid-row, both diagrams are legible at print size, and every arrow is
labelled with its MsgType.

- [ ] **Step 4: Deliver**

Send the PDF to the user. Keep it outside the repository with the templates.
