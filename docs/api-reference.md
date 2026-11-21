# API Reference

Base URL: `http://localhost:8080/api/v1` — every path below is relative to it (e.g. `POST /sessions` is
`POST http://localhost:8080/api/v1/sessions`). `/api/...` without `/v1` answers `404`.

All request/response bodies are JSON unless noted.

## Error responses

Every error carries the same body:

```json
{ "status": 404, "error": "Not Found", "message": "...", "timestamp": "2026-09-01T10:00:00Z" }
```

| Status | When |
|---|---|
| `400 Bad Request` | malformed JSON, missing/mistyped parameter, invalid argument, or scenario YAML the DSL parser cannot read — `message` names the line and column |
| `404 Not Found` | unknown entity id — **and** an unknown path (e.g. `/api/sessions` without `/v1`) |
| `405 Method Not Allowed` | path exists, verb does not |
| `409 Conflict` | session busy / conflicting state |
| `500 Internal Server Error` | unhandled fault; `message` is always the fixed string `Internal server error` |
| `503 Service Unavailable` | the embedded database is unusable and will stay that way until restart — `message` carries the cause. See `GET /system/health`. |

---

## Sessions

### List sessions
```
GET /sessions
```
Response: `FIXSessionConfig[]`

### Get session
```
GET /sessions/{id}
```

### Create session
```
POST /sessions
```
Body:
```json
{
  "name": "My Session",
  "mode": "INITIATOR",
  "fixVersion": "FIX_44",
  "defaultApplVerID": null,
  "senderCompID": "CLIENT",
  "targetCompID": "SERVER",
  "host": "localhost",
  "port": 9876,
  "heartbeatInterval": 30,
  "resetOnLogon": true,
  "resetOnLogout": true
}
```

