# FX Order Lifecycle Templates + FIX Repeating Group Support

Date: 2026-08-24
Status: approved (design)

## 1. Goal

Ship five importable scenario templates that let the simulator act as a **venue
(sell-side)** against FIX orders sent by the user's own application, covering the
full order lifecycle for five FX product families:

| Template | Product |
|---|---|
| `fx-spot-lifecycle` | FX Spot |
| `fx-forward-deliverable-lifecycle` | Deliverable FX Forward |
| `fx-ndf-lifecycle` | Non-Deliverable Forward (FX) |
| `fx-swap-lifecycle` | FX Swap (near/far legs) |
| `fx-option-vanilla-lifecycle` | FX Vanilla Option |

Every template covers: order creation, ACK, execution report, amend, cancel and
expiry. NDF adds the fixing event. The option adds exercise / abandon. Every
outbound ExecutionReport carries the full instrument reference block, including
`SecurityType` (167) and `CFICode` (461).

Wire protocol: **FIX 5.0 SP2** (FIXT.1.1 session, `DefaultApplVerID=9`).

The FX Swap template requires FIX repeating groups (`NoLegs`), which the engine
does not support today. Extending the engine for repeating groups is therefore
in scope, and the UI must reach functional parity: repeating groups have to be
fully editable from the graphical node editor, not merely round-tripped through
YAML.

## 2. Fixed decisions

Settled with the user before design:

1. **Role** - simulator is the venue. Scenarios start by *waiting* for an inbound
   order and reply with ExecutionReports. They never originate the order.
2. **Structure** - one self-contained master scenario per product. No
   `CALL_SCENARIO` cross-file dependencies: each YAML imports and runs alone.
3. **SecurityType / CFI** - standard FIX and ISO 10962 values, documented in a
   header table inside each template.
4. **FX Swap** - real `NoLegs` repeating group; extend the engine rather than
   approximate with flattened FIX 4.2-style tags or two linked single orders.
5. **Option events** - exercise / abandon only. Expiry, settlement and premium
   settlement are out of scope for this iteration.
6. **UI** - full functional parity for repeating groups in the graphical editor.

## 3. Approach

### Chosen: recursive `FIXMessageData`

Introduce a value type that models a FIX message as top-level fields plus named
repeating groups, recursively:

```java
public record FIXMessageData(
    Map<Integer, String> fields,
    Map<Integer, List<FIXMessageData>> groups   // counterTag -> entries, in order
) {}
```

The existing flat `Map<Integer, String>` stays available as a *projection* of the
message (top-level fields only), so correlation, `VALIDATE`, `DECISION` and
`ROUTE_FIX` keep working unchanged on every scenario already saved in the DB.

Ports gain an overload; the current single-argument methods remain as `default`
methods delegating to the new one. Nothing that exists today breaks.

### Rejected alternatives

**Path-keyed flat map** (`Map<String,String>` with `"555/0/600"` keys) - one type
instead of two, but it changes every signature in core, engine and adapters,
invalidates every persisted scenario, and forces a rewrite of the validation and
correlation engines. Cost far exceeds the benefit.

**Outbound-only groups** - roughly half the work, but the venue could not read
the legs of an inbound `NewOrderMultileg`, so the swap template could neither
validate nor echo them. Does not satisfy the requirement.

## 4. Component 1 - core model

New file `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/FIXMessageData.java`.

- Canonical constructor defensively copies and rejects nulls (empty maps instead).
- `static FIXMessageData ofFields(Map<Integer,String>)` for the common no-group case.
- `Map<Integer,String> flatFields()` returns the top-level projection.
- Group entry order is significant and preserved (`LinkedHashMap` / `List`).

Port changes:

```java
// FIXSessionPort
void sendMessage(UUID sessionId, FIXMessageData message);
default void sendMessage(UUID sessionId, Map<Integer,String> fields) {
    sendMessage(sessionId, FIXMessageData.ofFields(fields));
}

// InboundMessageListener  (no longer @FunctionalInterface)
void onMessage(String sessionId, FIXMessageData message);
default void onMessage(String sessionId, Map<Integer,String> fields) {
    onMessage(sessionId, FIXMessageData.ofFields(fields));
}
```

`ExecutionContext` stores the full `FIXMessageData` per node
(`storeNodeMessage(String, FIXMessageData)`), with `getNodeMessage(String)`
retained as the flat projection so existing plugins are untouched, plus a new
`getNodeMessageData(String)`.

## 5. Component 2 - DSL

### `SEND_FIX` gains `groups`

