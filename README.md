# FIX Flow Simulator

[![License: FIX Flow SAL v1.0](https://img.shields.io/badge/license-FIX%20Flow%20SAL%20v1.0-orange.svg)](LICENSE)

Visual FIX protocol scenario designer, runtime, and monitor.

## Quick Start

Prerequisites: Java 21+, Maven 3.9+, Node.js 20+.

```bash
# Build everything
~/maven/bin/mvn clean package -DskipTests

# Run
java -jar fix-flow-api/target/fix-flow-api-*.jar

# Open browser
open http://localhost:8080
```

## Features

- Visual flow editor (drag-and-drop FIX scenario design)
- Multi-scenario execution on shared FIX sessions
- FIX 4.2, FIX 4.4, FIX 5.0 SP2 (FIXT.1.1) support
- Per-session configurable: SenderCompID, TargetCompID, host, port, heartbeat interval
- Real-time execution monitoring (WebSocket)
- Validation engine with date/time rules
- ROUTE_FIX: multi-rule conditional routing on inbound FIX fields
- CALL_SCENARIO: reusable sub-flows with input/output variable mapping
- HTTP_REQUEST: REST integration block with response variable capture
- Dynamic placeholders: `{{uuid}}`, `{{now}}`, `{{now:offset:+5m}}`, `{{seq:...}}`, `{{env:VAR}}`, `{{node:id:tagN}}`
- Hot reload scenarios without restarting FIX session
- H2 embedded database (no install required)

## Architecture

- `fix-flow-core` — domain model records (ports + domain types)
- `fix-flow-engine` — execution engine, validation, correlation, hot reload
- `fix-flow-adapters` — JPA/H2 persistence + QuickFIX/J adapter
- `fix-flow-api` — Spring Boot REST + WebSocket + static UI bundle
- `fix-flow-ui` — React + ReactFlow + Tailwind CSS UI

## Documentation

- [Setup guide](docs/setup.md)
- [DSL reference](docs/dsl-reference.md)
- [API reference](docs/api-reference.md)
- [User guide](docs/user-guide.md)

## License

FIX Flow Simulator is **source available** under the
[FIX Flow Simulator Source Available License v1.0](LICENSE).

**You may:** study the source, modify it locally, and redistribute non-commercial forks —
provided you preserve attribution and link back to this repository.

**You may not** (without a Commercial License): offer it as SaaS, sell it, embed it in
a commercial product, or remove attribution.

**Commercial use** requires a separate license. Contact
[giogandola@gmail.com](mailto:giogandola@gmail.com) to enquire.

Copyright (c) 2026 Giorgio Gandola.
