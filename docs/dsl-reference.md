# Scenario DSL Reference

Scenarios are YAML documents stored under the `yamlDsl` field of a scenario.

## Top-level shape

```yaml
id: rfq-flow
name: RFQ Flow
description: Quote request/response
version: '1.0'
sessionRef: default
nodes: []
edges: []
```

## Node types

| type | purpose |
|---|---|
| `START` | Entry point. No incoming edges. |
| `SEND_FIX` | Send a FIX message via the session. |
| `EXPECT_FIX` | Wait for a matching inbound message. |
| `VALIDATE` | Apply validation rules to a received message. |
| `DECISION` | Evaluate a condition expression; branch on true/false. |
| `ROUTE_FIX` | Wait for inbound FIX; route to first matching rule. |
| `CALL_SCENARIO` | Synchronously execute another scenario as a reusable sub-flow. |
| `BRANCH` | Alias for `DECISION`. |
| `RETRY` / `LOOP` | Retry a sub-graph N times with delay. |
| `WAIT` / `DELAY` / `TIMEOUT` | Pause for a duration. |
| `END_PASS` / `END_FAIL` | Terminal nodes. |

## Common node fields

```yaml
- id: send-nos
  name: Send New Order Single
  type: SEND_FIX
  config: { ... }                # node-specific
  timeout:
    value: 30
    unit: SECONDS                # MILLISECONDS | SECONDS | MINUTES | HOURS
    onTimeout: FAIL              # FAIL | RETRY | CONTINUE | JUMP
    jumpTo: some-node-id         # required when onTimeout == JUMP
  retryPolicy:
    maxAttempts: 3
    delayMs: 1000
  onSuccess: next-node-id
  onFailure: error-node-id
  onTimeout: timeout-node-id
```

## SEND_FIX config

```yaml
config:
  msgType: D
  fields:
    - { tag: 11, value: "{{uuid}}" }
    - { tag: 55, value: AAPL }
    - { tag: 38, value: "100" }
    - { tag: 40, value: "2" }
    - { tag: 44, value: "{{node:prev:tag31}}" }
```

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

For **group entry** `fields`, use the **list form** shown above — it is the
only safe way to author them. The map form (`{tag: value}`) is accepted on
read, but it is **not round-trip safe**: JavaScript object keys that look like
integers are iterated in ascending numeric order, so if you hand-author a map
with the delimiter tag anywhere but the lowest tag number (e.g.
`{600: EUR/USD, 587: '0'}`), opening the scenario in the GUI and saving it
re-serialises the entry with the fields reordered ascending (`587` before
`600`), silently moving a lower-numbered tag ahead of the delimiter and
producing a malformed message on the wire. `SendFIXHandler` itself accepts
both forms for entry fields — the hazard is specific to the GUI's
save round trip, not the engine.

Top-level `fields` are unaffected by this: they have no delimiter-ordering
requirement, so the map form is fully safe there and is what the UI
serialiser emits.

## EXPECT_FIX config

```yaml
config:
  msgType: 8
  correlation:
    sourceTag: 11      # tag in the inbound message
    fromNode: send-nos # node id whose outbound value should match
    targetTag: 11      # tag in the outbound message
```

Both parts are optional and both are conditions: an inbound message is accepted only when it
satisfies **every** one that is present.

| config | accepts |
|---|---|
| `msgType` only | the first message with that tag 35 |
| `correlation` only | the first message whose `sourceTag` equals the referenced node's `targetTag` |
| both | a message that matches the MsgType **and** the correlated value |
| neither | the first application message on the session |

A `correlation` block that names no `fromNode` and no `expectedValue` carries no condition and is
ignored — including the empty `correlation: {}` that older editor exports contain. A block that
does name a `fromNode` whose message has no such tag at run time fails the node immediately, with
the reason, rather than waiting for the timeout.

## VALIDATE config

```yaml
config:
  sourceNodeId: expect-ack   # optional; defaults to the last message received in the run
  strictMode: true
  rules:
    - { tag: 35, rule: EQUALS, value: "8" }
    - { tag: 39, rule: ENUM, values: ["0", "1", "2"] }
    - { tag: 11, rule: REGEX, pattern: "^ORD-[0-9]+$" }
    - { tag: 38, rule: NUMERIC_MIN, numericValue: 1 }
    - { tag: 60, rule: DATE_RULE, dateRuleId: dr-recent }
  dateRules:
    - ruleId: dr-recent
      type: CURRENT_TIMESTAMP
      toleranceValue: 5
      toleranceUnit: SECONDS
    - ruleId: dr-expiry
      type: FIELD_OFFSET
      sourceNode: send-nos
      sourceTag: 60
      offsetValue: 5
      offsetUnit: MINUTES
      toleranceValue: 1
      toleranceUnit: SECONDS
```

