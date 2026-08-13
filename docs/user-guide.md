# FIX Flow Simulator — User Guide

**Version:** 0.4.0-beta

---

## Table of Contents

1. [What is FIX Flow Simulator?](#1-what-is-fix-flow-simulator)
2. [Quick Start — First Workflow in 5 Minutes](#2-quick-start--first-workflow-in-5-minutes)
3. [The Interface at a Glance](#3-the-interface-at-a-glance)
4. [Sessions — Connecting to FIX](#4-sessions--connecting-to-fix)
5. [Scenarios — Building Workflows](#5-scenarios--building-workflows)
6. [Block Reference — Every Block Explained](#6-block-reference--every-block-explained)
7. [Connecting Blocks — Drawing Edges](#7-connecting-blocks--drawing-edges)
8. [Variables and Placeholders](#8-variables-and-placeholders)
9. [Running a Scenario](#9-running-a-scenario)
10. [Reading Results — Events, Messages, Statistics](#10-reading-results--events-messages-statistics)
11. [Import and Export](#11-import-and-export)
12. [FIX Session Configuration Guide](#12-fix-session-configuration-guide)
13. [FIX Terminology Glossary](#13-fix-terminology-glossary)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. What is FIX Flow Simulator?

FIX Flow Simulator is a graphical tool for **testing FIX protocol workflows**. You build a scenario by dragging blocks onto a canvas, connecting them with arrows, and clicking **Run**. The simulator then executes the scenario — sending FIX messages, waiting for replies, validating fields, and recording every event.

**Typical use cases:**
- Validate that your trading system responds correctly to a New Order Single
- Simulate an Execution Report and verify your OMS processes it
- Automate regression tests for FIX connectivity

**You do not need to write code.** Everything is visual.

---

## 2. Quick Start — First Workflow in 5 Minutes

This section walks you through the minimum steps to send a FIX message and see the result.

### Step 1 — Create a session

1. In the **right panel**, click the **Sessione attiva** dropdown and select `-- nuova sessione --`
2. Fill in:
   - **Mode**: `INITIATOR` (your system connects to the counterparty)
   - **SenderCompID**: e.g. `CLIENT`
   - **TargetCompID**: e.g. `SERVER`
   - **Host**: the address of the acceptor (e.g. `127.0.0.1`)
   - **Port**: e.g. `9001`
3. Click **Save** (or **Salva**)
4. Click **Connect** (or **Connetti**) — the status dot turns green when connected

### Step 2 — Create a scenario

1. In the left panel under **Scenari**, click **+ New** (or **+ Nuovo**)
2. A new scenario opens with a **Start**, **End Pass**, and **End Fail** block already placed

### Step 3 — Add a Send FIX block

1. In the **Palette** on the left, find **Send FIX** (or **Invia FIX**)
2. Drag it onto the canvas between **Start** and **End Pass**
3. Click the Send FIX block to open its properties in the right panel
4. Click **Paste FIX Message**, paste a raw FIX string, then click **Parse → populate fields**
   - Example: `8=FIX.4.4|35=D|49=CLIENT|56=SERVER|11=ORD-001|55=AAPL|54=1|38=100|40=2`
5. The fields are parsed into the tag/value table automatically

### Step 4 — Connect the blocks

Draw arrows: **Start → Send FIX → End Pass**. To draw an arrow, hover over a block until a small circle appears at the bottom edge, then drag from that circle to the next block.

### Step 5 — Run

1. Click **Run** (or **Esegui**) in the top bar
2. Watch the blocks highlight as the scenario executes
3. Switch to the **Events** tab at the bottom to see real-time execution events
4. Switch to **FIX Messages** to see the message that was sent

---

## 3. The Interface at a Glance

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  FIX Flow Simulator  [Scenario Name]   [Run] [Stop] [Save] [Import] [Export] │
├──────────────────┬──────────────────────────────────────────┬────────────────┤
│                  │                                          │                │
│   LEFT PANEL     │           CANVAS                         │  RIGHT PANEL   │
│                  │                                          │                │
│  PALETTE         │   Drag blocks here, draw arrows          │  PROPERTIES    │
│  (block types)   │   to build your workflow                 │  (selected     │
│                  │                                          │   block config)│
│  SCENARIOS       │                          [+] [-] [Fit]   │                │
│  (list)          │                                          │  SESSION       │
│                  │                                          │  (connect)     │
├──────────────────┴──────────────────────────────────────────┴────────────────┤
│  [Events] [FIX Messages] [Validation Errors] [Statistics]       [Expand]     │
│  (real-time execution output)                                                │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Area | Purpose |
|---|---|
| Top bar | Run/stop the scenario, save, import/export |
| Left — Palette | Drag blocks onto the canvas |
| Left — Scenarios | Create, rename, delete, search scenarios |
| Canvas | Build your workflow visually |
| Right — Properties | Configure the selected block |
| Right — Session | Connect/disconnect FIX sessions |
| Bottom | Live output: events, messages, errors, stats |

**Language switcher:** The `EN` / `IT` / `FR` buttons in the top-right corner switch the interface language.

---

## 4. Sessions — Connecting to FIX

A **session** is a FIX connection to another system. Before running a scenario, you need at least one connected session.

### Session modes

| Mode | Description |
|---|---|
| **INITIATOR** | Your simulator connects outbound to a counterparty acceptor |
| **ACCEPTOR** | Your simulator listens for inbound connections |

For loopback testing (simulator talks to itself), create one ACCEPTOR and one INITIATOR pointing to `localhost` on the same port.

### Creating a session

1. In the right panel, select `-- new session --` from the **Active session** dropdown
2. Fill in the form fields (see [Section 12](#12-fix-session-configuration-guide) for all fields)
3. Click **Save**
4. Click **Connect**

### Session status

- **Green dot + CONNECTED**: session is up, scenario can run
- **Grey dot + DISCONNECTED**: not connected, click Connect
- **CONNECTING…**: handshake in progress, wait a moment

### Switching sessions

The **Active session** dropdown lets you switch between saved sessions. The currently selected session is used for all FIX blocks in the running scenario.

> **Note:** After restarting the application, sessions must be reconnected manually — click Connect for each session you need.

---

## 5. Scenarios — Building Workflows

A **scenario** is a workflow you build on the canvas. Each scenario belongs to a session.

### Managing scenarios

| Action | How |
|---|---|
| Create | Click **+ New** in the Scenarios section |
| Rename | Double-click the scenario name in the top bar |
| Delete | Click the trash icon next to the scenario in the list |
| Search | Use the search box above the scenario list |
| Save | Click **Save** in the top bar (dot indicator shows unsaved changes) |

### Default blocks

Every new scenario automatically gets:
- **Start** — the entry point (required)
- **End Pass** — marks successful completion
- **End Fail** — marks failed completion

You cannot delete Start, End Pass, or End Fail.

### The canvas

- **Drag** blocks from the palette onto the canvas
- **Click** a block to select it and open its properties
- **Delete** a block: select it and press `Delete` or `Backspace`
- **Zoom**: use `+` / `-` buttons or scroll wheel
- **Fit**: click **Fit** to center all blocks in view
- **Pan**: click and drag on empty canvas space

---

## 6. Block Reference — Every Block Explained

### Messages group

#### Send FIX

Sends an outbound FIX message.

| Field | Description |
|---|---|
| MsgType (tag 35) | The FIX message type. `D` = New Order Single, `G` = Order Cancel/Replace, `F` = Order Cancel, `V` = Market Data Request |
| Fields | Tag/value pairs to include in the message. Supports [variables](#8-variables-and-placeholders). |
| Paste FIX Message | Shortcut to parse a raw FIX string into the fields table |

**Tip:** Paste a captured FIX message from a log file using the **Paste FIX Message** button. The parser strips header/trailer tags automatically.

---

#### Expect FIX

Waits for an inbound FIX message matching a type (and optionally a correlation key).

| Field | Description |
|---|---|
| MsgType (tag 35) | The expected message type. `8` = Execution Report, `9` = Order Cancel Reject |
| Correlation | Optional. Links this block to a specific Send FIX — ensures you receive the reply to your specific order, not someone else's |
| Source Tag | Tag in the incoming message to match (e.g. `11` for ClOrdID) |
| From Node | The Send FIX node whose outbound value must match |
| Target Tag | Tag in the Send FIX message to compare against |

**Stored fields:** All fields from the matched message are stored and available to downstream Validate or Decision blocks via `{{node:id:tagN}}`.

---

#### Validate

Checks that a stored message (captured by an Expect FIX block) meets a set of rules.

| Rule type | Behaviour |
|---|---|
| `EQUALS` | Tag value must exactly match |
| `NOT_EQUALS` | Tag value must not match |
| `ENUM` | Tag value must be one of a comma-separated list |
| `REGEX` | Tag value must match the regular expression |
| `NUMERIC_MIN` | Tag value (as a number) must be ≥ the minimum |
| `NUMERIC_MAX` | Tag value (as a number) must be ≤ the maximum |
| `FIELD_PRESENT` | Tag must exist in the message |
| `FIELD_ABSENT` | Tag must not exist in the message |
| `DATE_RULE` | Timestamp validation (business date, trading session) |

**Strict Mode:** When enabled, any tag in the received message that is not covered by a rule causes a validation failure.

---

### Flow Control group

#### Decision

Branches the flow based on a condition expression.

**Condition syntax:** `LEFT OPERATOR RIGHT`

| Operator | Meaning |
|---|---|
| `==` | Exact string match |
| `!=` | Not equal |
| `contains` | Substring match |

**Example conditions:**

```
{{node:expect-er:tag39}} == "0"
{{node:expect-er:tag150}} != "8"
{{node:expect-er:tag58}} contains "reject"
```

The **bottom handle** of the diamond exits on success (condition is true).  
The **right handle** exits on failure (condition is false).

---

#### Route FIX

Waits for a FIX message and routes to the first matching rule. Rules are evaluated top-to-bottom. A rule with no matchers is a catch-all default.

Each rule has:
- **Label** — a human-readable name (e.g. "Fill", "Reject", "Default")
- **Matchers** — tag/value pairs that must ALL match the incoming message
- **Target Node** — where to go if this rule matches

---

#### Retry

Re-executes a target block up to N times. Stops as soon as one attempt succeeds.

| Field | Description |
|---|---|
| Target Node | The block to retry |
| Max Attempts | Maximum tries before giving up |
| Delay Between Retries (ms) | Milliseconds to wait between attempts |

---

#### Loop

Executes a target block exactly N times. All iterations must succeed.

| Field | Description |
|---|---|
| Target Node | The block to loop |
| Iterations | How many times to execute it |

---

#### Wait

Blocks execution for a configurable duration, then takes one of these actions:

| On Timeout | Behaviour |
|---|---|
| `CONTINUE` | Proceed to the next block (success path) |
| `FAIL` | Mark the block failed |
| `RETRY` | Re-run the block |
| `JUMP` | Jump to a specific block |

---

#### Delay

Pauses execution for a fixed number of milliseconds, then always continues on the success path. Unlike Wait, it has no timeout actions or branching.

---

### Composition group

#### Call Scenario

**Purpose:** Executes another scenario synchronously as a reusable sub-flow. The child scenario runs to completion before the parent continues. The parent's FIX session is inherited.

**Canvas appearance:** Violet border (`#8b5cf6`). Shows the target scenario name in the body.

**Config fields:**

| Field | Description |
|---|---|
| **Target Scenario** | The scenario to execute as a sub-flow. Select from the dropdown. |
| **Input Variables** | Copy variables from the parent into the child. Each row: *From* (parent expression, e.g. `var:orderId`) → *To* (variable name in child). |
| **Output Variables** | Copy variables from the child back into the parent. Each row: *From* (variable name in child) → *To* (variable name in parent). |

**Depth limit:** Calls cannot be nested more than 5 levels deep. Exceeding the limit causes the node to fail.

**Stopped propagation:** If the child is stopped (e.g. execution cancelled), the parent is also stopped immediately.

**Typical use case:** Extract a reusable sub-flow (e.g. a RFQ handshake) into a separate scenario and call it from multiple parent scenarios.

---

### Integration group

#### HTTP Request

Sends an HTTP request to an external endpoint.

| Field | Description |
|---|---|
| Method | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| URL | Full URL with optional query parameters |
| Headers | Key/value pairs (e.g. `Content-Type: application/json`) |
| Body | Request body for POST/PUT/PATCH (JSON, XML, or plain text) |

Supports `{{var:name}}` substitution in URL, headers, and body.

---

### Terminals group

#### Start

The entry point of every scenario. Every scenario must have exactly one Start block.

#### End Pass

Marks the scenario as **passed**. The execution ends here with a successful result.

#### End Fail

Marks the scenario as **failed**. The execution ends here with a failure result.

---

## 7. Connecting Blocks — Drawing Edges

Edges (arrows) connect blocks and define the execution path.

### How to draw an edge

1. Hover over the **source block** until a small circle appears at its bottom edge
2. Click and drag from that circle to the **target block**
3. Release over the target block

### Edge colours

| Colour | Meaning |
|---|---|
| Green | `success` path |
| Red | `failure` path |
| Amber | `timeout` path |
| Grey | `default` or unlabelled |

### Decision node edges

A Decision block has **two** outgoing handles:
- **Bottom handle** → success/true path (green)
- **Right handle** → failure/false path (red)

Drag from the bottom circle for the true path, and from the right circle for the false path.

### Deleting an edge

Click the edge to select it, then press `Delete` or `Backspace`.

---

## 8. Variables and Placeholders

Variables let you build dynamic FIX messages that reference runtime values.

| Placeholder | Description | Example |
|---|---|---|
| `{{now}}` | Current UTC ISO timestamp | `2026-05-20T12:00:00Z` |
| `{{now:offset:+5m}}` | Current UTC time with offset | `{{now:offset:+1h}}` |
| `{{nowdate}}` | Current UTC date as `YYYYMMDD` | `20260520` |
| `{{nowdate:offset:+1d}}` | Date with offset (today + N days) | `{{nowdate:offset:+1d}}` |
| `{{uuid}}` | Random UUID v4 | `550e8400-e29b-41d4...` |
| `{{seq:name}}` | Auto-incrementing counter (resets each run) | `{{seq:orderId}}` → 1, 2, 3 |
| `{{env:VAR}}` | Environment variable | `{{env:SENDER_ID}}` |
| `{{var:name}}` | Named variable (from HTTP_REQUEST response or CALL_SCENARIO output) | `{{var:subHttpStatus}}` |
| `{{node:id:tagN}}` | Tag N from a previous block | `{{node:send-order:tag11}}` |
| `{{node:id:tagN:offset:+5m}}` | Tag value with date offset | `{{node:send-order:tag60:offset:+1h}}` |

### Using variables in Send FIX

In the **Value** column of the Fields table, enter any placeholder:

| Tag | Value |
|---|---|
| 11 | `{{seq:orderId}}` |
| 60 | `{{now}}` |
| 1 | `{{env:ACCOUNT}}` |

### Referencing a previous node's field

To use a value received in an Expect FIX block:
1. Give your Expect FIX block an ID (visible in the block's properties as **Node Name**, e.g. `expect-er`)
2. Reference it as `{{node:expect-er:tag39}}` in any downstream block

**Time offsets** (for timestamp fields):
- `+5s` — add 5 seconds
- `-10m` — subtract 10 minutes
- `+2h` — add 2 hours
- `+1d` — add 1 day

### Variable Reference panel

In any block's properties, scroll down to **Variable Reference** and click it to expand a reference card. Click any placeholder to copy it to your clipboard.

---

## 9. Running a Scenario

### Before running

- At least one session must be **connected** (green dot)
- The scenario must be **saved** (no `•` next to Save button)
- All blocks must be connected with edges

### Starting execution

Click **Run** (or **Esegui**) in the top bar. The button is greyed out if no session is connected.

### During execution

- The currently executing block **pulses green**
- **Passed** blocks turn green
- **Failed** blocks turn red
- Events appear in real time in the bottom panel

### Stopping execution

Click **Stop** (or **Ferma**) to abort a running scenario.

### Execution results

| Status | Meaning |
|---|---|
| `PASSED` | Scenario reached an End Pass block |
| `FAILED` | A block failed or the scenario reached an End Fail block |
| `RUNNING` | Currently executing |
| `ERROR` | An unexpected error interrupted execution |

---

## 10. Reading Results — Events, Messages, Statistics

The bottom panel has four tabs.

### Events tab

Shows a chronological log of all execution events:

| Event | Meaning |
|---|---|
| `EXECUTION_STARTED` | Scenario began |
| `NODE_ENTERED` | Entered a block |
| `NODE_EXITED` | Block completed successfully |
| `MESSAGE_SENT` | Outbound FIX message was sent |
| `MESSAGE_RECEIVED` | Inbound FIX message was matched |
| `ERROR` | A block encountered an error |
| `TIMEOUT` | A Wait/Expect block timed out |
| `EXECUTION_FINISHED` | Scenario completed |

### FIX Messages tab

Shows every FIX message sent and received during execution:

- **IN** (green badge) — message received from counterparty
- **OUT** (blue badge) — message sent by the simulator
- **35=X** — the message type
- Click a row to expand the raw FIX string

### Validation Errors tab

If a Validate block fails, the offending rules appear here with:
- The tag that failed
- The rule that was applied
- The expected value
- The actual value received

### Statistics tab

| Metric | Description |
|---|---|
| Status | Final execution status |
| Nodes passed | Number of blocks that completed successfully |
| Nodes failed | Number of blocks that errored |
| Avg node time | Average time per block in milliseconds |
| Total duration | Total scenario runtime in milliseconds |

**Download Report:** Click the **Download Report** button to export a full JSON report of the execution (events, messages, node results).

---

## 11. Import and Export

### Export a scenario

1. Open the scenario you want to export
2. Click **Export** in the top bar
3. A YAML file is downloaded — this contains the complete scenario definition

### Import a scenario

1. Click **Import** in the top bar
2. Select a previously exported YAML file
3. The scenario is imported and appears in the Scenarios list

### YAML format

The exported file is a plain YAML document. Advanced users can edit it directly and re-import. Example:

```yaml
id: 550e8400-e29b-41d4-a716-446655440000
name: Send New Order
version: '1.0'
sessionRef: uat-initiator
nodes:
  - id: start-1
    name: Start
    type: START
    onSuccess: send-order
  - id: send-order
    name: SendOrder
    type: SEND_FIX
    config:
      msgType: D
      fields:
        11: ORD-{{seq:orderId}}
        55: AAPL
        54: '1'
        38: '100'
        40: '2'
    onSuccess: end-pass
  - id: end-pass
    name: End Pass
    type: END_PASS
edges:
  - from: start-1
    to: send-order
    label: success
  - from: send-order
    to: end-pass
    label: success
```

---

## 12. FIX Session Configuration Guide

### All session fields

| Field | Description | Example |
|---|---|---|
| Name | A friendly label for this session | `uat-initiator` |
| Mode | `INITIATOR` connects out; `ACCEPTOR` listens in | `INITIATOR` |
| FIX Version | Protocol version | `FIX 4.4` |
| DefaultApplVerID | Required only for FIXT.1.1 (FIX 5.0 SP2) | `FIX.5.0SP2` |
| SenderCompID | Your CompID (FIX tag 49) | `CLIENT` |
| TargetCompID | Counterparty CompID (FIX tag 56) | `SERVER` |
| Host | Acceptor IP/hostname (INITIATOR only) | `127.0.0.1` |
| Port | TCP port | `9001` |
| Heartbeat Interval (sec) | Seconds between heartbeat messages | `30` |
| Reset on Logon | Reset sequence numbers on each logon | `No` |
| Reset on Logout | Reset sequence numbers on logout | `No` |

### FIX versions

| Version string | Notes |
|---|---|
| `FIX 4.0` | Legacy |
| `FIX 4.1` | Legacy |
| `FIX 4.2` | Common in older systems |
| `FIX 4.4` | Most common — recommended default |
| `FIX 5.0` | Session/application layer separated |
| `FIX 5.0 SP2` | Latest; requires `DefaultApplVerID` |
| `FIXT.1.1` | Transport layer for FIX 5.x |

### Loopback testing (simulator to simulator)

To test without an external counterparty:

1. Create an **ACCEPTOR** session:
   - SenderCompID: `SERVER`
   - TargetCompID: `CLIENT`
   - Port: `9001`

2. Create an **INITIATOR** session:
   - SenderCompID: `CLIENT`
   - TargetCompID: `SERVER`
   - Host: `127.0.0.1`
   - Port: `9001`

3. Connect the ACCEPTOR first, then the INITIATOR.

---

## 13. FIX Terminology Glossary

| Term | Definition |
|---|---|
| **FIX** | Financial Information eXchange — a message protocol for financial trading |
| **Session** | A persistent TCP connection between two FIX endpoints, identified by SenderCompID + TargetCompID |
| **INITIATOR** | The side that opens the TCP connection (the client) |
| **ACCEPTOR** | The side that accepts incoming connections (the server) |
| **CompID** | Company identifier in a FIX session. SenderCompID = your side, TargetCompID = their side |
| **Tag** | A numeric FIX field identifier. Tag 35 = MsgType, Tag 49 = SenderCompID, Tag 56 = TargetCompID |
| **MsgType (tag 35)** | Identifies the type of FIX message. `D` = New Order Single, `8` = Execution Report |
| **Logon (tag 35=A)** | The first message exchanged to establish a FIX session |
| **Heartbeat (tag 35=0)** | Sent periodically to keep the session alive. Never appears in your scenario log |
| **New Order Single (D)** | The most common order type — sends a new order to an exchange/broker |
| **Execution Report (8)** | Response from an exchange/broker confirming or rejecting an order |
| **ClOrdID (tag 11)** | Client-assigned order ID. Used to correlate an order with its replies |
| **OrdStatus (tag 39)** | Order status in an Execution Report. `0` = New, `1` = Partial Fill, `2` = Fill, `8` = Rejected |
| **ExecType (tag 150)** | Execution type. `0` = New, `1` = Partial Fill, `2` = Fill, `8` = Rejected |
| **Symbol (tag 55)** | Instrument symbol, e.g. `AAPL` |
| **Side (tag 54)** | `1` = Buy, `2` = Sell |
| **OrdQty (tag 38)** | Order quantity |
| **OrdType (tag 40)** | `1` = Market, `2` = Limit, `3` = Stop |
| **SOH** | ASCII character 0x01 — the FIX field delimiter. In text representations often shown as `\|` |
| **Sequence number** | An incrementing counter attached to every FIX message to detect gaps |
| **Correlation** | Matching a reply to the original request using a shared field (e.g. ClOrdID) |

---

## 14. Troubleshooting

### Run button is greyed out

**Cause:** No session is connected.  
**Fix:** Select a session in the right panel and click Connect. Wait for the green CONNECTED status.

### "No scenario loaded"

**Cause:** No scenario is selected.  
**Fix:** Click a scenario in the Scenarios list on the left.

### Expect FIX block times out

**Cause:** No matching message was received within the timeout period.  
**Fixes:**
- Check that the session is connected
- Verify the MsgType matches the message your counterparty sends
- Check correlation settings — if the From Node or tag numbers are wrong, the message will not match
- Increase the timeout value on the Expect FIX block

### Validation fails unexpectedly

**Cause:** A field value does not match the rule.  
**Fix:** Switch to the **Validation Errors** tab to see the exact tag, expected value, and actual value. Adjust the rule or the Send FIX message accordingly.

### Session won't connect

**Causes and fixes:**
- **INITIATOR**: verify Host and Port match the acceptor's settings; ensure the acceptor is running
- **ACCEPTOR**: the status shows DISCONNECTED while waiting for an initiator — this is normal
- **CompID mismatch**: SenderCompID on one side must match TargetCompID on the other
- **After app restart**: reconnect each session manually (connections are not persisted across restarts)

### Blocks are invisible after reload (visibility: hidden)

**Cause:** The scenario YAML was imported without positions.  
**Fix:** Click the **Fit** button to auto-layout visible blocks; or drag them manually.

### Sequence number errors

After multiple test runs, sequence numbers may desync.  
**Fix:** Enable **Reset on Logon** in the session configuration, or disconnect and reconnect the session.

### Changes are lost after refresh

**Cause:** Scenario was not saved.  
**Fix:** Always click **Save** before refreshing. The `•` indicator next to Save means there are unsaved changes.

---

*This guide covers FIX Flow Simulator v0.4.0-beta. For developer/API documentation, see [developer-guide.md](developer-guide.md).*
