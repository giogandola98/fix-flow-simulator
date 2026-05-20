# Wave 2 Feature Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three FIX editor features: paste raw FIX into Send FIX block (#13), variable placeholder reference panel in field editor (#15), UX clarity improvements for Expect FIX and Validate blocks (#14).

**Architecture:** All UI-only changes. No backend modifications. New shared utility `parseFIXMessage.ts`. New shared component `VarRefPanel.tsx`. All changes are additive to existing config panels.

**Tech Stack:** React 18, TypeScript, Zustand, Tailwind CSS

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `fix-flow-ui/src/lib/parseFIXMessage.ts` | Parse raw FIX string → `{msgType, fields[]}` |
| Create | `fix-flow-ui/src/panels/right/NodeConfig/VarRefPanel.tsx` | Collapsible variable placeholder reference |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx` | Add paste FIX button + VarRefPanel |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx` | Add banner + ? hints + matchers table |
| Modify | `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx` | Add banner + ? hints on rules |

---

### Task 1: FIX Message Paste Parser — #13

**Files:**
- Create: `fix-flow-ui/src/lib/parseFIXMessage.ts`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`

- [ ] **Step 1: Create `parseFIXMessage.ts`**

```ts
// fix-flow-ui/src/lib/parseFIXMessage.ts
export interface ParsedFIX {
  msgType?: string;
  fields: Array<{ tag: number; value: string }>;
  skipped: number;
}

// Tags managed by the FIX engine — skip them on paste
const ENGINE_TAGS = new Set([8, 9, 10, 49, 56]);

