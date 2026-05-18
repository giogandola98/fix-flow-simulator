# Wave 1 UX Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four targeted UX improvements: delete session button (#24), clarify Wait/Delay (#11), clarify Retry/Loop (#12), add contextual tooltips (#8).

**Architecture:** All changes are UI-only except #24 which wires an existing backend endpoint. No new backend code. A shared `FieldHint` component carries tooltip text; palette items gain a `description` field for hover context.

**Tech Stack:** React 18, Zustand, TanStack Query, react-hook-form, Tailwind CSS, TypeScript

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `fix-flow-ui/src/components/FieldHint.tsx` | Reusable `?` tooltip span |
| Modify | `fix-flow-ui/src/panels/right/SessionPanel.tsx` | Add delete button + mutation |
| Modify | `fix-flow-ui/src/api/sessions.ts` | Add `deleteSession` import (already exists, verify) |
| Modify | `fix-flow-ui/src/panels/left/NodePalette.tsx` | Add `description` field + render tooltip |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/WaitConfig.tsx` | Clarify purpose + add FieldHint |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/DelayConfig.tsx` | Clarify purpose + add FieldHint |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx` | Clarify purpose + add FieldHints |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/LoopConfig.tsx` | Clarify purpose + add FieldHints |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx` | Add FieldHints |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/HttpRequestConfig.tsx` | Add FieldHints |
| Modify | `fix-flow-ui/src/panels/right/SessionPanel.tsx` | Add FieldHints on session fields |

---

### Task 1: Delete Session Button — #24

**Files:**
- Modify: `fix-flow-ui/src/panels/right/SessionPanel.tsx:168-177`

- [ ] **Step 1: Verify `deleteSession` is exported from sessions.ts**

Read `fix-flow-ui/src/api/sessions.ts`. Confirm `deleteSession` is exported. If not, add it:
```ts
export const deleteSession = (id: string) => deleteJson(`/api/v1/sessions/${id}`);
```

- [ ] **Step 2: Add `deleteSession` import to SessionPanel**

At line 8 of `SessionPanel.tsx`, add `deleteSession` to the existing import from `../../api/sessions`:
```ts
import {
  getSessions,
  createSession,
  updateSession,
  deleteSession,
  connectSession,
  disconnectSession,
  getSession,
} from '../../api/sessions';
```

- [ ] **Step 3: Add `deleteMutation` after `disconnectMutation` (around line 88)**

```tsx
const deleteMutation = useMutation({
  mutationFn: async () => {
    if (!activeSession) return;
    if (activeSession.connected) {
      await disconnectSession(activeSession.id);
    }
    await deleteSession(activeSession.id);
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['sessions'] });
    setActiveSession(null);
  },
});
```

- [ ] **Step 4: Add Delete button in the button row (lines 168-177)**

Replace the existing button row `<div className="flex gap-1">...</div>` with:
```tsx
<div className="flex gap-1">
  <button type="submit" className="flex-1 px-2 py-1 rounded bg-gray-700 hover:bg-gray-600">
    Save
  </button>
  {connected ? (
    <button
      type="button"
      className="flex-1 px-2 py-1 rounded bg-red-600 hover:bg-red-500"
      onClick={() => disconnectMutation.mutate()}
    >
      Disconnect
    </button>
  ) : (
    <button
      type="button"
      className="flex-1 px-2 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40"
      disabled={!activeSession}
      onClick={() => connectMutation.mutate()}
    >
      Connect
    </button>
  )}
  {activeSession && (
    <button
      type="button"
      className="px-2 py-1 rounded bg-red-900 hover:bg-red-800 text-red-300 text-xs"
      title="Delete this session"
      onClick={() => {
        if (window.confirm(`Delete session "${activeSession.name}"?`)) {
          deleteMutation.mutate();
        }
      }}
    >
      Del
    </button>
  )}
</div>
```

- [ ] **Step 5: Build UI and verify no TypeScript errors**

```bash
cd fix-flow-ui && npm run build 2>&1 | tail -20
```
Expected: build succeeds with no errors.

- [ ] **Step 6: Commit**

```bash
git add fix-flow-ui/src/panels/right/SessionPanel.tsx fix-flow-ui/src/api/sessions.ts
git commit -m "feat(ui): add delete button to SessionPanel (#24)

Auto-disconnects before deletion if session is connected.
Confirmation dialog prevents accidental deletion."
```

