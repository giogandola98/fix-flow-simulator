# Setup Guide

## Prerequisites

- Java 21 or later (`java -version`)
- Maven 3.9 or later (`~/maven/bin/mvn -version`)
- Node.js 20 or later (`node --version`) — only needed for UI development

## Production build

```bash
~/maven/bin/mvn clean package -DskipTests
java -jar fix-flow-api/target/fix-flow-api-*.jar
```

The fat JAR bundles the React UI build under `/static`. Open
`http://localhost:8080` once the application logs `Started FixFlowApplication`.

## Development mode

Run the backend with hot reload:

```bash
~/maven/bin/mvn -pl fix-flow-api spring-boot:run
```

Run the UI with Vite dev server (proxies `/api` and `/ws` to port 8080):

```bash
cd fix-flow-ui
npm install
npm run dev
```

Open `http://localhost:5173`.

## H2 console

The embedded database is exposed at `http://localhost:8080/h2-console`
with JDBC URL `jdbc:h2:file:./data/fixflow`.

## Troubleshooting

- **Port 8080 in use**: pass `--server.port=8090` on the `java -jar` command.
- **WebSocket disconnects**: verify there is no reverse proxy stripping `/ws`.
- **UI does not build**: delete `fix-flow-ui/node_modules` and rerun `npm install`.
- **FIX session won't connect**: confirm the counterparty CompIDs and that
  `host`/`port` are reachable.