```yaml
config:
  msgType: AB
  fields:
    - { tag: 11, value: "{{uuid}}" }
  groups:
    - counterTag: 555              # NoLegs
      entries:
        - fields:
            - { tag: 600, value: EUR/USD }     # LegSymbol
            - { tag: 624, value: "1" }         # LegSide - Buy near leg
            - { tag: 587, value: "0" }         # LegSettlType - Regular (spot)
            - { tag: 588, value: "{{nowdate:offset:+2d}}" }
        - fields:
            - { tag: 600, value: EUR/USD }
            - { tag: 624, value: "2" }         # LegSide - Sell far leg
            - { tag: 587, value: "6" }         # LegSettlType - Future
            - { tag: 588, value: "{{nowdate:offset:+92d}}" }
          groups: []                            # nesting allowed, same shape
```

The counter tag value is **not** written by hand: `SendFIXHandler` emits it from
`entries.size()`. This removes the "NoLegs=2 but one entry" class of malformed
message by construction.

`ScenarioDslParser` needs no change - node `config` is already `Map<String,Object>`,
so `groups` deserializes as nested lists/maps and passes through.

### New placeholder for group values

`{{node:<nodeId>:g<counterTag>.<index>:tag<N>}}`, index 0-based. Example:

```
{{node:route-order:g555.0:tag600}}   # LegSymbol of the first leg of the inbound order
```

Implemented as a new `GroupFieldPlugin` in `VariableResolver`, registered before
`NodeFieldPlugin`. A companion `g...:offset:` variant mirrors `DateOffsetPlugin`.
Missing group or out-of-range index throws with a message naming the node,
counter tag and index, consistent with the existing plugins' behaviour.

### `VALIDATE` on group fields

Rules gain two optional keys:

```yaml
rules:
  - { tag: 609, groupTag: 555, index: 0, rule: EQUALS, value: FXSPOT }
  - { tag: 609, groupTag: 555, index: 1, rule: EQUALS, value: FXFWD }
```

Absent `groupTag` means top-level, i.e. today's behaviour. `index` defaults to 0.
An `index` of `*` applies the rule to every entry of the group.

### Unchanged

`EXPECT_FIX` correlation and `ROUTE_FIX` matchers keep operating on the flat
top-level projection. FX order routing keys (`35`, `11`, `41`) are always
top-level, so no template needs group-aware routing.

## 6. Component 3 - QuickFIX/J adapter

**Outbound** (`QuickFIXAdapter.sendMessage`): after setting top-level fields,
walk `groups` and, for each counter tag, build one `quickfix.Group` per entry
using the counter tag and the entry's first tag as the delimiter, recursing into
nested groups. `msg.addGroup(...)` per entry; QuickFIX/J then writes the counter
tag and the correct field order itself. Session tags (8, 9, 10, 34, 49, 52, 56)
stay filtered at the `SendFIXHandler` level as they are today.

**Inbound** (`QuickFIXApplicationAdapter.extractFields`): today it iterates only
top-level fields, so group content is silently dropped. It becomes
`extractMessage`, returning `FIXMessageData`: top-level fields as now, then for
each known counter tag present in the message, `message.getGroups(counterTag)`
is walked and each `quickfix.Group` converted recursively.

Counter tags are discovered from the message itself - any tag whose value parses
as an integer and for which `getGroups(tag)` returns a non-empty list is treated
as a group counter. This avoids maintaining a hard-coded table on the Java side.

`MessageRouter`, `MessageBuffer` and `CorrelationEngine` carry `FIXMessageData`
instead of `Map<Integer,String>`; their matching logic keeps reading the flat
projection, so the change is a type substitution, not a logic change.

Persistence is unaffected: `FIXMessageEntity` stores the raw FIX string, which
already contains the groups.

## 7. Component 4 - UI functional parity

This is a first-class requirement, not a follow-up.

### `SendFIXConfig.tsx`

Extract the existing tag/name/value table into a reusable `FieldTable.tsx`
(tag input with `fix-tag-list` datalist, resolved field name, value input,
`ENGINE_TAGS` yellow-border warning, remove button). Use it in three places:
top-level fields, group entry fields, and nested group entry fields.

Add a **Repeating groups** section below Fields:

- `+ Add group` opens a counter-tag input backed by a datalist of known counter
  tags (555 NoLegs, 453 NoPartyIDs, 864 NoEvents, 711 NoUnderlyings,
  702 NoPositions, 78 NoAllocs, 232 NoStipulations, 1445 NoRateSources).