---

### Task 2: Clarify Wait vs Delay in NodePalette — #11

**Background:**
- `WAIT` uses `node.timeout` (TimeoutConfig: value + unit + onTimeout action). Blocks for a duration then takes a configurable action (proceed, fail, or jump to another node).
- `DELAY` uses `config.delayMs` (fixed milliseconds). Always sleeps then continues on the success path — no branching.

**Files:**
- Modify: `fix-flow-ui/src/panels/left/NodePalette.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/WaitConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/DelayConfig.tsx`

- [ ] **Step 1: Add `description` to PaletteItem interface and GROUPS data**

In `NodePalette.tsx`, update the `PaletteItem` interface and GROUPS:
```tsx
interface PaletteItem {
  type: NodeType;
  label: string;
  description?: string;
}

// In the Flow Control group, update WAIT and DELAY:
{ type: 'WAIT', label: 'Wait', description: 'Block until timeout; configurable on-timeout action (proceed / fail / jump).' },
{ type: 'DELAY', label: 'Delay', description: 'Fixed sleep (ms). Always continues on success — no branching.' },
{ type: 'RETRY', label: 'Retry', description: 'Re-execute a subgraph up to N times; stops on first success.' },
{ type: 'LOOP', label: 'Loop', description: 'Execute a subgraph exactly N times; all iterations must succeed.' },
```

- [ ] **Step 2: Render description as tooltip on palette items**

In the drag item `<div>`, add a `title` attribute:
```tsx
<div
  key={it.type}
  draggable
  onDragStart={(e) => onDragStart(e, it.type)}
  className="px-2 py-1 rounded cursor-grab bg-[#0f1117] border text-xs hover:bg-[#22252f]"
  style={{ borderColor: colors.node[it.type as keyof typeof colors.node] }}
  title={it.description}
>
  {it.label}
</div>
```

- [ ] **Step 3: Update WaitConfig to add purpose header**

Replace the `WaitConfig.tsx` content with:
```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface Props { node: ScenarioNode; }

export function WaitConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Blocks execution for a configurable duration. On timeout you can proceed, fail, or jump to another node.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(next) => updateNode(node.id, { timeout: next })}
        currentNodeId={node.id}
      />
    </div>
  );
}
```

- [ ] **Step 4: Update DelayConfig to add purpose header**

Replace the `DelayConfig.tsx` content with:
```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface DelayCfg { delayMs?: number; }
interface Props { node: ScenarioNode; }

export function DelayConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as DelayCfg) ?? {};

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Fixed sleep in milliseconds. Execution always continues on the success path — no timeout actions or branching.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Delay (ms)
          <span
            title="Duration to sleep in milliseconds before moving to the next node."
            className="ml-1 text-gray-600 cursor-help"
          >?</span>
        </label>
        <input
          type="number"
          min={0}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.delayMs ?? 0}
          onChange={(e) => updateNode(node.id, { config: { ...cfg, delayMs: Number(e.target.value) } })}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Build and check**

```bash
cd fix-flow-ui && npm run build 2>&1 | tail -20
```
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add fix-flow-ui/src/panels/left/NodePalette.tsx fix-flow-ui/src/panels/right/NodeConfig/WaitConfig.tsx fix-flow-ui/src/panels/right/NodeConfig/DelayConfig.tsx
git commit -m "ux: clarify Wait vs Delay blocks in palette and config panels (#11)

Wait = configurable timeout with branching action.
Delay = fixed sleep, always proceeds on success.
Descriptions added to palette hover tooltips and config panel headers."
```

---

### Task 3: Clarify Retry vs Loop in NodePalette — #12

**Background:**
- `RETRY` re-executes a target subgraph up to `maxAttempts` times; **stops as soon as one attempt succeeds**. Use when an operation may transiently fail.
- `LOOP` executes a target subgraph exactly `iterations` times unconditionally; **all iterations must succeed** or the loop fails. Use for repeated deterministic operations.

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/LoopConfig.tsx`
(NodePalette descriptions already added in Task 2, Step 1)

- [ ] **Step 1: Read current RetryConfig.tsx**

```bash
cat fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx
```

- [ ] **Step 2: Update RetryConfig to add purpose header and hints**

Full replacement for `RetryConfig.tsx`:
```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface RetryCfg { targetNodeId?: string; }
interface Props { node: ScenarioNode; }