| Field | Required | Values / notes |
|---|---|---|
| `name` | yes | display label |
| `mode` | **yes** | `INITIATOR` (connects to `host`:`port`) \| `ACCEPTOR` (listens on `port`) |
| `fixVersion` | **yes** | `FIX_42` \| `FIX_44` \| `FIXT_11` (use `FIXT_11` for FIX 5.0 / 5.0 SP2) |
| `defaultApplVerID` | with `FIXT_11` | FIX `DefaultApplVerID` (tag 1137), e.g. `"9"` = FIX 5.0 SP2; ignored for `FIX_42`/`FIX_44` |
| `senderCompID` / `targetCompID` | yes | tags 49 / 56 — note the upper-case `ID` |
| `host` | INITIATOR | acceptor address; ignored by an ACCEPTOR |
| `port` | yes | TCP port to connect to (INITIATOR) or listen on (ACCEPTOR) |
| `heartbeatInterval` | yes | seconds (tag 108) |
| `resetOnLogon` / `resetOnLogout` | no | reset sequence numbers; default `false` |
| `autoConnect` | no | connect this session when the application starts (acceptors first, then initiators); absent = `false` on create, unchanged on update. The session response carries it |
| `appDataDictionary` | no | name of an uploaded dictionary (see [Dictionaries](#dictionaries)) used as the application dictionary: `DataDictionary` for `FIX_42`/`FIX_44`, `AppDataDictionary` for `FIXT_11`. `null`/absent keeps the built-in one (`FIX42.xml`, `FIX44.xml`, `FIX50SP2.xml`) |
| `transportDataDictionary` | no | `FIXT_11` only: transport dictionary; `null` keeps `FIXT11.xml` |

A missing or unknown `mode` / `fixVersion` is answered `400 Bad Request` with the allowed values in
`message`. The session response carries `appDataDictionary` and `transportDataDictionary` too.

FIX 5.0 SP2 acceptor example:
```json
{
  "name": "venue-sim",
  "mode": "ACCEPTOR",
  "fixVersion": "FIXT_11",
  "defaultApplVerID": "9",
  "senderCompID": "VENUE",
  "targetCompID": "CLIENT",
  "host": "",
  "port": 7000,
  "heartbeatInterval": 30,
  "resetOnLogon": true,
  "resetOnLogout": true
}
```

### Update session
```
PUT /sessions/{id}
```
Same body as create.

### Delete session
```
DELETE /sessions/{id}
```

### Connect
```
PUT /sessions/{id}/connect
```
A session whose `appDataDictionary` / `transportDataDictionary` names a dictionary that is not stored, or whose file
is not valid QuickFIX/J XML, is answered `400` with the cause (e.g. `dictionary not found: MY.xml`).

### Disconnect
```
PUT /sessions/{id}/disconnect
```

### Session status
```
GET /sessions/{id}/status
```
Response:
```json
{ "sessionId": "abc", "connected": true }
```

### Hot-reload session
```
POST /sessions/{id}/reload
```
Re-applies the stored configuration to a live session without restarting running scenarios.

---

## Dictionaries

QuickFIX/J data dictionaries (XML) a session can select by file name. Files live in the folder set by the
property `fixflow.dictionaries.dir` (default `./dictionaries`). Names match `[A-Za-z0-9._-]+.xml`.

### List dictionaries
```
GET /dictionaries
```
Response: `[{ "name": "MY.xml", "sizeBytes": 2048 }]`

### Upload dictionary
```
POST /dictionaries        (multipart/form-data, part "file")
```
The file name is the dictionary name; an existing file with that name is replaced. The XML is parsed as a
QuickFIX/J `DataDictionary` first: invalid XML or an unsafe name is `400`. Response `201`: `{ "name", "sizeBytes" }`.

### Delete dictionary
```
DELETE /dictionaries/{name}
```
`204`; `409` naming the sessions if one of them uses the dictionary.

---

## Data sets

Named tables of string values (a header of unique column names, rows of the same width), editable in the UI and
meant to feed runs of a scenario row by row. A data set may carry a `packId` when it belongs to a provider pack.
Names are unique: a duplicate is `409`.

### List data sets
```
GET /datasets
```
Response: `[{ "id", "name", "packId", "columns": ["a","b"], "rowCount": 2 }]` (no rows).

### Get data set
```
GET /datasets/{id}
```
Full body: `{ "id", "name", "packId", "columns", "rows": [["1","2"]], "rowCount" }`. `404` if unknown.

### Create / update / delete
```
POST   /datasets        { "name", "columns", "rows" }   -> 201
PUT    /datasets/{id}   { "name", "columns", "rows" }   -> 200 (keeps id and packId)
DELETE /datasets/{id}                                   -> 204
```
`400` for a blank name, no or duplicate columns, or a row whose size differs from the column count.

### Import CSV
```
POST /datasets/import        (multipart/form-data, parts "file" and "name")
```
RFC 4180 CSV with a header row; a UTF-8 BOM is stripped, CRLF and LF are accepted, fields may be quoted
(commas, doubled quotes, newlines). `name` defaults to the file name without `.csv`. Response `201`: the full
data set. A malformed file is `400` naming the line, e.g. `line 3: 2 values, header has 3`; nothing is stored.

### Export CSV
```
GET /datasets/{id}/export
```
`text/csv` (UTF-8, LF line ends, fields quoted only when needed), as an attachment named `<name>.csv`.

---

## Packs

Provider packs: dictionaries, data sets, scenarios and session templates installed as one unit. The pack format,
the name references and the update rules are in [Provider packs](packs.md).

### List packs
```
GET /packs
```
Response:
```json
[
  {
    "id": "demo", "name": "Demo pack", "version": "1.0.0", "provider": "Demo", "description": "...",
    "installedAt": "2026-11-21T10:00:00Z", "updatedAt": "2026-11-21T10:00:00Z",
    "counts": { "scenarios": 2, "datasets": 1, "sessions": 1, "dictionaries": 1 }
  }
]
```

### Inspect a pack
```
POST /packs/inspect          (multipart/form-data, part "file": the zip)
```
Reads `pack.yaml` without installing anything, so a form can ask for the values:
```json
{
  "id": "demo", "name": "Demo pack", "version": "1.0.0", "provider": "Demo", "engineMin": "0.8.0",
  "description": "...",
  "values": [
    { "name": "SENDER", "description": "SenderCompID of the simulator", "required": true },
    { "name": "PORT", "description": "...", "default": "9876", "required": false }
  ],
  "installedVersion": "0.9.0",
  "installedValues": { "SENDER": "SIM", "PORT": "9876" }
}
```
`installedVersion` and `installedValues` appear only when a pack with the same `id` is installed.

### Import (install or update) a pack
```
POST /packs                  (multipart/form-data, parts "file": the zip, "values": a JSON object string)
```
Example `values`: `{"SENDER":"SIM","TARGET":"SUT"}`. Response `201`:
```json
{ "packId": "demo", "version": "1.0.0", "scenarios": 2, "datasets": 1, "sessions": 1, "dictionaries": 1,
  "warnings": [] }
```
A pack with the same `id` is updated in place (objects matched by name keep their ids; objects no longer in the zip
are removed). `400`: not a zip, bad `pack.yaml`, `engineMin` above this engine, missing values (all listed), a file
that does not parse or a name reference that does not resolve (file and reference named), `values` that is not a
JSON object. `409`: an execution of the pack is running (ids listed), or a data set name is taken outside the pack.
Nothing is written when the import is refused.

### Export a pack
```
GET /packs/{id}/export
```
`application/zip` attachment `<id>-<version>.zip`, in the pack layout, names instead of ids. `404` if unknown.

### Delete a pack
```
DELETE /packs/{id}
```
`204`. Removes the pack's scenarios, data sets, sessions (disconnected first) and dictionaries. `409` while one of its
executions runs; `404` if unknown.

---

## Scenarios

### List scenarios
```
GET /scenarios
GET /scenarios?packId=demo   # only the scenarios that pack installed
```
Response: `Scenario[]`

```json
[
  {
    "id": "uuid",
    "name": "RFQ Flow",
    "description": "Quote request/response",
    "version": "1.0",
    "sessionRef": "default",
    "nodeCount": 5,
    "packId": null
  }
]
```
`packId` is the provider pack that installed the scenario, `null` otherwise. The list carries no `yamlDsl`; get one
scenario for it.

### Get scenario
```
GET /scenarios/{id}
```

### Create scenario
```
POST /scenarios
```
Body:
```json
{
  "name": "My Scenario",
  "description": "...",
  "version": "1.0",
  "sessionRef": "session-id",
  "yamlDsl": "id: my-scenario\n..."
}
```

### Update scenario
```
PUT /scenarios/{id}
```
Same body as create.

### Duplicate scenario
```
POST /scenarios/{id}/duplicate
```
Body (optional):
```json
{ "name": "My copy" }
```
Returns `201` with the copy, `yamlDsl` included. The copy gets a **new scenario id and new node
ids**, and every reference to those node ids is rewritten: `onSuccess` / `onFailure` / `onTimeout`,
`timeout.jumpTo`, the visual `edges`, `ROUTE_FIX` rule targets, `EXPECT_FIX` `correlation.fromNode`,
`VALIDATE` `sourceNodeId`, date rules' `sourceNode`, and `{{node:<id>:...}}` placeholders. Node
positions and any other key in the source YAML are preserved, so the copy opens with the same
layout. Without a `name`, the copy is called `<name> (copy)`.

### Delete scenario
```
DELETE /scenarios/{id}
```
`204`. The scenario can no longer be executed or called as a sub-flow from that moment (it is removed from the
engine's registry too, not only from the database).

### Execute scenario
```
POST /scenarios/{id}/execute
```
Body (every field optional):
```json
{
  "sessionId": "session-uuid",
  "datasetId": "data-set-uuid",
  "rowIndex": 0,
  "allRows": false,
  "vars": { "qty": "100" }
}
```
- `datasetId`: data set to read; without it the scenario's `datasetRef` (name or id) is used. `rowIndex` is the 0-based row (default 0).
- `allRows: true`: one execution per row, started one after another on one worker; a failed row does not stop the next. Needs a data set.
- `vars`: scenario variables, applied after the scenario's own `variables` defaults.
- Unknown data set, no data set for a requested row, empty data set or a row out of range answer `400` before anything starts.

Response: `202 Accepted`, `Location: /api/v1/executions/{executionId}` (the first execution)
```json
{ "executionId": "exec-uuid", "executionIds": ["exec-uuid"] }
```
`executionIds` lists every execution in row order (one element for a plain run); `executionId` is the first one.

### Import scenario (YAML file)
```
POST /scenarios/import
Content-Type: multipart/form-data
```
Field: `file` — a `.yaml` file containing a scenario DSL document.

### Export scenario
```
GET /scenarios/{id}/export
```
Response: `application/octet-stream` YAML file download.

---

## Executions

### Get execution
```
GET /executions/{id}
```
Response:
```json
{
  "id": "exec-uuid",
  "scenarioId": "scenario-uuid",
  "scenarioVersion": "1.0",
  "sessionId": "session-uuid",
  "status": "RUNNING",
  "startTime": "2024-01-01T10:00:00Z",
  "endTime": null,
  "currentNodeId": "expect-quote"
}
```
`status`: `RUNNING` | `PASSED` | `FAILED` | `STOPPED`

There is no endpoint listing all executions; keep the `executionId` returned by
`POST /scenarios/{id}/execute`.

### Execution events
```
GET /executions/{id}/events
```
Response: the execution's event log (`EXECUTION_STARTED`, `NODE_ENTERED`, `NODE_EXITED`,
`MESSAGE_SENT`, `MESSAGE_RECEIVED`, `TIMEOUT`, `ERROR`, `OPERATOR_PROMPT`, `OPERATOR_ANSWERED`, `EXECUTION_FINISHED`, …) in order.
Child runs of `CALL_SCENARIO` and `PARALLEL` have no event log of their own; a `PARALLEL` node reports
for its children in its `NODE_EXITED` detail (`"<passed>/<total> children passed"`), and every inbound
message on the session is recorded against the parent execution.

### Operator prompt
```
GET /executions/{id}/operator
```
Response: the open prompt of an `OPERATOR` node, or `204` when the execution is not waiting for a person.
Prompts of `CALL_SCENARIO` / `PARALLEL` children are included (one of them when several are open); the
`executionId` in the body is the one to answer on.
```json
{ "executionId": "…", "nodeId": "ask-dealer", "instruction": "Accept the quote…",
  "confirmLabel": "Accepted", "failLabel": null }
```
```
POST /executions/{executionId}/operator/{nodeId}
{ "ok": true, "note": "done on the venue" }
```
`204` when the prompt was open and is now answered; `404` when no such prompt is open (already answered,
timed out, stopped). Events on `/topic/executions/{id}/events`: `OPERATOR_PROMPT` (detail = instruction)
and `OPERATOR_ANSWERED` (detail `ok: <note>` / `fail: <note>` / `timeout` / `cancelled`). When several child prompts are open, the oldest is returned first.

### Execution FIX messages
```
GET /executions/{id}/messages
```
Response: the FIX messages sent and received by the execution.

### Stop execution
```
POST /executions/{id}/stop
```

### Get execution report
```
GET /executions/{id}/report
```
Response:
```json
{
  "executionId": "exec-uuid",
  "scenarioName": "RFQ Flow",
  "scenarioVersion": "1.0",
  "sessionName": "My Session",
  "status": "PASSED",
  "startTime": "2024-01-01T10:00:00Z",
  "endTime": "2024-01-01T10:00:05Z",
  "durationMs": 5123,
  "nodeResults": [
    { "nodeId": "send-qr", "nodeName": "Send QuoteRequest", "status": "PASSED", "durationMs": 12 }
  ],
  "rawFIXMessages": [
    "8=FIX.4.2|9=...|35=R|..."
  ],
  "validationErrors": [],
  "statistics": {
    "totalNodes": 6,
    "passedNodes": 6,
    "failedNodes": 0,
    "messagesSent": 1,
    "messagesReceived": 1
  }
}
```

### Download execution report
```
GET /executions/{id}/report/download
```
Response: `application/octet-stream` JSON file download.

---

## System

### Health check
```
GET /system/health
```
Probes the embedded H2 store with a real query.

`200 OK`:
```json
{ "status": "UP", "database": "UP", "timestamp": "2026-09-01T10:00:00Z" }
```

`503 Service Unavailable` when the store is unusable:
```json
{
  "status": "DOWN",
  "database": "DOWN",
  "reason": "MVStoreException: Writing to sun.nio.ch.FileChannelImpl@17e680db failed",
  "timestamp": "2026-09-01T10:00:00Z"
}
```
A store failure is permanent until the simulator is restarted. While it is down every
other endpoint also answers `503` (not `500`) with the same cause, so a test harness can
tell "restart me" apart from "that one request failed".

### Shutdown the simulator
```
POST /system/shutdown
```
Returns `202 Accepted` and terminates the JVM shortly after. Used by the
Shutdown button in the top bar. Repeated calls are idempotent — only the first
starts the exit.

---

## WebSocket

Connect to `ws://localhost:8080/ws` using SockJS + STOMP.

### Subscribe to execution events
```
SUBSCRIBE /topic/executions/{executionId}/events
```
Payload:
```json
{
  "type": "NODE_ENTERED",
  "nodeId": "send-qr",
  "nodeName": "Send QuoteRequest",
  "timestamp": "2024-01-01T10:00:00.123Z",
  "details": {}
}
```
Event types: `EXECUTION_STARTED` | `EXECUTION_FINISHED` | `NODE_ENTERED` | `NODE_EXITED` | `MESSAGE_SENT` | `MESSAGE_RECEIVED` | `TIMEOUT` | `ERROR`

`MESSAGE_RECEIVED` is emitted for **every** inbound application message on a session with a running
execution, and its detail says whether a block was waiting for it. A message no block matched is
buffered and reported all the same — it is not silently dropped from the log.

### Subscribe to FIX messages
```
SUBSCRIBE /topic/executions/{executionId}/messages
```
Payload:
```json
{
  "direction": "SENT",
  "rawMessage": "8=FIX.4.2|9=...",
  "msgType": "R",
  "timestamp": "2024-01-01T10:00:00.123Z"
}
```

### Subscribe to session status
```
SUBSCRIBE /topic/sessions/{sessionId}/status
```
Payload:
```json
{ "sessionId": "abc", "connected": true }
```
