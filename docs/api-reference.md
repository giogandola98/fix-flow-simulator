# API Reference

Base URL: `http://localhost:8080/api`

All request/response bodies are JSON unless noted.

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