export function RetryConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const nodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RetryCfg) ?? {};
  const policy = node.retryPolicy ?? { maxAttempts: 1, delayMs: 0 };

  const patchConfig = (patch: Partial<RetryCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const patchPolicy = (patch: Partial<typeof policy>) =>
    updateNode(node.id, { retryPolicy: { ...policy, ...patch } });

  const candidates = nodes.filter((n) => n.id !== node.id);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Re-executes the target node up to N times. Stops as soon as one attempt succeeds. Use for transient failures (e.g. network flaps).
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Target Node
          <span title="The node this Retry block will re-execute on failure." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''}
          onChange={(e) => patchConfig({ targetNodeId: e.target.value || undefined })}
        >
          <option value="">-- select node --</option>
          {candidates.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </select>
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Max Attempts
          <span title="Maximum number of times to try before giving up. Minimum 1." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input
          type="number"
          min={1}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.maxAttempts}
          onChange={(e) => patchPolicy({ maxAttempts: Math.max(1, Number(e.target.value)) })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Delay Between Retries (ms)
          <span title="Milliseconds to wait between attempts. 0 = retry immediately." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input
          type="number"
          min={0}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.delayMs}
          onChange={(e) => patchPolicy({ delayMs: Math.max(0, Number(e.target.value)) })}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Read current LoopConfig.tsx**

```bash
cat fix-flow-ui/src/panels/right/NodeConfig/LoopConfig.tsx
```

- [ ] **Step 4: Update LoopConfig to add purpose header and hints**

Full replacement for `LoopConfig.tsx`:
```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface LoopCfg { targetNodeId?: string; iterations?: number; }
interface Props { node: ScenarioNode; }

export function LoopConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const nodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as LoopCfg) ?? {};

  const candidates = nodes.filter((n) => n.id !== node.id);

  const patchConfig = (patch: Partial<LoopCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Executes the target node exactly N times. All iterations must succeed — fails immediately on the first failure.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Target Node
          <span title="The node this Loop block will execute repeatedly." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''}
          onChange={(e) => patchConfig({ targetNodeId: e.target.value || undefined })}
        >
          <option value="">-- select node --</option>
          {candidates.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </select>
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Iterations
          <span title="Number of times to execute the target node. Minimum 1." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input
          type="number"
          min={1}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.iterations ?? 1}
          onChange={(e) => patchConfig({ iterations: Math.max(1, Number(e.target.value)) })}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Build and check**

```bash
cd fix-flow-ui && npm run build 2>&1 | tail -20
```
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx fix-flow-ui/src/panels/right/NodeConfig/LoopConfig.tsx
git commit -m "ux: clarify Retry vs Loop block purpose in config panels (#12)

Retry = try until success (stops early). Loop = run N times (all must pass).
Inline description banners and ? tooltips on each field."
```

---

### Task 4: Contextual Tooltips on Config Fields — #8

Adds `?` hint spans to all major config panels: SendFIXConfig, HttpRequestConfig, SessionPanel form fields, TimeoutConfig.

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/HttpRequestConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/SessionPanel.tsx` (Field helper component)

- [ ] **Step 1: Read current SendFIXConfig.tsx**

```bash
cat fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx
```

- [ ] **Step 2: Add hints to SendFIXConfig**

Add `?` spans next to the MsgType and fields table labels:
```tsx
// Replace the MsgType label line:
<label className="text-[10px] text-gray-500">
  MsgType
  <span title="FIX tag 35 value. e.g. D = New Order Single, 8 = Execution Report, V = Market Data Request." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// Replace the Fields label:
<label className="text-[10px] text-gray-500">
  Fields
  <span title="FIX tag-value pairs to include in the message. Tag is the integer FIX field number, Value is a string. Use {{var:name}} for runtime substitution." className="ml-1 text-gray-600 cursor-help">?</span>
</label>
```

- [ ] **Step 3: Add hints to HttpRequestConfig**

Add `?` hints to Method, URL, Headers, Body labels in `HttpRequestConfig.tsx`:
```tsx
// Method label:
<label className="text-[10px] text-gray-500">
  Method
  <span title="HTTP verb. POST/PUT/PATCH/DELETE show a body editor; GET uses query params in the URL." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// URL label:
<label className="text-[10px] text-gray-500">
  URL
  <span title="Full request URL. Query params can be appended: ?key=value. Supports {{var:name}} substitution." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// Headers label:
