# FIX Flow Simulator

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