- Each group renders as a collapsible block headed `555 - NoLegs (2 entries)`.
- Each entry is a numbered card (`#1`, `#2`) containing a `FieldTable`, with
  per-entry actions: add field, duplicate entry, delete entry, move up, move down.
- The counter tag value is displayed read-only and derived from entry count.
- An entry may itself hold sub-groups; the group block component recurses with
  indentation. Depth is capped at 3 with a message beyond that.

### `parseFIXMessage.ts`

Pasting a raw FIX message containing groups currently produces duplicated flat
fields, silently losing structure. Add a `GROUP_DELIMITERS` table mapping counter
tag to delimiter tag (555 -> 600, 453 -> 448, 864 -> 865, 711 -> 311,
702 -> 703, 78 -> 79). On encountering a counter tag, consume following segments
into entries, starting a new entry at each delimiter tag, until a tag outside the
group's observed tag set appears. Unknown counter tags fall back to today's flat
behaviour and surface a visible warning in the paste panel.

### `scenarioSerializer.ts`

`serializeConfig` / `parseConfig` handle `groups` recursively. Today
`serializeConfig` only rewrites `fields`, and spreads the rest of `config`, so
groups would survive by accident but their inner `fields` arrays would not be
normalised. Make the handling explicit and recursive in both directions.

### `ValidateConfig.tsx`

Two extra optional inputs per rule: group counter tag and entry index
(`*` allowed), rendered inline and omitted from the emitted config when blank.

### `fixTags.ts`

Extend the dictionary (currently 90 entries) with every tag the templates use:

- Legs: 555, 566, 587, 588, 600, 608, 609, 623, 624, 637, 654, 675, 687, 1418
- Options: 200, 201, 202, 231, 541, 947, 1193, 1194, 1482
- Position maintenance: 702, 703, 704, 705, 709, 710, 712, 721, 722, 723
- Trade capture: 487, 571, 828, 856, 1003, 1123
- FX settlement: 63, 64, 119, 120, 155, 156, 193
- Events: 864, 865, 866
- Instrument: 22, 48, 107, 167, 207, 460, 461, 762
- Order lifecycle: 41, 102, 103, 126, 150, 151, 372, 378, 432, 434, 442

### i18n

New keys added to `en.json`, `it.json` and `fr.json`.

## 8. Component 5 - the templates

Location: `templates/fx/*.yaml` plus a `README.md`. Import via the UI Import
button or `POST /api/v1/scenarios/import` (multipart, field `file`).

Each template carries a fixed UUID in `id` so re-importing updates rather than
duplicates, and a comment header with the instrument reference table.

### Shared graph

```
start -> dispatch (ROUTE_FIX, idle timeout 120s -> end-pass)

dispatch rules, first match wins:
  35=D  -> validate-new
  35=G  -> validate-amend
  35=F  -> validate-cancel
  35=AL -> validate-exercise      (option template only)
  {}    -> reject-unsupported (SEND_FIX 35=j BusinessMessageReject) -> dispatch

validate-new  ok -> ack-new    ko -> reject-new (ER 150=8, 39=8, 103 OrdRejReason) -> dispatch
ack-new  (ER 150=0 New, 39=0)  -> decide-ordtype
decide-ordtype  40 == "1" (Market) -> fill
                otherwise (Limit)  -> wait-limit
wait-limit  timeout JUMP -> expire (ER 150=C Expired, 39=C) -> dispatch
fill  (ER 150=F Trade, 39=2 Filled) -> dispatch

validate-amend  ok -> ack-amend  (ER 150=5 Replaced, 39=0)    -> dispatch
                ko -> reject-amend (35=9, 434=2, 102, 39)      -> dispatch
validate-cancel ok -> ack-cancel (ER 150=4 Canceled, 39=4)     -> dispatch
                ko -> reject-cancel (35=9, 434=1, 102, 39)     -> dispatch
```

Every branch returns to `dispatch`, so one execution services the whole lifecycle
of an order rather than one message.

### Instrument reference block

Present on every outbound ExecutionReport:

`55` Symbol, `48` SecurityID, `22` SecurityIDSource, `167` SecurityType,
`461` CFICode, `460` Product, `762` SecuritySubType, `107` SecurityDesc,
`15` Currency, `120` SettlCurrency, `63` SettlType, `64` SettlDate,
`541` MaturityDate, `207` SecurityExchange.

### Per-product deltas

**fx-spot** - `167=FXSPOT`, `461=IFXXXP`, `63=0` Regular, `64` = T+2. No `541`.
Market and limit branches; expiry driven by `59` TimeInForce (`0` Day / `6` GTD
with `126` ExpireTime).

