# API Reference

Base URL: `http://localhost:8080/api`

All request/response bodies are JSON unless noted.

## Error responses

Every error carries the same body:

```json
{ "status": 404, "error": "Not Found", "message": "...", "timestamp": "2026-09-01T10:00:00Z" }
```

| Status | When |
|---|---|
| `400 Bad Request` | malformed JSON, missing/mistyped parameter, invalid argument |
| `404 Not Found` | unknown entity id — **and** an unknown path (e.g. `/api/sessions` without `/v1`) |
| `405 Method Not Allowed` | path exists, verb does not |
| `409 Conflict` | session busy / conflicting state |
| `500 Internal Server Error` | unhandled fault; `message` is always the fixed string `Internal server error` |
| `503 Service Unavailable` | the embedded database is unusable and will stay that way until restart — `message` carries the cause. See `GET /api/v1/system/health`. |

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
  "fixVersion": "FIX42",
  "senderCompId": "CLIENT",
  "targetCompId": "SERVER",
  "host": "localhost",
  "port": 9876,
  "heartbeatInterval": 30
}
```
`fixVersion`: `FIX42` | `FIX44` | `FIX50SP2`

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

---

## Scenarios

### List scenarios
```
GET /scenarios
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
    "yamlDsl": "..."
  }
]
```

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

### Execute scenario
```
POST /scenarios/{id}/execute
```
Body:
```json
{ "sessionId": "session-uuid" }
```
Response:
```json
{ "executionId": "exec-uuid" }
```

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

### List executions
```
GET /executions
```
Optional query params: `scenarioId`, `status`

### Get execution
```
GET /executions/{id}
```
Response:
```json
{
  "id": "exec-uuid",
  "scenarioId": "scenario-uuid",
  "sessionId": "session-uuid",
  "status": "RUNNING",
  "startedAt": "2024-01-01T10:00:00Z",
  "endedAt": null
}
```
`status`: `PENDING` | `RUNNING` | `PASSED` | `FAILED` | `TIMEOUT` | `ABORTED`

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
GET /api/v1/system/health
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
POST /api/v1/system/shutdown
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