export function parseFIXMessage(raw: string): ParsedFIX {
  const normalized = raw.replace(//g, '|');
  const segments = normalized.split('|').map(s => s.trim()).filter(Boolean);

  let msgType: string | undefined;
  const fields: Array<{ tag: number; value: string }> = [];
  let skipped = 0;

  for (const seg of segments) {
    const eq = seg.indexOf('=');
    if (eq < 0) { skipped++; continue; }
    const tag = parseInt(seg.slice(0, eq).trim(), 10);
    const value = seg.slice(eq + 1);
    if (isNaN(tag) || tag <= 0) { skipped++; continue; }
    if (tag === 35) { msgType = value; continue; }
    if (ENGINE_TAGS.has(tag)) { skipped++; continue; }
    fields.push({ tag, value });
  }

  return { msgType, fields, skipped };
}
```

- [ ] **Step 2: Build to verify**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator/fix-flow-ui && npm run build 2>&1 | grep -E 'error|warning|✓'
```

- [ ] **Step 3: Read `SendFIXConfig.tsx` in full**

- [ ] **Step 4: Add paste FIX UI to `SendFIXConfig.tsx`**

Add a collapsible "Paste FIX Message" section between MsgType and Fields. State: `showPaste` (boolean), `pasteRaw` (string), `parseError` (string).

Full replacement for `SendFIXConfig.tsx`:
```tsx
import { useState } from 'react';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';
import { parseFIXMessage } from '../../../lib/parseFIXMessage';

interface FieldRow { tag: number; value: string; }
interface SendCfg { msgType?: string; fields?: FieldRow[]; }
interface Props { node: ScenarioNode; }

export function SendFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as SendCfg) ?? {};
  const fields = cfg.fields ?? [];
  const [showPaste, setShowPaste] = useState(false);
  const [pasteRaw, setPasteRaw] = useState('');
  const [parseError, setParseError] = useState('');

  const patchConfig = (patch: Partial<SendCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateField = (i: number, patch: Partial<FieldRow>) => {
    const next = fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f));
    patchConfig({ fields: next });
  };
  const addField = () => patchConfig({ fields: [...fields, { tag: 0, value: '' }] });
  const removeField = (i: number) => patchConfig({ fields: fields.filter((_, idx) => idx !== i) });

  const handleParse = () => {
    if (!pasteRaw.trim()) { setParseError('Paste a FIX message first'); return; }
    try {
      const result = parseFIXMessage(pasteRaw);
      const updates: Partial<SendCfg> = { fields: result.fields };
      if (result.msgType) updates.msgType = result.msgType;
      patchConfig(updates);
      setPasteRaw('');
      setParseError(result.skipped > 0 ? `Parsed OK — ${result.skipped} segment(s) skipped (engine-managed or malformed)` : '');
      setShowPaste(false);
    } catch (e) {
      setParseError(`Parse error: ${e instanceof Error ? e.message : String(e)}`);
    }
  };

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          MsgType (tag 35)
          <span title="FIX tag 35 value. e.g. D = New Order Single, 8 = Execution Report, V = Market Data Request." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''} onChange={(e) => patchConfig({ msgType: e.target.value })} />
      </div>

      {/* ── Paste FIX ──────────────────────────────────────── */}
      <div className="border border-[#2a2d3a] rounded">
        <button
          type="button"
          className="w-full flex items-center justify-between px-2 py-1 text-[10px] text-gray-400 hover:text-gray-300"
          onClick={() => setShowPaste(v => !v)}
        >
          <span>Paste FIX Message</span>
          <span>{showPaste ? '▲' : '▼'}</span>
        </button>
        {showPaste && (
          <div className="px-2 pb-2 space-y-1">
            <div className="text-[10px] text-gray-500 italic">
              Paste a raw FIX message (SOH-separated or pipe-separated). Tags 8/9/10/49/56 are skipped — managed by the engine.
            </div>
            <textarea
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono text-[10px] resize-y"
              rows={3}
              placeholder="8=FIX.4.4|35=D|49=CLIENT|56=SERVER|11=ORD-001|55=AAPL|54=1|38=100|40=2|"
              value={pasteRaw}
              onChange={e => { setPasteRaw(e.target.value); setParseError(''); }}
            />
            {parseError && (
              <div className={`text-[10px] ${parseError.startsWith('Parse') ? 'text-red-400' : 'text-yellow-400'}`}>
                {parseError}
              </div>
            )}
            <div className="flex gap-1">
              <button
                type="button"
                className="flex-1 px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded text-[10px]"
                onClick={handleParse}
              >
                Parse → populate fields
              </button>
              <button
                type="button"
                className="px-2 py-0.5 bg-gray-700 hover:bg-gray-600 rounded text-[10px]"
                onClick={() => { setPasteRaw(''); setParseError(''); setShowPaste(false); }}
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Fields table ───────────────────────────────────── */}
      <div>
        <div className="flex items-center justify-between">
          <label className="text-[10px] text-gray-500">
            Fields
            <span title="FIX tag-value pairs. Tag is the integer field number; Value is a string. Use {{var:name}} for runtime substitution." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded" onClick={addField}>+ Field</button>
        </div>
        <table className="w-full mt-1">
          <thead className="text-[10px] text-gray-500">
            <tr><th className="text-left">Tag</th><th className="text-left">Value</th><th /></tr>
          </thead>
          <tbody>
            {fields.map((f, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.tag} onChange={(e) => updateField(i, { tag: Number(e.target.value) })} />
                </td>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.value} onChange={(e) => updateField(i, { value: e.target.value })} />
                </td>
                <td>
                  <button className="text-red-400 hover:text-red-300 text-xs" onClick={() => removeField(i)}>x</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
```

- [ ] **Step 5: Build and verify no TypeScript errors**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator/fix-flow-ui && npm run build 2>&1 | tail -15
```

- [ ] **Step 6: Commit**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator
git add fix-flow-ui/src/lib/parseFIXMessage.ts fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx
git commit -m "feat(ui): paste raw FIX message into Send FIX block (#13)

Collapsible paste section accepts SOH or pipe-separated FIX.
Skips engine-managed tags (8/9/10/49/56). Populates msgType + fields.
Shows count of skipped segments."
```

---

### Task 2: Variable Placeholder Reference Panel — #15

**Files:**
- Create: `fix-flow-ui/src/panels/right/NodeConfig/VarRefPanel.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx` (add VarRefPanel below fields table)

- [ ] **Step 1: Create `VarRefPanel.tsx`**

```tsx
// fix-flow-ui/src/panels/right/NodeConfig/VarRefPanel.tsx
import { useState } from 'react';

interface VarEntry {
  syntax: string;
  description: string;
  example: string;
  category: string;
}

const VARS: VarEntry[] = [
  { syntax: '{{now}}', description: 'Current UTC ISO timestamp', example: '{{now}}', category: 'Time' },
  { syntax: '{{uuid}}', description: 'Random UUID v4', example: '{{uuid}}', category: 'Random' },
  { syntax: '{{seq:name}}', description: 'Monotonic counter, keyed by name', example: '{{seq:orderId}}', category: 'Sequence' },
  { syntax: '{{env:VAR}}', description: 'Environment variable value', example: '{{env:SENDER_ID}}', category: 'Env' },
  { syntax: '{{node:id:tagN}}', description: 'Tag N value from a previous node', example: '{{node:send-order:tag11}}', category: 'Cross-node' },
  { syntax: '{{node:id:tagN:offset:+5m}}', description: 'Tag value with time offset (s/m/h/d)', example: '{{node:send-order:tag60:offset:+1h}}', category: 'Cross-node' },
];

export function VarRefPanel() {
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  const copy = async (syntax: string) => {
    try {
      await navigator.clipboard.writeText(syntax);
      setCopied(syntax);
      setTimeout(() => setCopied(null), 1500);
    } catch {
      setCopied(null);
    }
  };

  return (
    <div className="border border-[#2a2d3a] rounded">
      <button
        type="button"
        className="w-full flex items-center justify-between px-2 py-1 text-[10px] text-gray-400 hover:text-gray-300"
        onClick={() => setOpen(v => !v)}
      >
        <span>Variable Reference</span>
        <span>{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="px-2 pb-2 space-y-1">
          <div className="text-[10px] text-gray-500 italic mb-1">
            Click to copy a placeholder into the clipboard, then paste into a Value field.
          </div>
          {VARS.map((v) => (
            <div key={v.syntax} className="flex items-start gap-1 group">
              <button
                type="button"
                className="shrink-0 font-mono text-[10px] px-1 py-0.5 bg-[#0f1117] border border-[#2a2d3a] rounded text-blue-400 hover:border-blue-500 hover:text-blue-300"
                title={`Copy ${v.syntax}`}
                onClick={() => copy(v.example)}
              >
                {copied === v.example ? '✓' : v.syntax}
              </button>
              <div className="min-w-0">
                <div className="text-[10px] text-gray-400 leading-tight">{v.description}</div>
                {v.example !== v.syntax && (
                  <div className="text-[9px] text-gray-600 font-mono">{v.example}</div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Add `VarRefPanel` to `SendFIXConfig.tsx`**

Import and place `<VarRefPanel />` between the Fields table and the `TimeoutConfig`:
```tsx
import { VarRefPanel } from './VarRefPanel';

// In JSX, after the </div> closing the fields table, before <TimeoutConfig>:
<VarRefPanel />
<TimeoutConfig ... />
```

- [ ] **Step 3: Build and verify**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator/fix-flow-ui && npm run build 2>&1 | tail -15
```

- [ ] **Step 4: Commit**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator
git add fix-flow-ui/src/panels/right/NodeConfig/VarRefPanel.tsx fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx
git commit -m "feat(ui): add variable placeholder reference panel to Send FIX (#15)

Collapsible Variable Reference section lists all supported {{...}}
placeholders with descriptions and click-to-copy."
```

---

### Task 3: Clarify Expect FIX and Validate blocks — #14

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`

- [ ] **Step 1: Read both files in full**

- [ ] **Step 2: Replace `ExpectFIXConfig.tsx` completely**

```tsx
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface CorrelationCfg { sourceTag?: number; fromNode?: string; targetTag?: number; }
interface ExpectCfg { msgType?: string; correlation?: CorrelationCfg; }

export function ExpectFIXConfig({ node }: { node: ScenarioNode }) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as ExpectCfg) ?? {};
  const corr = cfg.correlation ?? {};

  const patchConfig = (patch: Partial<ExpectCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });
  const patchCorr = (patch: Partial<CorrelationCfg>) =>
    patchConfig({ correlation: { ...corr, ...patch } });

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Waits for an inbound FIX message matching the criteria. Stores matched fields for downstream VALIDATE or cross-node references.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          MsgType (tag 35)
          <span title="Required. FIX tag 35 of the message to wait for. e.g. 8 = Execution Report, W = Market Data Snapshot." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''} onChange={(e) => patchConfig({ msgType: e.target.value })} />
      </div>
      <div className="border border-[#2a2d3a] rounded p-2">
        <div className="text-[10px] uppercase text-gray-500 mb-1">
          Correlation
          <span title="Correlation links this Expect block to a previously sent message. The engine only accepts an inbound message whose Source Tag value matches the Target Tag value from the referenced send node." className="ml-1 normal-case text-gray-600 cursor-help">?</span>
        </div>
        <div className="text-[10px] text-gray-500 italic mb-2">
          Optional. Use to match a response back to a specific sent order (e.g. match ClOrdID tag 11 in the reply against tag 11 sent in the order node).
        </div>
        <div>
          <label className="text-[10px] text-gray-500">
            Source Tag (in received message)
            <span title="The FIX tag number in the inbound message whose value is checked for correlation. e.g. 11 for ClOrdID." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.sourceTag ?? 0} onChange={(e) => patchCorr({ sourceTag: Number(e.target.value) })} />
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">
            From Node
            <span title="The Send FIX node whose outbound tag value is used as the expected correlation value." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.fromNode ?? ''} onChange={(e) => patchCorr({ fromNode: e.target.value })}>
            <option value="">-- none --</option>
            {allNodes.filter((n) => n.id !== node.id).map((n) => (
              <option key={n.id} value={n.id}>{n.name}</option>
            ))}
          </select>
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">
            Target Tag (in send node)
            <span title="The FIX tag number in the referenced send node whose outbound value must match the Source Tag in the reply." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.targetTag ?? 0} onChange={(e) => patchCorr({ targetTag: Number(e.target.value) })} />
        </div>
      </div>
      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
```

- [ ] **Step 3: Update `ValidateConfig.tsx` — add banner and ? hints**

Add a description banner at the top and `?` hints to Strict Mode and each rule type label. Insert the banner as the first child of the outer `<div>`:

```tsx
// Banner (add as first element inside outer div):
<div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
  Validates fields of a stored message (from an Expect FIX or incoming ROUTE_FIX node). Rules run in order; all must pass unless Strict Mode is off.
</div>
```

Update Strict Mode label to:
```tsx
<label className="flex items-center gap-2">
  <input type="checkbox" checked={cfg.strictMode ?? false} onChange={(e) => patchConfig({ strictMode: e.target.checked })} />
  Strict Mode
  <span title="When enabled, any field in the received message not covered by a rule causes validation failure." className="text-[10px] text-gray-600 cursor-help">?</span>
</label>
```

Add a hint to the Rules section label:
```tsx
<div className="text-[10px] uppercase text-gray-500">
  Rules
  <span title="Each rule checks one FIX tag. EQUALS/NOT_EQUALS: exact string match. ENUM: value in list. REGEX: pattern match. NUMERIC_MIN/MAX: numeric bounds. FIELD_PRESENT/ABSENT: existence check. DATE_RULE: timestamp validation." className="ml-1 normal-case text-gray-600 cursor-help">?</span>
</div>
```

Add a `ref` field hint:
Find the `ref` input placeholder text "cross-node ref (optional)" and add a hint to its label:
```tsx
<label className="text-[10px] text-gray-500">
  Cross-node ref
  <span title="Compare this tag against a value from another node. Syntax: {{node:nodeId:tagN}}. Leave blank to use the static Value above." className="ml-1 text-gray-600 cursor-help">?</span>
</label>
// Followed by the existing ref input (remove the placeholder text approach and use proper label+input)
```

Actually, the ref input currently has no label — just a placeholder. Add the label+hint above it inside each rule's `<div>`:
```tsx
{/* Inside each rule div, before the ref input: */}
<div className="mt-1">
  <label className="text-[10px] text-gray-500">
    Cross-node ref
    <span title="Compare this tag against a value from another node. Syntax: {{node:nodeId:tagN}}. Leave blank to use the static Value above." className="ml-1 text-gray-600 cursor-help">?</span>
  </label>
  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
    value={r.ref ?? ''} onChange={(e) => updateRule(i, { ref: e.target.value })} placeholder="{{node:send-order:tag11}}" />
</div>
```

- [ ] **Step 4: Build and verify**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator/fix-flow-ui && npm run build 2>&1 | tail -15
```

- [ ] **Step 5: Commit**

```bash
cd /home/giorgio/Documenti/fix-flow-simulator
git add fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx
git commit -m "ux: clarify Expect FIX and Validate block config panels (#14)

Expect FIX: purpose banner, ? hints on all correlation fields with
examples (ClOrdID matching pattern).
Validate: purpose banner, Strict Mode hint, Rules section hint,
cross-node ref field labeled with syntax example."
```

---

## Self-Review

**Spec coverage:**
- #13 ✅ Paste FIX button, SOH+pipe parse, auto-populate msgType+fields, skip engine tags, error feedback, cancel
- #15 ✅ Variable reference panel, all 6 placeholder types, click-to-copy, collapsible
- #14 ✅ Banners on both panels, ? hints on all fields, cross-node ref syntax example, strict mode explained

**Placeholder scan:** No TBD. All code blocks complete.

**Type consistency:**
- `parseFIXMessage` returns `ParsedFIX` with `fields: Array<{tag: number; value: string}>` — matches `FieldRow` in `SendFIXConfig`
- `VarRefPanel` uses only local state, no props, no store dependency
- `ExpectFIXConfig` correlation types unchanged — same `CorrelationCfg` interface
- `ValidateConfig` rule types unchanged — same `ValidationRule` and `ValidateCfg` interfaces