**fx-forward-deliverable** - `167=FXFWD`, `461=JFTXFP`, `63=6` Future,
`64`/`541` at the forward date. Fill carries `155` SettlCurrFxRate and
`156` SettlCurrFxRateCalc.

**fx-ndf** - `167=FXNDF`, `461=JFTXFN`, `120=USD` (non-deliverable settlement
currency). Extra branch after `fill`:

```
fill -> wait-fixing (WAIT, short in the template; real life = fixing date)
     -> send-fixing (SEND_FIX 35=AE TradeCaptureReport)
     -> expect-fixing-ack (EXPECT_FIX 35=AR TradeCaptureReportAck) -> dispatch
```

The fixing TradeCaptureReport carries `571` TradeReportID, `487` TransType=0,
`856` TradeReportType=0, `828` TrdType, `31` fixing rate, `32` LastQty,
`75` TradeDate, `64` SettlDate, `119` SettlCurrAmt, `120` SettlCurrency,
`155` SettlCurrFxRate, `156` SettlCurrFxRateCalc, and a `NoEvents` (864) group
with `865` EventType and `866` EventDate carrying the fixing date.

**fx-swap** - dispatcher matches `35=AB` NewOrderMultileg instead of `35=D`.
Inbound validation checks `NoLegs` entry 0 (`609=FXSPOT`) and entry 1
(`609=FXFWD`) using the new group-aware VALIDATE rules. Outbound ExecutionReports
carry `442=3` MultiLegReportingType and a `NoLegs` group echoing both legs with
`637` LegLastPx and `1418` LegLastQty. `167=FXSWAP`, `461=SFAXXP`.
This is the template that justifies the engine extension.

**fx-option-vanilla** - `167=OPT`, `460=4` Currency, `461=HFRAVP` (European call,
physical) with `HFRDVP` documented for puts. Instrument adds `201` PutOrCall,
`202` StrikePrice, `947` StrikeCurrency, `1194=0` ExerciseStyle European,
`1193` SettlMethod, `1482=1` OptPayoutType Vanilla, `541` MaturityDate,
`200` MaturityMonthYear, `231` ContractMultiplier; `44` Price is the premium.
Extra dispatcher branch:

```
35=AL -> validate-exercise -> decide-exercise
   709 == "1" -> exercise-report (35=AM, 722=0 Accepted, 723 PosMaintResult) -> dispatch
   709 == "2" -> abandon-report  (35=AM, 722=0 Accepted, abandon)            -> dispatch
   invalid    -> exercise-reject (35=AM, 722=2 Rejected, 723)                -> dispatch
```

`PositionMaintenanceRequest` in carries `710` PosReqID, `709` PosTransType,
`712` PosMaintAction, `1` Account, `581` AccountType, `715` ClearingBusinessDate
and a `NoPositions` (702) group; the report echoes with `721` PosMaintRptID.

### Dates and timings

Templates use `{{nowdate:offset:+2d}}`-style placeholders so they stay valid
whenever they are run, and short timeouts (seconds, not days) so a full lifecycle
is exercisable in one sitting. Both are single-point edits documented in the
README.

## 9. Reference values

Sources: ISO 10962:2021 attribute tables per group, FIX 5.0 SP2 dictionary.

| Product | SecurityType (167) | CFICode (461) | CFI breakdown |
|---|---|---|---|
| FX Spot | `FXSPOT` | `IFXXXP` | I Spot / F FX / attr4 P Physical |
| FX Forward, deliverable | `FXFWD` | `JFTXFP` | J Forwards / F FX / T Spot underlying / X / F Forward price / P Physical |
| NDF | `FXNDF` | `JFTXFN` | as above, attr4 N Non-Deliverable |
| FX Swap | `FXSWAP` | `SFAXXP` | S Swaps / F FX / A Spot-Forward swap / X / X / P Physical |
| FX Vanilla Option, European call, physical | `OPT` + `460=4` | `HFRAVP` | H Non-listed options / F FX / R Forward underlying / A European Call / V Vanilla / P Physical |
| FX Vanilla Option, European put, physical | `OPT` + `460=4` | `HFRDVP` | attr2 D European Put |

Two judgement calls, flagged in the template headers:

- FIX 5.0 SP2 defines no `SecurityType` for FX options; only `FXSPOT`, `FXFWD`,
  `FXNDF`, `FXSWAP` exist. `OPT` plus `Product=4` (Currency) is used, with the
  CFI carrying the precision.