<label className="text-[10px] text-gray-500">
  Headers
  <span title="HTTP request headers. Common: Content-Type: application/json, Authorization: Bearer &lt;token&gt;." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// Body label:
<label className="text-[10px] text-gray-500">
  Body
  <span title="Request body (JSON, XML, plain text). Supports {{var:name}} substitution." className="ml-1 text-gray-600 cursor-help">?</span>
</label>
```

- [ ] **Step 4: Read TimeoutConfig.tsx**

```bash
cat fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx
```

- [ ] **Step 5: Add hints to TimeoutConfig**

Add `?` hint spans to the Value, Unit, On Timeout, and Jump To labels:
```tsx
// Timeout Value label:
<label className="text-[10px] text-gray-500">
  Timeout Value
  <span title="How long to wait before triggering the timeout action." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// Timeout Unit label:
<label className="text-[10px] text-gray-500">
  Unit
  <span title="Time unit for the timeout value." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// On Timeout label:
<label className="text-[10px] text-gray-500">
  On Timeout
  <span title="Action to take when the timeout fires: PROCEED continues to onSuccess, FAIL marks the node failed, JUMP goes to the specified node." className="ml-1 text-gray-600 cursor-help">?</span>
</label>

// Jump To label (when visible):
<label className="text-[10px] text-gray-500">
  Jump To Node
  <span title="Node to execute when On Timeout is set to JUMP." className="ml-1 text-gray-600 cursor-help">?</span>
</label>
```

- [ ] **Step 6: Add hints to SessionPanel Field labels**

Update the `Field` helper at the bottom of `SessionPanel.tsx` to accept an optional `hint` prop, then add hints to key fields:

```tsx
// Updated Field helper:
function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-[10px] text-gray-500">
        {label}
        {hint && <span title={hint} className="ml-1 text-gray-600 cursor-help">?</span>}
      </label>
      {children}
    </div>
  );
}

// Add hint props to key fields:
<Field label="Mode" hint="INITIATOR: connects to a counterparty. ACCEPTOR: listens for incoming FIX connections.">
<Field label="FIX Version" hint="FIX protocol version. FIX 4.4 is the most common. FIXT.1.1 (FIX 5.0 SP2) requires a DefaultApplVerID.">
<Field label="SenderCompID" hint="Your CompID — identifies this side of the FIX session (tag 49).">
<Field label="TargetCompID" hint="Counterparty CompID — identifies the remote side (tag 56).">
<Field label="Host" hint="IP address or hostname of the ACCEPTOR. Only relevant for INITIATOR mode.">
<Field label="Port" hint="TCP port. ACCEPTOR listens; INITIATOR connects to it.">
<Field label="Heartbeat Interval (sec)" hint="Seconds between heartbeat messages (tag 108). Standard is 30.">
<Field label="Reconnect Interval (sec)" hint="Seconds to wait before reconnecting after a disconnection (INITIATOR only).">
```

- [ ] **Step 7: Build and verify no TypeScript errors**

```bash
cd fix-flow-ui && npm run build 2>&1 | tail -30
```
Expected: clean build, no TS errors.

- [ ] **Step 8: Commit**

```bash
git add fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/HttpRequestConfig.tsx \
        fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx \
        fix-flow-ui/src/panels/right/SessionPanel.tsx
git commit -m "ux: add contextual ? tooltips to all config fields (#8)

Hover hints on every field: FIX tag meanings, URL/body format,
timeout units, session CompIDs, mode explanation."
```

---

## Self-Review

**Spec coverage:**
- #24 ✅ Delete button + auto-disconnect + confirmation
- #11 ✅ Distinction documented in palette + panel headers
- #12 ✅ Distinction documented in palette + panel headers
- #8 ✅ Tooltips on all major config surfaces (Send FIX, HTTP, Timeout, Session, Delay, Retry, Loop)

**Placeholder scan:** No TBD, no TODO, all code blocks complete.

**Type consistency:**
- `RetryConfig`: uses `node.retryPolicy` (matches `ScenarioNode` record field)
- `LoopConfig`: uses `config.iterations` (matches `LoopHandler.java` config key)
- `deleteMutation`: returns `void` (matches API — deleteJson returns void)
- `Field` helper: `hint?: string` is optional, no breaking change to existing usage