### Rule kinds

| rule | extra fields |
|---|---|
| `EQUALS` / `NOT_EQUALS` | `value` |
| `ENUM` | `values` (list) |
| `REGEX` | `pattern` |
| `NUMERIC_MIN` / `NUMERIC_MAX` | `numericValue` |
| `CONTAINS` / `NOT_CONTAINS` | `value` (or `ref`) |
| `LENGTH` / `LENGTH_MIN` / `LENGTH_MAX` | `numericValue` |
| `FIELD_PRESENT` / `FIELD_ABSENT` | none |
| `DATE_RULE` | `dateRuleId` (`dateRule` also accepted) |

`CONTAINS` and `NOT_CONTAINS` test for a substring, so they say in one line what a `REGEX` of
`.*XXX.*` says awkwardly and a value containing `.` or `|` says wrongly. `LENGTH`, `LENGTH_MIN`
and `LENGTH_MAX` compare the field's character count.

A **missing field fails all five**, exactly like `NUMERIC_MIN` / `NUMERIC_MAX`: absence is
asserted with `FIELD_ABSENT`, never as a side effect of a content rule — otherwise a typo in a tag
number would make `NOT_CONTAINS` pass. Like `EQUALS`, the two substring rules accept a `ref`
(`{{node:send-nos:tag11}}`) in place of a literal `value`, and all five work inside a repeating
group through `groupTag` / `index`.

```yaml
rules:
  - { tag: 55,  rule: CONTAINS, value: "/" }            # EUR/USD is a pair
  - { tag: 461, rule: NOT_CONTAINS, value: "XXX" }      # no placeholder CFI code
  - { tag: 11,  rule: LENGTH_MAX, numericValue: 20 }    # ClOrdID fits the venue's limit
  - { tag: 1,   rule: LENGTH, numericValue: 7 }         # account codes are exactly 7 chars
```

### Validating repeating groups

A rule can target a field inside a group entry instead of a top-level field:

```yaml
rules:
  - { tag: 609, groupTag: 555, index: 0,   rule: EQUALS, value: FXSPOT }
  - { tag: 600, groupTag: 555, index: '*', rule: FIELD_PRESENT }
```

`groupTag` absent means a top-level field, evaluated against the message's flat
fields as before. When `groupTag` is set, `tag` is looked up inside that group's
entries instead. `index` defaults to `0`; `*` applies the rule to every entry in
the group (one result per entry). An out-of-range numeric `index`, or a
`groupTag` with no entries present in the message, fails the rule.

## DECISION config

Two forms. A single condition routes success/failure:

```yaml
config:
  condition: '{{node:expect-er:tag39}} == "2"'
  # Operators: == != contains
  # Left and right sides support {{...}} placeholders.
  # True  → onSuccess path
  # False → onFailure path
```

Or several branches, each with its own conditions and its own target — the same contract
`ROUTE_FIX` rules have, over conditions instead of tag matchers:

```yaml
config:
  branches:
    - branchId: b1
      label: Filled
      conditions:                                   # ALL must hold
        - '{{node:expect-er:tag39}} == "2"'
        - '{{node:expect-er:tag151}} == "0"'
      targetNodeId: send-confirm
    - branchId: b2
      label: Partially filled
      conditions: ['{{node:expect-er:tag39}} == "1"']
      targetNodeId: wait-more
    - branchId: b3
      label: Anything else
      conditions: []                                # no conditions → catch-all default
      targetNodeId: end-fail
```

- Branches are evaluated **in order**; the first whose conditions **all** hold is taken.
- A branch with no conditions (or only blank ones) is the **default**. A later branch after the
  first default is still evaluated first — the default is only used when nothing matched.
- With no match and no default, the node fails down `onFailure`, which is what a false `condition`
  has always done.
- A branch with no `targetNodeId` falls back to the node's `onSuccess`.
- `branches` wins when both forms are present; an empty `branches` list falls back to `condition`.

In the editor each branch gets its own handle on the diamond, and the matched branch label is
shown on the node's event in the execution log.

## ROUTE_FIX config