- CFI group `HF` attribute 1 has no "spot" value. `R` (Forwards) is used, since a
  vanilla FX option prices off the outright forward. `M` (Others) is the
  alternative if the user's reference data says so.

## 10. Testing

**Java unit tests**
- `FIXMessageDataTest` - construction, defensive copy, flat projection, nesting.
- `SendFIXHandlerTest` - groups emitted, counter tag derived from entry count,
  session tags still filtered, placeholders resolved inside group entries.
- `QuickFIXAdapterTest` - outbound message with `NoLegs=2` serialises to a raw
  FIX string with both leg blocks in the right order.
- `QuickFIXApplicationAdapterTest` - inbound message with groups round-trips to
  `FIXMessageData` with both entries.
- `VariableResolverTest` - `{{node:x:g555.0:tag600}}`, out-of-range index error,
  offset variant.
- `ValidationEngineTest` - `groupTag` / `index` / `index: '*'` rules.
- Regression: every existing test must pass untouched. That is the proof the
  flat-map projection preserved backward compatibility.

**UI tests (vitest)**
- `SendFIXConfig.test.tsx` - add group, add entry, edit a field inside an entry,
  delete entry, reorder entries, counter derived from entry count, nested group.
- `scenarioSerializer.test.ts` - YAML round-trip with groups, including nesting.
- `parseFIXMessage.test.ts` - paste a raw multileg message, assert group
  reconstruction; paste an unknown counter tag, assert flat fallback plus warning.

**End-to-end**
Wipe `./data/fixflow.*`, start the app, create a loopback acceptor/initiator
pair on FIXT.1.1, import all five templates, and drive each lifecycle from a
script acting as the client application. Assert the raw FIX on the wire,
particularly that the swap ExecutionReport contains two well-formed leg blocks.

## 11. Out of scope

- Option expiry, settlement/delivery and premium settlement events.
- Multi-currency netting, position keeping, or any P&L computation.
- FIX 4.2 / 4.4 variants of the templates.
- Market data, RFQ or quote negotiation flows.
- Changes to persistence schema or the REST API surface.

## 12. File inventory

**New**
```
fix-flow-core/src/main/java/com/fixflow/core/domain/execution/FIXMessageData.java
fix-flow-ui/src/panels/right/NodeConfig/FieldTable.tsx
fix-flow-ui/src/panels/right/NodeConfig/GroupEditor.tsx
templates/fx/fx-spot-lifecycle.yaml
templates/fx/fx-forward-deliverable-lifecycle.yaml
templates/fx/fx-ndf-lifecycle.yaml
templates/fx/fx-swap-lifecycle.yaml
templates/fx/fx-option-vanilla-lifecycle.yaml
templates/fx/README.md
```

**Modified**
```
fix-flow-core/.../ports/outbound/FIXSessionPort.java
fix-flow-core/.../ports/outbound/InboundMessageListener.java
fix-flow-engine/.../execution/ExecutionContext.java
fix-flow-engine/.../execution/ExecutionManager.java
fix-flow-engine/.../handlers/SendFIXHandler.java
fix-flow-engine/.../handlers/ValidateHandler.java
fix-flow-engine/.../handlers/ExpectFIXHandler.java
fix-flow-engine/.../handlers/RouteFIXHandler.java
fix-flow-engine/.../variable/VariableResolver.java
fix-flow-engine/.../validation/ValidationEngine.java
fix-flow-engine/.../validation/ValidationRuleConfig.java
fix-flow-engine/.../fix/MessageRouter.java
fix-flow-engine/.../fix/MessageBuffer.java
fix-flow-engine/.../correlation/CorrelationEngine.java
fix-flow-adapters/.../quickfixj/QuickFIXAdapter.java
fix-flow-adapters/.../quickfixj/QuickFIXApplicationAdapter.java
fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx
fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx
fix-flow-ui/src/lib/scenarioSerializer.ts
fix-flow-ui/src/lib/parseFIXMessage.ts
fix-flow-ui/src/lib/fixTags.ts
fix-flow-ui/src/lib/scenarioSerializer.test.ts
fix-flow-ui/src/lib/parseFIXMessage.test.ts
fix-flow-ui/src/lib/fixTags.test.ts
fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.test.tsx
fix-flow-ui/src/i18n/locales/{en,it,fr}.json
docs/dsl-reference.md
CLAUDE.md
```

`CLAUDE.md` gains a Gotchas entry: repeating group counter tags are derived from
entry count and must never be written by hand in a `SEND_FIX` config.
