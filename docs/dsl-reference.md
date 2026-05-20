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

## EXPECT_FIX config

```yaml
config:
  msgType: 8
  correlation:
    sourceTag: 11      # tag in the inbound message
    fromNode: send-nos # node id whose outbound value should match
    targetTag: 11      # tag in the outbound message
```

## VALIDATE config

```yaml
config:
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
| `FIELD_PRESENT` / `FIELD_ABSENT` | none |
| `DATE_RULE` | `dateRuleId` |

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