```yaml
config:
  rules:
    - ruleId: r1
      label: Quote
      matchers:
        35: S
        131: "{{node:send-rfq:tag131}}"
      targetNodeId: process-quote

    - ruleId: r2
      label: Reject
      matchers:
        35: AG
      targetNodeId: handle-reject

    - ruleId: r3
      label: Default
      matchers: {}
      targetNodeId: unexpected-msg
```

Rules evaluated top-to-bottom; first match wins. Matcher values support `{{node:id:tagN}}` placeholders resolved at execution time. After routing, matched rule label appears in the `NODE_EXITED` event detail.

## CALL_SCENARIO config

Executes another scenario synchronously as a sub-flow. The child inherits the parent's FIX session. Output variables are copied back to the parent after the child completes.

```yaml
- id: call-rfq
  name: Call RFQ Sub-Flow
  type: CALL_SCENARIO
  config:
    targetScenarioId: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"   # UUID of the target scenario
    inputVars:                                                   # copy parent vars → child
      - from: "var:orderId"      # parent expression (supports {{...}} syntax)
        to: "childOrderId"       # variable name in child context
    outputVars:                                                  # copy child vars → parent
      - from: "rfqResult"        # variable name in child context
        to: "parentRfqResult"    # variable name in parent context
  onSuccess: next-node
  onFailure: error-node
```

**Depth limit:** Maximum call depth is 5. Reaching the limit returns failure.

**STOPPED propagation:** If the child is stopped (e.g. the execution is cancelled), the parent execution is also stopped immediately.

## Variable syntax

| placeholder | meaning |
|---|---|
| `{{now}}` | current UTC ISO timestamp |
| `{{now:offset:+1h}}` | current UTC time with offset applied |
| `{{nowdate}}` | current UTC date as `YYYYMMDD` |
| `{{nowdate:offset:+1d}}` | current UTC date with offset, e.g. `+1d` for tomorrow |
| `{{uuid}}` | random UUID |
| `{{seq:name}}` | monotonic sequence keyed by `name` |
| `{{env:VAR}}` | environment variable |
| `{{var:name}}` | named variable set earlier via HTTP_REQUEST response or CALL_SCENARIO output |
| `{{node:id:tagN}}` | value of tag N from a previous node |
| `{{node:id:tagN:offset:+5m}}` | value with date offset applied |
| `{{node:id:gNNN.i:tagM}}` | tag M of entry `i` (0-based) of group NNN on node `id` |
| `{{node:id:gNNN.i:tagM:offset:+2d}}` | same, with a date offset applied |

Offset format: `[+-](\d+)[smhd]` (seconds, minutes, hours, days).

## Edges

```yaml
edges:
  - { from: send-nos, to: expect-er, label: success }
  - { from: send-nos, to: end-fail, label: failure }
  - { from: send-nos, to: retry,     label: timeout }
```

## Worked example — RFQ flow

```yaml
id: rfq-flow
name: RFQ Flow
description: Quote request/response
version: '1.0'
sessionRef: default
nodes:
  - id: start
    name: Start
    type: START
    config: {}
    onSuccess: send-qr
  - id: send-qr
    name: Send QuoteRequest
    type: SEND_FIX
    config:
      msgType: R
      fields:
        - { tag: 131, value: "{{uuid}}" }
        - { tag: 55,  value: AAPL }
        - { tag: 38,  value: "100" }
    timeout: { value: 5, unit: SECONDS, onTimeout: FAIL }
    onSuccess: expect-quote
  - id: expect-quote
    name: Expect Quote
    type: EXPECT_FIX
    config:
      msgType: S
      correlation:
        sourceTag: 131
        fromNode: send-qr
        targetTag: 131
    timeout: { value: 10, unit: SECONDS, onTimeout: FAIL }
    onSuccess: validate
  - id: validate
    name: Validate Quote
    type: VALIDATE
    config:
      strictMode: false
      rules:
        - { tag: 132, rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 60,  rule: DATE_RULE,   dateRuleId: dr-fresh }
      dateRules:
        - ruleId: dr-fresh
          type: CURRENT_TIMESTAMP
          toleranceValue: 5
          toleranceUnit: SECONDS
    onSuccess: end-pass
    onFailure: end-fail
  - id: end-pass
    name: End OK
    type: END_PASS
    config: {}
  - id: end-fail
    name: End Failed
    type: END_FAIL
    config: {}
edges:
  - { from: start,        to: send-qr,      label: success }
  - { from: send-qr,      to: expect-quote, label: success }
  - { from: expect-quote, to: validate,     label: success }
  - { from: validate,     to: end-pass,     label: success }
  - { from: validate,     to: end-fail,     label: failure }
```
