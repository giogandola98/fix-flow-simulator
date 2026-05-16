# FIX Flow Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-ready FIX protocol simulation tool with visual flow editor, multi-session runtime, and real-time monitoring.

**Architecture:** Monolithic fat-JAR with hexagonal internals. Maven multi-module (core/engine/adapters/api) + React Vite frontend built into JAR static resources. H2 file-mode DB, WebSocket STOMP for live events.

**Tech Stack:** Java 21, Spring Boot 3.3.x, QuickFIX/J 2.3.x, Jackson YAML, JPA/H2, WebSocket STOMP, React 18, TypeScript, ReactFlow, Zustand, TanStack Query, Tailwind CSS, Maven, Vite

---

## File Structure

```
fix-flow-simulator/
├── pom.xml                                       # Root parent POM
├── fix-flow-core/                                # Pure domain + ports (no Spring)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/fixflow/core/
│       │   ├── domain/
│       │   │   ├── scenario/
│       │   │   │   ├── Scenario.java
│       │   │   │   ├── ScenarioNode.java
│       │   │   │   ├── ScenarioEdge.java
│       │   │   │   ├── NodeType.java
│       │   │   │   ├── TimeoutConfig.java
│       │   │   │   ├── RetryPolicy.java
│       │   │   │   ├── RoutingRule.java
│       │   │   │   ├── CorrelationRule.java
│       │   │   │   ├── VariableDef.java
│       │   │   │   ├── RuntimePolicy.java
│       │   │   │   ├── TimeUnit.java
│       │   │   │   └── TimeoutAction.java
│       │   │   ├── session/
│       │   │   │   ├── FIXSessionConfig.java
│       │   │   │   ├── FIXVersion.java
│       │   │   │   └── FIXMode.java
│       │   │   └── execution/
│       │   │       ├── Execution.java
│       │   │       ├── ExecutionStatus.java
│       │   │       ├── ExecutionEvent.java
│       │   │       ├── ExecutionEventType.java
│       │   │       ├── NodeResult.java
│       │   │       ├── FIXMessage.java
│       │   │       └── Direction.java
│       │   └── ports/
│       │       ├── inbound/
│       │       │   ├── ScenarioUseCase.java
│       │       │   ├── ExecutionUseCase.java
│       │       │   └── SessionUseCase.java
│       │       └── outbound/
│       │           ├── ScenarioRepositoryPort.java
│       │           ├── ExecutionRepositoryPort.java
│       │           ├── FIXSessionPort.java
│       │           ├── InboundMessageListener.java
│       │           └── EventPublisherPort.java
│       └── test/java/com/fixflow/core/
│           └── domain/ScenarioDomainTest.java
├── fix-flow-engine/                              # Orchestration + handlers + parser
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/fixflow/engine/
│       │   ├── scenario/
│       │   │   ├── ScenarioDslParser.java
│       │   │   └── ScenarioRegistry.java
│       │   ├── fix/
│       │   │   ├── FIXSessionManager.java
│       │   │   ├── MessageBuffer.java
│       │   │   └── MessageRouter.java
│       │   ├── correlation/CorrelationEngine.java
│       │   ├── execution/
│       │   │   ├── ExecutionContext.java
│       │   │   └── ExecutionManager.java
│       │   └── handlers/
│       │       ├── NodeHandler.java
│       │       ├── NodeHandlerResult.java
│       │       ├── NodeDispatcher.java
│       │       ├── SendFIXHandler.java
│       │       ├── ExpectFIXHandler.java
│       │       └── EndHandler.java
│       └── test/java/com/fixflow/engine/
│           ├── scenario/ScenarioDslParserTest.java
│           ├── scenario/ScenarioRegistryTest.java
│           ├── fix/FIXSessionManagerTest.java
│           ├── fix/FakeFixAdapter.java
│           ├── fix/FakeFixAdapterTest.java
│           ├── fix/MessageBufferTest.java
│           ├── fix/MessageRouterTest.java
│           ├── correlation/CorrelationEngineTest.java
│           ├── execution/ExecutionManagerTest.java
│           ├── handlers/SendFIXHandlerTest.java
│           ├── handlers/ExpectFIXHandlerTest.java
│           ├── MultiScenarioIntegrationTest.java
│           └── TimeoutRetryTest.java
├── fix-flow-adapters/                            # JPA + QuickFIX/J
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/fixflow/adapters/
│       │   ├── persistence/
│       │   │   ├── entity/
│       │   │   │   ├── ScenarioEntity.java
│       │   │   │   ├── ScenarioVersionEntity.java
│       │   │   │   ├── FIXSessionEntity.java
│       │   │   │   ├── ExecutionEntity.java
│       │   │   │   ├── ExecutionEventEntity.java
│       │   │   │   ├── FIXMessageEntity.java
│       │   │   │   ├── NodeResultEntity.java
│       │   │   │   └── ValidationErrorEntity.java
│       │   │   ├── jpa/
│       │   │   │   ├── JpaScenarioRepository.java
│       │   │   │   ├── JpaScenarioVersionRepository.java
│       │   │   │   ├── JpaFIXSessionRepository.java
│       │   │   │   ├── JpaExecutionRepository.java
│       │   │   │   ├── JpaExecutionEventRepository.java
│       │   │   │   ├── JpaFIXMessageRepository.java
│       │   │   │   └── JpaNodeResultRepository.java
│       │   │   ├── ScenarioRepositoryAdapter.java
│       │   │   ├── ExecutionRepositoryAdapter.java
│       │   │   └── FIXSessionRepositoryAdapter.java
│       │   └── quickfixj/
│       │       ├── QuickFIXApplicationAdapter.java
│       │       ├── QuickFIXAdapter.java
│       │       └── InboundMessageListener.java
│       └── test/java/com/fixflow/adapters/
│           ├── persistence/ScenarioRepositoryAdapterTest.java
│           ├── persistence/ScenarioPersistenceTest.java
│           └── quickfixj/QuickFIXApplicationAdapterTest.java
├── fix-flow-api/                                 # Spring Boot app + REST + WS
│   ├── pom.xml
│   └── src/main/resources/application.yml
└── fix-flow-ui/                                  # React Vite (Parts 2-3)
    └── pom.xml
```

---

## Phase 1: Maven Scaffold + Core Domain Model + DSL Parsing

### Task 1: Maven root POM + module scaffold

**Files:**
- Create: `pom.xml` (root)
- Create: `fix-flow-core/pom.xml`
- Create: `fix-flow-engine/pom.xml`
- Create: `fix-flow-adapters/pom.xml`
- Create: `fix-flow-api/pom.xml`
- Create: `fix-flow-ui/pom.xml`

**Steps:**

- [ ] **1.1 — Create root `pom.xml`** with the following full content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.fixflow</groupId>
    <artifactId>fix-flow-simulator</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>FIX Flow Simulator</name>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/>
    </parent>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <quickfixj.version>2.3.1</quickfixj.version>
        <jackson.version>2.17.1</jackson.version>
        <lombok.version>1.18.32</lombok.version>
        <h2.version>2.2.224</h2.version>
    </properties>

    <modules>
        <module>fix-flow-core</module>
        <module>fix-flow-engine</module>
        <module>fix-flow-adapters</module>
        <module>fix-flow-api</module>
        <module>fix-flow-ui</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.fixflow</groupId>
                <artifactId>fix-flow-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fixflow</groupId>
                <artifactId>fix-flow-engine</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fixflow</groupId>
                <artifactId>fix-flow-adapters</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>org.quickfixj</groupId>
                <artifactId>quickfixj-core</artifactId>
                <version>${quickfixj.version}</version>
            </dependency>
            <dependency>
                <groupId>org.quickfixj</groupId>
                <artifactId>quickfixj-messages-fix42</artifactId>
                <version>${quickfixj.version}</version>
            </dependency>
            <dependency>
                <groupId>org.quickfixj</groupId>
                <artifactId>quickfixj-messages-fix44</artifactId>
                <version>${quickfixj.version}</version>
            </dependency>
            <dependency>
                <groupId>org.quickfixj</groupId>
                <artifactId>quickfixj-messages-fix50sp2</artifactId>
                <version>${quickfixj.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fasterxml.jackson.dataformat</groupId>
                <artifactId>jackson-dataformat-yaml</artifactId>
                <version>${jackson.version}</version>
            </dependency>
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
                <scope>provided</scope>
            </dependency>
            <dependency>
                <groupId>com.h2database</groupId>
                <artifactId>h2</artifactId>
                <version>${h2.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <source>21</source>
                        <target>21</target>
                        <release>21</release>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **1.2 — Create `fix-flow-core/pom.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-simulator</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>fix-flow-core</artifactId>
    <name>FIX Flow Core (Domain + Ports)</name>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **1.3 — Create `fix-flow-engine/pom.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-simulator</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>fix-flow-engine</artifactId>
    <name>FIX Flow Engine (Orchestration)</name>

    <dependencies>
        <dependency>
            <groupId>com.fixflow</groupId>
            <artifactId>fix-flow-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **1.4 — Create `fix-flow-adapters/pom.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-simulator</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>fix-flow-adapters</artifactId>
    <name>FIX Flow Adapters (JPA + QuickFIX/J)</name>

    <dependencies>
        <dependency>
            <groupId>com.fixflow</groupId>
            <artifactId>fix-flow-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fixflow</groupId>
            <artifactId>fix-flow-engine</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
        </dependency>
        <dependency>
            <groupId>org.quickfixj</groupId>
            <artifactId>quickfixj-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.quickfixj</groupId>
            <artifactId>quickfixj-messages-fix42</artifactId>
        </dependency>
        <dependency>
            <groupId>org.quickfixj</groupId>
            <artifactId>quickfixj-messages-fix44</artifactId>
        </dependency>
        <dependency>
            <groupId>org.quickfixj</groupId>
            <artifactId>quickfixj-messages-fix50sp2</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **1.5 — Create `fix-flow-api/pom.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-simulator</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>fix-flow-api</artifactId>
    <name>FIX Flow API (Spring Boot App)</name>

    <dependencies>
        <dependency>
            <groupId>com.fixflow</groupId>
            <artifactId>fix-flow-adapters</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **1.6 — Create `fix-flow-ui/pom.xml`** (placeholder for now; React build wired in Part 3):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-simulator</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>fix-flow-ui</artifactId>
    <packaging>pom</packaging>
    <name>FIX Flow UI (React Vite)</name>
</project>
```

- [ ] **1.7 — Create the directory skeleton** (run from repo root):

```bash
mkdir -p fix-flow-core/src/main/java/com/fixflow/core/{domain/scenario,domain/session,domain/execution,ports/inbound,ports/outbound}
mkdir -p fix-flow-core/src/test/java/com/fixflow/core/domain
mkdir -p fix-flow-engine/src/main/java/com/fixflow/engine/{scenario,fix,correlation,execution,handlers}
mkdir -p fix-flow-engine/src/test/java/com/fixflow/engine/{scenario,fix,correlation,execution,handlers}
mkdir -p fix-flow-adapters/src/main/java/com/fixflow/adapters/{persistence/entity,persistence/jpa,quickfixj}
mkdir -p fix-flow-adapters/src/test/java/com/fixflow/adapters/{persistence,quickfixj}
mkdir -p fix-flow-api/src/main/java/com/fixflow/api
mkdir -p fix-flow-api/src/main/resources
mkdir -p fix-flow-ui
```

- [ ] **1.8 — Run validation:**
  ```bash
  mvn validate
  ```
  Expected: `BUILD SUCCESS` listing all five reactor modules.

- [ ] **1.9 — Commit:**
  ```bash
  git add pom.xml fix-flow-*/pom.xml
  git commit -m "scaffold: maven multi-module skeleton (core, engine, adapters, api, ui)"
  ```

---

### Task 2: Core domain model — Scenario, Node, Edge

**Files:**
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/NodeType.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/TimeUnit.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/TimeoutAction.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/RuntimePolicy.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/TimeoutConfig.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/RetryPolicy.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/RoutingRule.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/CorrelationRule.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/VariableDef.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/ScenarioNode.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/ScenarioEdge.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/scenario/Scenario.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/session/FIXVersion.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/session/FIXMode.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/session/FIXSessionConfig.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/ExecutionStatus.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/ExecutionEventType.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/Direction.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/ExecutionEvent.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/NodeResult.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/FIXMessage.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/domain/execution/Execution.java`
- Create: `fix-flow-core/src/test/java/com/fixflow/core/domain/ScenarioDomainTest.java`

**Steps:**

- [ ] **2.1 — Write the failing test first** at `fix-flow-core/src/test/java/com/fixflow/core/domain/ScenarioDomainTest.java`:

```java
package com.fixflow.core.domain;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDomainTest {

    @Test
    void scenarioExposesNodeLookupById() {
        ScenarioNode start = new ScenarioNode(
            "n1", "Start", NodeType.START, Map.of(), null, null, "n2", null, null);
        ScenarioNode end = new ScenarioNode(
            "n2", "End", NodeType.END_PASS, Map.of(), null, null, null, null, null);

        Scenario scenario = new Scenario(
            UUID.randomUUID(), "demo", "test scenario", "1.0", "sess-1",
            RuntimePolicy.PARALLEL, List.of(), List.of(),
            List.of(start, end),
            List.of(new ScenarioEdge("n1", "n2", "ok")),
            Map.of());

        assertThat(scenario.findNode("n1")).contains(start);
        assertThat(scenario.findNode("n2")).contains(end);
        assertThat(scenario.findNode("missing")).isEmpty();
        assertThat(scenario.nodes()).hasSize(2);
    }

    @Test
    void timeoutConfigUsesEnumUnits() {
        TimeoutConfig tc = new TimeoutConfig(5, TimeUnit.SECONDS, TimeoutAction.FAIL, null);
        assertThat(tc.value()).isEqualTo(5);
        assertThat(tc.unit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(tc.onTimeout()).isEqualTo(TimeoutAction.FAIL);
    }
}
```

Run: `mvn test -pl fix-flow-core` — expect **compilation failure** (no domain types yet).

- [ ] **2.2 — `NodeType.java`:**

```java
package com.fixflow.core.domain.scenario;

public enum NodeType {
    START, SEND_FIX, EXPECT_FIX, VALIDATE, WAIT, TIMEOUT,
    DECISION, BRANCH, RETRY, LOOP, DELAY, END_PASS, END_FAIL
}
```

- [ ] **2.3 — `TimeUnit.java`:**

```java
package com.fixflow.core.domain.scenario;

public enum TimeUnit { MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS }
```

- [ ] **2.4 — `TimeoutAction.java`:**

```java
package com.fixflow.core.domain.scenario;

public enum TimeoutAction { FAIL, RETRY, CONTINUE, JUMP }
```

- [ ] **2.5 — `RuntimePolicy.java`:**

```java
package com.fixflow.core.domain.scenario;

public enum RuntimePolicy { PARALLEL, SEQUENTIAL, EXCLUSIVE }
```

- [ ] **2.6 — `TimeoutConfig.java`:**

```java
package com.fixflow.core.domain.scenario;

public record TimeoutConfig(long value, TimeUnit unit, TimeoutAction onTimeout, String jumpTo) {
    public long toMillis() {
        return switch (unit) {
            case MILLISECONDS -> value;
            case SECONDS -> value * 1_000L;
            case MINUTES -> value * 60_000L;
            case HOURS   -> value * 3_600_000L;
            case DAYS    -> value * 86_400_000L;
        };
    }
}
```

- [ ] **2.7 — `RetryPolicy.java`:**

```java
package com.fixflow.core.domain.scenario;

public record RetryPolicy(int maxAttempts, long delayMs) {
    public RetryPolicy {
        if (maxAttempts < 0) throw new IllegalArgumentException("maxAttempts must be >= 0");
        if (delayMs < 0) throw new IllegalArgumentException("delayMs must be >= 0");
    }
}
```

- [ ] **2.8 — `RoutingRule.java`:**

```java
package com.fixflow.core.domain.scenario;

import java.util.Map;

public record RoutingRule(Map<String, String> criteria, String scenarioId, int priority) {}
```

- [ ] **2.9 — `CorrelationRule.java`:**

```java
package com.fixflow.core.domain.scenario;

public record CorrelationRule(int sourceTag, String targetNode, int targetTag, long timeWindowMs) {}
```

- [ ] **2.10 — `VariableDef.java`:**

```java
package com.fixflow.core.domain.scenario;

public record VariableDef(String type, String defaultValue) {}
```

- [ ] **2.11 — `ScenarioNode.java`:**

```java
package com.fixflow.core.domain.scenario;

import java.util.Map;

public record ScenarioNode(
        String id,
        String name,
        NodeType type,
        Map<String, Object> config,
        TimeoutConfig timeout,
        RetryPolicy retryPolicy,
        String onSuccess,
        String onFailure,
        String onTimeout
) {
    public ScenarioNode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("node id required");
        if (type == null) throw new IllegalArgumentException("node type required");
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
```

- [ ] **2.12 — `ScenarioEdge.java`:**

```java
package com.fixflow.core.domain.scenario;

public record ScenarioEdge(String from, String to, String label) {}
```

- [ ] **2.13 — `Scenario.java`:**

```java
package com.fixflow.core.domain.scenario;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record Scenario(
        UUID id,
        String name,
        String description,
        String version,
        String sessionRef,
        RuntimePolicy runtimePolicy,
        List<RoutingRule> routingRules,
        List<CorrelationRule> correlationRules,
        List<ScenarioNode> nodes,
        List<ScenarioEdge> edges,
        Map<String, VariableDef> variables
) {
    public Scenario {
        if (id == null) throw new IllegalArgumentException("scenario id required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("scenario name required");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        routingRules = routingRules == null ? List.of() : List.copyOf(routingRules);
        correlationRules = correlationRules == null ? List.of() : List.copyOf(correlationRules);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public Optional<ScenarioNode> findNode(String nodeId) {
        if (nodeId == null) return Optional.empty();
        return nodes.stream().filter(n -> nodeId.equals(n.id())).findFirst();
    }

    public Optional<ScenarioNode> startNode() {
        return nodes.stream().filter(n -> n.type() == NodeType.START).findFirst();
    }
}
```

- [ ] **2.14 — Session enums + config**

`FIXVersion.java`:
```java
package com.fixflow.core.domain.session;

public enum FIXVersion { FIX_42, FIX_44, FIXT_11 }
```

`FIXMode.java`:
```java
package com.fixflow.core.domain.session;

public enum FIXMode { INITIATOR, ACCEPTOR }
```

`FIXSessionConfig.java`:
```java
package com.fixflow.core.domain.session;

import java.util.UUID;

public record FIXSessionConfig(
        UUID id,
        String name,
        FIXMode mode,
        FIXVersion fixVersion,
        String defaultApplVerID,
        String senderCompID,
        String targetCompID,
        String host,
        int port,
        int heartbeatInterval,
        int reconnectInterval,
        boolean resetOnLogon,
        boolean resetOnLogout
) {
    public FIXSessionConfig {
        if (id == null) throw new IllegalArgumentException("session id required");
        if (fixVersion == null) throw new IllegalArgumentException("fixVersion required");
        if (mode == null) throw new IllegalArgumentException("mode required");
    }
}
```

- [ ] **2.15 — Execution domain types**

`ExecutionStatus.java`:
```java
package com.fixflow.core.domain.execution;

public enum ExecutionStatus { RUNNING, PASSED, FAILED, STOPPED }
```

`ExecutionEventType.java`:
```java
package com.fixflow.core.domain.execution;

public enum ExecutionEventType {
    EXECUTION_STARTED, EXECUTION_FINISHED, NODE_ENTERED, NODE_EXITED,
    MESSAGE_SENT, MESSAGE_RECEIVED, TIMEOUT, ERROR, SESSION_UP, SESSION_DOWN
}
```

`Direction.java`:
```java
package com.fixflow.core.domain.execution;

public enum Direction { INBOUND, OUTBOUND }
```

`ExecutionEvent.java`:
```java
package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionEvent(
        UUID id,
        UUID executionId,
        ExecutionEventType type,
        String nodeId,
        Instant timestamp,
        String detail,
        String rawFix
) {
    public static ExecutionEvent of(UUID executionId, ExecutionEventType type, String nodeId, String detail) {
        return new ExecutionEvent(UUID.randomUUID(), executionId, type, nodeId, Instant.now(), detail, null);
    }
}
```

`NodeResult.java`:
```java
package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.UUID;

public record NodeResult(
        UUID id,
        UUID executionId,
        String nodeId,
        String status,
        Instant startTime,
        Instant endTime,
        String error
) {}
```

`FIXMessage.java`:
```java
package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FIXMessage(
        UUID id,
        UUID executionId,
        Direction direction,
        String rawFix,
        Map<Integer, String> fields,
        Instant receivedAt
) {
    public FIXMessage {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
```

`Execution.java`:
```java
package com.fixflow.core.domain.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Execution(
        UUID id,
        UUID scenarioId,
        String scenarioVersion,
        UUID sessionId,
        ExecutionStatus status,
        Instant startTime,
        Instant endTime,
        String currentNodeId,
        Map<String, String> variables,
        List<NodeResult> nodeResults,
        List<ExecutionEvent> events
) {
    public Execution {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        nodeResults = nodeResults == null ? List.of() : List.copyOf(nodeResults);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
```

- [ ] **2.16 — Run tests:**
  ```bash
  mvn test -pl fix-flow-core
  ```
  Expected: `ScenarioDomainTest` passes (2/2).

- [ ] **2.17 — Commit:**
  ```bash
  git add fix-flow-core
  git commit -m "core: domain model (scenario, session, execution records)"
  ```

---

### Task 3: Port interfaces (hexagonal boundaries)

**Files:**
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/inbound/ScenarioUseCase.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/inbound/ExecutionUseCase.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/inbound/SessionUseCase.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/ScenarioRepositoryPort.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/ExecutionRepositoryPort.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/FIXSessionPort.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/InboundMessageListener.java`
- Create: `fix-flow-core/src/main/java/com/fixflow/core/ports/outbound/EventPublisherPort.java`

**Steps:**

- [ ] **3.1 — `ScenarioUseCase.java`:**

```java
package com.fixflow.core.ports.inbound;

import com.fixflow.core.domain.scenario.Scenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioUseCase {
    Scenario save(Scenario scenario);
    Optional<Scenario> findById(UUID id);
    List<Scenario> findAll();
    void delete(UUID id);
    List<String> getVersions(UUID id);
}
```

- [ ] **3.2 — `ExecutionUseCase.java`:**

```java
package com.fixflow.core.ports.inbound;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.execution.FIXMessage;

import java.util.List;
import java.util.UUID;

public interface ExecutionUseCase {
    UUID start(UUID scenarioId, UUID sessionId);
    void stop(UUID executionId);
    ExecutionStatus getStatus(UUID executionId);
    List<ExecutionEvent> getEvents(UUID executionId);
    List<FIXMessage> getMessages(UUID executionId);
}
```

- [ ] **3.3 — `SessionUseCase.java`:**

```java
package com.fixflow.core.ports.inbound;

import com.fixflow.core.domain.session.FIXSessionConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionUseCase {
    FIXSessionConfig save(FIXSessionConfig config);
    Optional<FIXSessionConfig> findById(UUID id);
    List<FIXSessionConfig> findAll();
    void connect(UUID id);
    void disconnect(UUID id);
    boolean getStatus(UUID id);
}
```

- [ ] **3.4 — `ScenarioRepositoryPort.java`:**

```java
package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.scenario.Scenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepositoryPort {
    Scenario save(Scenario scenario);
    Optional<Scenario> findById(UUID id);
    List<Scenario> findAll();
    void delete(UUID id);
    void saveVersion(Scenario scenario);
    List<String> findVersions(UUID id);
}
```

- [ ] **3.5 — `ExecutionRepositoryPort.java`:**

```java
package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.Execution;
import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.FIXMessage;
import com.fixflow.core.domain.execution.NodeResult;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepositoryPort {
    Execution save(Execution execution);
    Optional<Execution> findById(UUID id);
    void addEvent(UUID executionId, ExecutionEvent event);
    void addMessage(UUID executionId, FIXMessage message);
    void addNodeResult(UUID executionId, NodeResult result);
}
```

- [ ] **3.6 — `InboundMessageListener.java`:**

```java
package com.fixflow.core.ports.outbound;

import java.util.Map;

@FunctionalInterface
public interface InboundMessageListener {
    void onMessage(String sessionId, Map<Integer, String> fields);
}
```

- [ ] **3.7 — `FIXSessionPort.java`:**

```java
package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.session.FIXSessionConfig;

import java.util.Map;
import java.util.UUID;

public interface FIXSessionPort {
    void connect(FIXSessionConfig config);
    void disconnect(UUID sessionId);
    void sendMessage(UUID sessionId, Map<Integer, String> fields);
    boolean isConnected(UUID sessionId);
    void setInboundListener(InboundMessageListener listener);
}
```

- [ ] **3.8 — `EventPublisherPort.java`:**

```java
package com.fixflow.core.ports.outbound;

import com.fixflow.core.domain.execution.ExecutionEvent;

public interface EventPublisherPort {
    void publish(ExecutionEvent event);
}
```

- [ ] **3.9 — Compile:**
  ```bash
  mvn compile -pl fix-flow-core
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **3.10 — Commit:**
  ```bash
  git add fix-flow-core/src/main/java/com/fixflow/core/ports
  git commit -m "core: inbound + outbound port interfaces (hexagonal boundaries)"
  ```

---

### Task 4: ScenarioDslParser — YAML/JSON ↔ Scenario

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/scenario/ScenarioDslParser.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioDslParserTest.java`

**Steps:**

- [ ] **4.1 — Write failing tests** at `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioDslParserTest.java`:

```java
package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDslParserTest {

    private static final String MINIMAL_YAML = """
            id: 11111111-1111-1111-1111-111111111111
            name: minimal-demo
            description: minimal smoke
            version: '1.0'
            sessionRef: sess-1
            runtimePolicy: PARALLEL
            nodes:
              - id: n1
                name: Start
                type: START
                onSuccess: n2
              - id: n2
                name: Send NewOrderSingle
                type: SEND_FIX
                config:
                  msgType: D
                  fields:
                    11: REQ-001
                    55: AAPL
                timeout:
                  value: 5
                  unit: SECONDS
                  onTimeout: FAIL
                onSuccess: n3
              - id: n3
                name: Done
                type: END_PASS
            edges:
              - from: n1
                to: n2
              - from: n2
                to: n3
            """;

    @Test
    void parsesMinimalYaml() {
        Scenario s = new ScenarioDslParser().parseYaml(MINIMAL_YAML);

        assertThat(s.name()).isEqualTo("minimal-demo");
        assertThat(s.nodes()).hasSize(3);
        assertThat(s.findNode("n2")).isPresent();
        assertThat(s.findNode("n2").orElseThrow().type()).isEqualTo(NodeType.SEND_FIX);
        assertThat(s.findNode("n2").orElseThrow().config()).containsKey("fields");
        assertThat(s.findNode("n2").orElseThrow().timeout().value()).isEqualTo(5);
        assertThat(s.findNode("n2").orElseThrow().timeout().unit()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void roundTripYamlSerialization() {
        ScenarioDslParser parser = new ScenarioDslParser();
        Scenario original = parser.parseYaml(MINIMAL_YAML);

        String yamlOut = parser.toYaml(original);
        Scenario reparsed = parser.parseYaml(yamlOut);

        assertThat(reparsed).isEqualTo(original);
    }
}
```

Run: `mvn test -pl fix-flow-engine -Dtest=ScenarioDslParserTest` — expect compile failure.

- [ ] **4.2 — Implement `ScenarioDslParser.java`:**

```java
package com.fixflow.engine.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fixflow.core.domain.scenario.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

public class ScenarioDslParser {

    private final ObjectMapper mapper;

    public ScenarioDslParser() {
        YAMLFactory yf = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.mapper = new ObjectMapper(yf)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Scenario parseYaml(String yaml) {
        try {
            ScenarioDto dto = mapper.readValue(yaml, ScenarioDto.class);
            return dto.toDomain();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse scenario YAML", e);
        }
    }

    public String toYaml(Scenario scenario) {
        try {
            return mapper.writeValueAsString(ScenarioDto.fromDomain(scenario));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize scenario YAML", e);
        }
    }

    // ---------- DTOs (pure JSON/YAML shape) ----------

    public record ScenarioDto(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("version") String version,
            @JsonProperty("sessionRef") String sessionRef,
            @JsonProperty("runtimePolicy") RuntimePolicy runtimePolicy,
            @JsonProperty("routingRules") List<RoutingRuleDto> routingRules,
            @JsonProperty("correlationRules") List<CorrelationRuleDto> correlationRules,
            @JsonProperty("nodes") List<NodeDto> nodes,
            @JsonProperty("edges") List<EdgeDto> edges,
            @JsonProperty("variables") Map<String, VariableDefDto> variables
    ) {
        Scenario toDomain() {
            return new Scenario(
                    id,
                    name,
                    description,
                    version,
                    sessionRef,
                    runtimePolicy == null ? RuntimePolicy.PARALLEL : runtimePolicy,
                    routingRules == null ? List.of()
                            : routingRules.stream().map(RoutingRuleDto::toDomain).toList(),
                    correlationRules == null ? List.of()
                            : correlationRules.stream().map(CorrelationRuleDto::toDomain).toList(),
                    nodes == null ? List.of()
                            : nodes.stream().map(NodeDto::toDomain).toList(),
                    edges == null ? List.of()
                            : edges.stream().map(EdgeDto::toDomain).toList(),
                    variables == null ? Map.of()
                            : variables.entrySet().stream().collect(Collectors.toMap(
                                    Map.Entry::getKey, e -> e.getValue().toDomain()))
            );
        }

        static ScenarioDto fromDomain(Scenario s) {
            return new ScenarioDto(
                    s.id(), s.name(), s.description(), s.version(), s.sessionRef(),
                    s.runtimePolicy(),
                    s.routingRules().stream().map(RoutingRuleDto::fromDomain).toList(),
                    s.correlationRules().stream().map(CorrelationRuleDto::fromDomain).toList(),
                    s.nodes().stream().map(NodeDto::fromDomain).toList(),
                    s.edges().stream().map(EdgeDto::fromDomain).toList(),
                    s.variables().entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, e -> VariableDefDto.fromDomain(e.getValue())))
            );
        }
    }

    public record NodeDto(
            String id,
            String name,
            NodeType type,
            Map<String, Object> config,
            TimeoutConfigDto timeout,
            RetryPolicyDto retryPolicy,
            String onSuccess,
            String onFailure,
            String onTimeout
    ) {
        ScenarioNode toDomain() {
            return new ScenarioNode(
                    id, name, type,
                    config == null ? Map.of() : config,
                    timeout == null ? null : timeout.toDomain(),
                    retryPolicy == null ? null : retryPolicy.toDomain(),
                    onSuccess, onFailure, onTimeout);
        }

        static NodeDto fromDomain(ScenarioNode n) {
            return new NodeDto(
                    n.id(), n.name(), n.type(),
                    n.config().isEmpty() ? null : n.config(),
                    n.timeout() == null ? null : TimeoutConfigDto.fromDomain(n.timeout()),
                    n.retryPolicy() == null ? null : RetryPolicyDto.fromDomain(n.retryPolicy()),
                    n.onSuccess(), n.onFailure(), n.onTimeout());
        }
    }

    public record EdgeDto(String from, String to, String label) {
        ScenarioEdge toDomain() { return new ScenarioEdge(from, to, label); }
        static EdgeDto fromDomain(ScenarioEdge e) { return new EdgeDto(e.from(), e.to(), e.label()); }
    }

    public record TimeoutConfigDto(long value, TimeUnit unit, TimeoutAction onTimeout, String jumpTo) {
        TimeoutConfig toDomain() { return new TimeoutConfig(value, unit, onTimeout, jumpTo); }
        static TimeoutConfigDto fromDomain(TimeoutConfig t) {
            return new TimeoutConfigDto(t.value(), t.unit(), t.onTimeout(), t.jumpTo());
        }
    }

    public record RetryPolicyDto(int maxAttempts, long delayMs) {
        RetryPolicy toDomain() { return new RetryPolicy(maxAttempts, delayMs); }
        static RetryPolicyDto fromDomain(RetryPolicy r) {
            return new RetryPolicyDto(r.maxAttempts(), r.delayMs());
        }
    }

    public record RoutingRuleDto(Map<String, String> criteria, String scenarioId, int priority) {
        RoutingRule toDomain() { return new RoutingRule(criteria, scenarioId, priority); }
        static RoutingRuleDto fromDomain(RoutingRule r) {
            return new RoutingRuleDto(r.criteria(), r.scenarioId(), r.priority());
        }
    }

    public record CorrelationRuleDto(int sourceTag, String targetNode, int targetTag, long timeWindowMs) {
        CorrelationRule toDomain() { return new CorrelationRule(sourceTag, targetNode, targetTag, timeWindowMs); }
        static CorrelationRuleDto fromDomain(CorrelationRule r) {
            return new CorrelationRuleDto(r.sourceTag(), r.targetNode(), r.targetTag(), r.timeWindowMs());
        }
    }

    public record VariableDefDto(String type, String defaultValue) {
        VariableDef toDomain() { return new VariableDef(type, defaultValue); }
        static VariableDefDto fromDomain(VariableDef v) { return new VariableDefDto(v.type(), v.defaultValue()); }
    }
}
```

- [ ] **4.3 — Run tests:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=ScenarioDslParserTest
  ```
  Expected: both tests pass (2/2).

- [ ] **4.4 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: scenario DSL parser (YAML <-> Scenario domain round-trip)"
  ```

---

## Phase 2: H2 Persistence + JPA + ScenarioRegistry

### Task 5: JPA entities + Spring Data repositories

**Files:**
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/ScenarioEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/ScenarioVersionEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/FIXSessionEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/ExecutionEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/ExecutionEventEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/FIXMessageEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/NodeResultEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/entity/ValidationErrorEntity.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaScenarioRepository.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaScenarioVersionRepository.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaFIXSessionRepository.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaExecutionRepository.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaExecutionEventRepository.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaFIXMessageRepository.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/jpa/JpaNodeResultRepository.java`
- Create: `fix-flow-api/src/main/resources/application.yml`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java`
- Create: `fix-flow-adapters/src/test/java/com/fixflow/adapters/persistence/ScenarioPersistenceTest.java`

**Steps:**

- [ ] **5.1 — `ScenarioEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenarios")
public class ScenarioEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String yamlDsl;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getYamlDsl() { return yamlDsl; }
    public void setYamlDsl(String yamlDsl) { this.yamlDsl = yamlDsl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **5.2 — `ScenarioVersionEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenario_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"scenarioId", "version"}))
public class ScenarioVersionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID scenarioId;

    @Column(nullable = false)
    private String version;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String yamlDsl;

    @Column(nullable = false)
    private Instant savedAt;

    @PrePersist
    void onInsert() { savedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getScenarioId() { return scenarioId; }
    public void setScenarioId(UUID scenarioId) { this.scenarioId = scenarioId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getYamlDsl() { return yamlDsl; }
    public void setYamlDsl(String yamlDsl) { this.yamlDsl = yamlDsl; }
    public Instant getSavedAt() { return savedAt; }
}
```

- [ ] **5.3 — `FIXSessionEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXVersion;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "fix_sessions")
public class FIXSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FIXMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FIXVersion fixVersion;

    private String defaultApplVerID;
    private String senderCompID;
    private String targetCompID;
    private String host;
    private int port;
    private int heartbeatInterval;
    private int reconnectInterval;
    private boolean resetOnLogon;
    private boolean resetOnLogout;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FIXMode getMode() { return mode; }
    public void setMode(FIXMode mode) { this.mode = mode; }
    public FIXVersion getFixVersion() { return fixVersion; }
    public void setFixVersion(FIXVersion fixVersion) { this.fixVersion = fixVersion; }
    public String getDefaultApplVerID() { return defaultApplVerID; }
    public void setDefaultApplVerID(String defaultApplVerID) { this.defaultApplVerID = defaultApplVerID; }
    public String getSenderCompID() { return senderCompID; }
    public void setSenderCompID(String s) { this.senderCompID = s; }
    public String getTargetCompID() { return targetCompID; }
    public void setTargetCompID(String t) { this.targetCompID = t; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(int h) { this.heartbeatInterval = h; }
    public int getReconnectInterval() { return reconnectInterval; }
    public void setReconnectInterval(int r) { this.reconnectInterval = r; }
    public boolean isResetOnLogon() { return resetOnLogon; }
    public void setResetOnLogon(boolean r) { this.resetOnLogon = r; }
    public boolean isResetOnLogout() { return resetOnLogout; }
    public void setResetOnLogout(boolean r) { this.resetOnLogout = r; }
}
```

- [ ] **5.4 — `ExecutionEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.execution.ExecutionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class ExecutionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID scenarioId;

    private String scenarioVersion;

    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    private Instant startTime;
    private Instant endTime;
    private String currentNodeId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String variablesJson;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getScenarioId() { return scenarioId; }
    public void setScenarioId(UUID s) { this.scenarioId = s; }
    public String getScenarioVersion() { return scenarioVersion; }
    public void setScenarioVersion(String v) { this.scenarioVersion = v; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID s) { this.sessionId = s; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus s) { this.status = s; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant t) { this.startTime = t; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant t) { this.endTime = t; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String n) { this.currentNodeId = n; }
    public String getVariablesJson() { return variablesJson; }
    public void setVariablesJson(String v) { this.variablesJson = v; }
}
```

- [ ] **5.5 — `ExecutionEventEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.execution.ExecutionEventType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_events", indexes = @Index(columnList = "executionId"))
public class ExecutionEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionEventType type;

    private String nodeId;

    @Column(nullable = false)
    private Instant timestamp;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String detail;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String rawFix;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public ExecutionEventType getType() { return type; }
    public void setType(ExecutionEventType t) { this.type = t; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String n) { this.nodeId = n; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant t) { this.timestamp = t; }
    public String getDetail() { return detail; }
    public void setDetail(String d) { this.detail = d; }
    public String getRawFix() { return rawFix; }
    public void setRawFix(String r) { this.rawFix = r; }
}
```

- [ ] **5.6 — `FIXMessageEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import com.fixflow.core.domain.execution.Direction;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fix_messages", indexes = @Index(columnList = "executionId"))
public class FIXMessageEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String rawFix;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String fieldsJson;

    @Column(nullable = false)
    private Instant receivedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction d) { this.direction = d; }
    public String getRawFix() { return rawFix; }
    public void setRawFix(String r) { this.rawFix = r; }
    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String f) { this.fieldsJson = f; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant r) { this.receivedAt = r; }
}
```

- [ ] **5.7 — `NodeResultEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_results", indexes = @Index(columnList = "executionId"))
public class NodeResultEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String status;

    private Instant startTime;
    private Instant endTime;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String error;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String n) { this.nodeId = n; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant t) { this.startTime = t; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant t) { this.endTime = t; }
    public String getError() { return error; }
    public void setError(String e) { this.error = e; }
}
```

- [ ] **5.8 — `ValidationErrorEntity.java`:**

```java
package com.fixflow.adapters.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_errors", indexes = @Index(columnList = "executionId"))
public class ValidationErrorEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID executionId;

    private String nodeId;
    private String message;
    private Instant occurredAt;

    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID e) { this.executionId = e; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String n) { this.nodeId = n; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant o) { this.occurredAt = o; }
}
```

- [ ] **5.9 — Spring Data repositories**

`JpaScenarioRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaScenarioRepository extends JpaRepository<ScenarioEntity, UUID> {
    Optional<ScenarioEntity> findByName(String name);
}
```

`JpaScenarioVersionRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ScenarioVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaScenarioVersionRepository extends JpaRepository<ScenarioVersionEntity, UUID> {
    List<ScenarioVersionEntity> findByScenarioIdOrderBySavedAtDesc(UUID scenarioId);
}
```

`JpaFIXSessionRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.FIXSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaFIXSessionRepository extends JpaRepository<FIXSessionEntity, UUID> { }
```

`JpaExecutionRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaExecutionRepository extends JpaRepository<ExecutionEntity, UUID> { }
```

`JpaExecutionEventRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.ExecutionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaExecutionEventRepository extends JpaRepository<ExecutionEventEntity, UUID> {
    List<ExecutionEventEntity> findByExecutionIdOrderByTimestampAsc(UUID executionId);
}
```

`JpaFIXMessageRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.FIXMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaFIXMessageRepository extends JpaRepository<FIXMessageEntity, UUID> {
    List<FIXMessageEntity> findByExecutionIdOrderByReceivedAtAsc(UUID executionId);
}
```

`JpaNodeResultRepository.java`:
```java
package com.fixflow.adapters.persistence.jpa;

import com.fixflow.adapters.persistence.entity.NodeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaNodeResultRepository extends JpaRepository<NodeResultEntity, UUID> {
    List<NodeResultEntity> findByExecutionIdOrderByStartTimeAsc(UUID executionId);
}
```

- [ ] **5.10 — `application.yml`** at `fix-flow-api/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/fixflow;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: false
  h2:
    console:
      enabled: true
      path: /h2-console
server:
  port: 8080
```

- [ ] **5.11 — `FixFlowApplication.java`** at `fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java`:

```java
package com.fixflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.fixflow")
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
public class FixFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixFlowApplication.class, args);
    }
}
```

- [ ] **5.12 — Integration test** at `fix-flow-adapters/src/test/java/com/fixflow/adapters/persistence/ScenarioPersistenceTest.java`:

```java
package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@EnableAutoConfiguration
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
class ScenarioPersistenceTest {

    @Configuration
    static class TestConfig {}

    @Autowired
    JpaScenarioRepository repo;

    @Test
    void savesAndRetrievesScenarioEntity() {
        ScenarioEntity e = new ScenarioEntity();
        e.setId(UUID.randomUUID());
        e.setName("demo");
        e.setVersion("1.0");
        e.setYamlDsl("name: demo\n");
        repo.save(e);

        var loaded = repo.findById(e.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("demo");
        assertThat(loaded.getYamlDsl()).contains("demo");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }
}
```

- [ ] **5.13 — Run:**
  ```bash
  mvn test -pl fix-flow-adapters -Dtest=ScenarioPersistenceTest
  ```
  Expected: 1/1 passes (H2 in-memory by default for `@DataJpaTest`).

- [ ] **5.14 — Commit:**
  ```bash
  git add fix-flow-adapters fix-flow-api
  git commit -m "adapters: JPA entities + Spring Data repos + H2 file-mode app config"
  ```

---

### Task 6: ScenarioRepositoryAdapter + ExecutionRepositoryAdapter

**Files:**
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/ScenarioRepositoryAdapter.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/ExecutionRepositoryAdapter.java`
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/persistence/FIXSessionRepositoryAdapter.java`
- Create: `fix-flow-adapters/src/test/java/com/fixflow/adapters/persistence/ScenarioRepositoryAdapterTest.java`

**Steps:**

- [ ] **6.1 — Failing test** `ScenarioRepositoryAdapterTest.java`:

```java
package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.adapters.persistence.jpa.JpaScenarioVersionRepository;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableAutoConfiguration
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
class ScenarioRepositoryAdapterTest {

    @Configuration
    static class TestConfig {
        @Bean ScenarioDslParser parser() { return new ScenarioDslParser(); }
        @Bean ScenarioRepositoryAdapter adapter(JpaScenarioRepository r,
                                                JpaScenarioVersionRepository v,
                                                ScenarioDslParser p) {
            return new ScenarioRepositoryAdapter(r, v, p);
        }
    }

    @Autowired ScenarioRepositoryAdapter adapter;

    @Test
    void savesAndRetrievesScenarioDomainObject() {
        UUID id = UUID.randomUUID();
        Scenario s = new Scenario(id, "demo", "desc", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(new ScenarioNode("n1", "Start", NodeType.START, Map.of(), null, null, null, null, null)),
                List.of(), Map.of());

        adapter.save(s);
        Scenario loaded = adapter.findById(id).orElseThrow();

        assertThat(loaded.name()).isEqualTo("demo");
        assertThat(loaded.nodes()).hasSize(1);
        assertThat(loaded.nodes().get(0).type()).isEqualTo(NodeType.START);
    }
}
```

- [ ] **6.2 — `ScenarioRepositoryAdapter.java`:**

```java
package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.ScenarioEntity;
import com.fixflow.adapters.persistence.entity.ScenarioVersionEntity;
import com.fixflow.adapters.persistence.jpa.JpaScenarioRepository;
import com.fixflow.adapters.persistence.jpa.JpaScenarioVersionRepository;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ScenarioRepositoryAdapter implements ScenarioRepositoryPort {

    private final JpaScenarioRepository scenarioRepo;
    private final JpaScenarioVersionRepository versionRepo;
    private final ScenarioDslParser parser;

    public ScenarioRepositoryAdapter(JpaScenarioRepository scenarioRepo,
                                     JpaScenarioVersionRepository versionRepo,
                                     ScenarioDslParser parser) {
        this.scenarioRepo = scenarioRepo;
        this.versionRepo = versionRepo;
        this.parser = parser;
    }

    @Override
    @Transactional
    public Scenario save(Scenario scenario) {
        ScenarioEntity e = scenarioRepo.findById(scenario.id()).orElseGet(ScenarioEntity::new);
        e.setId(scenario.id());
        e.setName(scenario.name());
        e.setVersion(scenario.version());
        e.setYamlDsl(parser.toYaml(scenario));
        scenarioRepo.save(e);
        return scenario;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Scenario> findById(UUID id) {
        return scenarioRepo.findById(id).map(e -> parser.parseYaml(e.getYamlDsl()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scenario> findAll() {
        return scenarioRepo.findAll().stream()
                .map(e -> parser.parseYaml(e.getYamlDsl()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) { scenarioRepo.deleteById(id); }

    @Override
    @Transactional
    public void saveVersion(Scenario scenario) {
        ScenarioVersionEntity v = new ScenarioVersionEntity();
        v.setScenarioId(scenario.id());
        v.setVersion(scenario.version());
        v.setYamlDsl(parser.toYaml(scenario));
        versionRepo.save(v);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findVersions(UUID id) {
        return versionRepo.findByScenarioIdOrderBySavedAtDesc(id).stream()
                .map(ScenarioVersionEntity::getVersion)
                .toList();
    }
}
```

- [ ] **6.3 — `ExecutionRepositoryAdapter.java`:**

```java
package com.fixflow.adapters.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.persistence.entity.*;
import com.fixflow.adapters.persistence.jpa.*;
import com.fixflow.core.domain.execution.*;
import com.fixflow.core.ports.outbound.ExecutionRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ExecutionRepositoryAdapter implements ExecutionRepositoryPort {

    private final JpaExecutionRepository executionRepo;
    private final JpaExecutionEventRepository eventRepo;
    private final JpaFIXMessageRepository messageRepo;
    private final JpaNodeResultRepository nodeResultRepo;
    private final ObjectMapper json = new ObjectMapper();

    public ExecutionRepositoryAdapter(JpaExecutionRepository executionRepo,
                                      JpaExecutionEventRepository eventRepo,
                                      JpaFIXMessageRepository messageRepo,
                                      JpaNodeResultRepository nodeResultRepo) {
        this.executionRepo = executionRepo;
        this.eventRepo = eventRepo;
        this.messageRepo = messageRepo;
        this.nodeResultRepo = nodeResultRepo;
    }

    @Override
    @Transactional
    public Execution save(Execution execution) {
        ExecutionEntity e = executionRepo.findById(execution.id()).orElseGet(ExecutionEntity::new);
        e.setId(execution.id());
        e.setScenarioId(execution.scenarioId());
        e.setScenarioVersion(execution.scenarioVersion());
        e.setSessionId(execution.sessionId());
        e.setStatus(execution.status());
        e.setStartTime(execution.startTime());
        e.setEndTime(execution.endTime());
        e.setCurrentNodeId(execution.currentNodeId());
        e.setVariablesJson(writeJson(execution.variables()));
        executionRepo.save(e);
        return execution;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Execution> findById(UUID id) {
        return executionRepo.findById(id).map(e -> new Execution(
                e.getId(), e.getScenarioId(), e.getScenarioVersion(), e.getSessionId(),
                e.getStatus(), e.getStartTime(), e.getEndTime(), e.getCurrentNodeId(),
                readJsonMap(e.getVariablesJson()),
                java.util.List.of(), java.util.List.of()
        ));
    }

    @Override
    @Transactional
    public void addEvent(UUID executionId, ExecutionEvent event) {
        ExecutionEventEntity e = new ExecutionEventEntity();
        e.setId(event.id() == null ? UUID.randomUUID() : event.id());
        e.setExecutionId(executionId);
        e.setType(event.type());
        e.setNodeId(event.nodeId());
        e.setTimestamp(event.timestamp());
        e.setDetail(event.detail());
        e.setRawFix(event.rawFix());
        eventRepo.save(e);
    }

    @Override
    @Transactional
    public void addMessage(UUID executionId, FIXMessage message) {
        FIXMessageEntity e = new FIXMessageEntity();
        e.setId(message.id() == null ? UUID.randomUUID() : message.id());
        e.setExecutionId(executionId);
        e.setDirection(message.direction());
        e.setRawFix(message.rawFix());
        e.setFieldsJson(writeJson(message.fields()));
        e.setReceivedAt(message.receivedAt());
        messageRepo.save(e);
    }

    @Override
    @Transactional
    public void addNodeResult(UUID executionId, NodeResult result) {
        NodeResultEntity e = new NodeResultEntity();
        e.setId(result.id() == null ? UUID.randomUUID() : result.id());
        e.setExecutionId(executionId);
        e.setNodeId(result.nodeId());
        e.setStatus(result.status());
        e.setStartTime(result.startTime());
        e.setEndTime(result.endTime());
        e.setError(result.error());
        nodeResultRepo.save(e);
    }

    private String writeJson(Object obj) {
        try { return json.writeValueAsString(obj == null ? Map.of() : obj); }
        catch (JsonProcessingException ex) { throw new UncheckedIOException(ex); }
    }

    private Map<String, String> readJsonMap(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try { return json.readValue(s, new TypeReference<Map<String, String>>() {}); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
```

- [ ] **6.4 — `FIXSessionRepositoryAdapter.java`:**

```java
package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.FIXSessionEntity;
import com.fixflow.adapters.persistence.jpa.JpaFIXSessionRepository;
import com.fixflow.core.domain.session.FIXSessionConfig;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FIXSessionRepositoryAdapter {

    private final JpaFIXSessionRepository repo;

    public FIXSessionRepositoryAdapter(JpaFIXSessionRepository repo) { this.repo = repo; }

    @Transactional
    public FIXSessionConfig save(FIXSessionConfig cfg) {
        FIXSessionEntity e = repo.findById(cfg.id()).orElseGet(FIXSessionEntity::new);
        e.setId(cfg.id());
        e.setName(cfg.name());
        e.setMode(cfg.mode());
        e.setFixVersion(cfg.fixVersion());
        e.setDefaultApplVerID(cfg.defaultApplVerID());
        e.setSenderCompID(cfg.senderCompID());
        e.setTargetCompID(cfg.targetCompID());
        e.setHost(cfg.host());
        e.setPort(cfg.port());
        e.setHeartbeatInterval(cfg.heartbeatInterval());
        e.setReconnectInterval(cfg.reconnectInterval());
        e.setResetOnLogon(cfg.isResetOnLogon());
        e.setResetOnLogout(cfg.isResetOnLogout());
        repo.save(e);
        return cfg;
    }

    @Transactional(readOnly = true)
    public Optional<FIXSessionConfig> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Transactional(readOnly = true)
    public List<FIXSessionConfig> findAll() {
        return repo.findAll().stream().map(this::toDomain).toList();
    }

    private FIXSessionConfig toDomain(FIXSessionEntity e) {
        return new FIXSessionConfig(
                e.getId(), e.getName(), e.getMode(), e.getFixVersion(),
                e.getDefaultApplVerID(), e.getSenderCompID(), e.getTargetCompID(),
                e.getHost(), e.getPort(), e.getHeartbeatInterval(),
                e.getReconnectInterval(), e.isResetOnLogon(), e.isResetOnLogout());
    }
}
```

- [ ] **6.5 — Run:**
  ```bash
  mvn test -pl fix-flow-adapters -Dtest=ScenarioRepositoryAdapterTest
  ```
  Expected: PASS.

- [ ] **6.6 — Commit:**
  ```bash
  git add fix-flow-adapters
  git commit -m "adapters: ScenarioRepositoryAdapter + ExecutionRepositoryAdapter + FIXSessionRepositoryAdapter"
  ```

---

### Task 7: ScenarioRegistry (hot-swappable in-memory store)

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/scenario/ScenarioRegistry.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioRegistryTest.java`

**Steps:**

- [ ] **7.1 — Failing test:**

```java
package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioRegistryTest {

    private Scenario scenario(UUID id, String version) {
        return new Scenario(id, "demo", "", version, "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(new ScenarioNode("n1", "s", NodeType.START, Map.of(), null, null, null, null, null)),
                List.of(), Map.of());
    }

    @Test
    void registerAndRetrieve() {
        ScenarioRegistry reg = new ScenarioRegistry();
        UUID id = UUID.randomUUID();
        Scenario s1 = scenario(id, "1.0");

        reg.register(s1);

        assertThat(reg.getById(id)).contains(s1);
        assertThat(reg.findAll()).containsExactly(s1);
    }

    @Test
    void reloadKeepsHistoricVersionsAccessible() {
        ScenarioRegistry reg = new ScenarioRegistry();
        UUID id = UUID.randomUUID();
        Scenario v1 = scenario(id, "1.0");
        Scenario v2 = scenario(id, "2.0");

        reg.register(v1);
        reg.reload(v2);

        assertThat(reg.getById(id)).contains(v2);
        assertThat(reg.getVersion(id, "1.0")).contains(v1);
        assertThat(reg.getVersion(id, "2.0")).contains(v2);
    }

    @Test
    void unknownIdReturnsEmpty() {
        ScenarioRegistry reg = new ScenarioRegistry();
        assertThat(reg.getById(UUID.randomUUID())).isEmpty();
    }
}
```

- [ ] **7.2 — Implementation:**

```java
package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.Scenario;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScenarioRegistry {

    private final ConcurrentHashMap<UUID, Scenario> current = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<String, Scenario>> versions = new ConcurrentHashMap<>();

    public void register(Scenario scenario) {
        current.put(scenario.id(), scenario);
        versions.computeIfAbsent(scenario.id(), k -> new ConcurrentHashMap<>())
                .put(scenario.version(), scenario);
    }

    public void reload(Scenario newVersion) {
        register(newVersion);
    }

    public Optional<Scenario> getById(UUID id) {
        return Optional.ofNullable(current.get(id));
    }

    public Optional<Scenario> getVersion(UUID id, String version) {
        Map<String, Scenario> byVersion = versions.get(id);
        return byVersion == null ? Optional.empty() : Optional.ofNullable(byVersion.get(version));
    }

    public List<Scenario> findAll() {
        return List.copyOf(current.values());
    }

    public void unregister(UUID id) {
        current.remove(id);
        versions.remove(id);
    }
}
```

- [ ] **7.3 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=ScenarioRegistryTest
  ```
  Expected: 3/3 pass.

- [ ] **7.4 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: ScenarioRegistry with hot-reload + version history"
  ```

---

## Phase 3: QuickFIX/J Adapter + FIXSessionManager

### Task 8: QuickFIX/J application adapter

**Files:**
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/quickfixj/QuickFIXApplicationAdapter.java`
- Create: `fix-flow-adapters/src/test/java/com/fixflow/adapters/quickfixj/QuickFIXApplicationAdapterTest.java`

**Note:** `InboundMessageListener` already lives in `com.fixflow.core.ports.outbound` (Task 3). We reuse it; no duplicate.

**Steps:**

- [ ] **8.1 — Failing test:**

```java
package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QuickFIXApplicationAdapterTest {

    @Test
    void fromAppParsesFieldsAndDelegatesToListener() throws Exception {
        AtomicReference<String> capturedSession = new AtomicReference<>();
        AtomicReference<Map<Integer, String>> capturedFields = new AtomicReference<>();

        InboundMessageListener listener = (sid, fields) -> {
            capturedSession.set(sid);
            capturedFields.set(fields);
        };
        EventPublisherPort publisher = ev -> { /* no-op */ };

        QuickFIXApplicationAdapter adapter = new QuickFIXApplicationAdapter(listener, publisher);

        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, "D");
        msg.setString(11, "CL-1");
        msg.setString(55, "AAPL");

        SessionID sid = new SessionID("FIX.4.4", "SENDER", "TARGET");
        adapter.fromApp(msg, sid);

        assertThat(capturedSession.get()).isEqualTo(sid.toString());
        assertThat(capturedFields.get()).containsEntry(11, "CL-1").containsEntry(55, "AAPL");
        assertThat(capturedFields.get()).containsEntry(MsgType.FIELD, "D");
    }

    @Test
    void onLogonEmitsSessionUpEvent() {
        AtomicReference<ExecutionEvent> captured = new AtomicReference<>();
        EventPublisherPort publisher = captured::set;
        InboundMessageListener noop = (s, f) -> {};

        QuickFIXApplicationAdapter adapter = new QuickFIXApplicationAdapter(noop, publisher);
        SessionID sid = new SessionID("FIX.4.4", "SENDER", "TARGET");
        adapter.onLogon(sid);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().detail()).contains(sid.toString());
    }
}
```

- [ ] **8.2 — Implementation `QuickFIXApplicationAdapter.java`:**

```java
package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.ExecutionEvent;
import com.fixflow.core.domain.execution.ExecutionEventType;
import com.fixflow.core.ports.outbound.EventPublisherPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import quickfix.*;
import quickfix.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class QuickFIXApplicationAdapter implements Application {

    private final InboundMessageListener listener;
    private final EventPublisherPort publisher;

    public QuickFIXApplicationAdapter(InboundMessageListener listener, EventPublisherPort publisher) {
        this.listener = listener;
        this.publisher = publisher;
    }

    @Override
    public void onCreate(SessionID sessionId) { /* no-op */ }

    @Override
    public void onLogon(SessionID sessionId) {
        publisher.publish(new ExecutionEvent(
                UUID.randomUUID(), null, ExecutionEventType.SESSION_UP, null,
                Instant.now(), "Session up: " + sessionId, null));
    }

    @Override
    public void onLogout(SessionID sessionId) {
        publisher.publish(new ExecutionEvent(
                UUID.randomUUID(), null, ExecutionEventType.SESSION_DOWN, null,
                Instant.now(), "Session down: " + sessionId, null));
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void toApp(Message message, SessionID sessionId) { /* no-op */ }

    @Override
    public void fromApp(Message message, SessionID sessionId) {
        Map<Integer, String> fields = extractFields(message);
        listener.onMessage(sessionId.toString(), fields);
    }

    private Map<Integer, String> extractFields(Message message) {
        Map<Integer, String> fields = new HashMap<>();
        copyFields(message.getHeader().iterator(), fields);
        copyFields(message.iterator(), fields);
        copyFields(message.getTrailer().iterator(), fields);
        return fields;
    }

    private void copyFields(Iterator<Field<?>> it, Map<Integer, String> out) {
        while (it.hasNext()) {
            Field<?> f = it.next();
            out.put(f.getTag(), String.valueOf(f.getObject()));
        }
    }
}
```

- [ ] **8.3 — Run:**
  ```bash
  mvn test -pl fix-flow-adapters -Dtest=QuickFIXApplicationAdapterTest
  ```
  Expected: 2/2 pass.

- [ ] **8.4 — Commit:**
  ```bash
  git add fix-flow-adapters
  git commit -m "adapters: QuickFIXApplicationAdapter (fromApp + lifecycle events)"
  ```

---

### Task 9: FIXSessionManager — connect, send, disconnect

**Files:**
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/quickfixj/QuickFIXAdapter.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/FIXSessionManager.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/FIXSessionManagerTest.java`

**Steps:**

- [ ] **9.1 — Failing test** (uses `FakeFixAdapter` from Task 10; declare a minimal fake inline here, then refactor in Task 10):

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FIXSessionManagerTest {

    @Test
    void connectSendDisconnectLifecycle() {
        FakeFixAdapter fake = new FakeFixAdapter();
        FIXSessionManager mgr = new FIXSessionManager(fake);

        FIXSessionConfig cfg = new FIXSessionConfig(
                UUID.randomUUID(), "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "SENDER", "TARGET", "localhost", 9876, 30, 5, true, false);

        mgr.connect(cfg);
        assertThat(mgr.isConnected(cfg.id())).isTrue();

        mgr.send(cfg.id(), Map.of(35, "D", 11, "CL-1"));
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0)).containsEntry(35, "D");

        mgr.disconnect(cfg.id());
        assertThat(mgr.isConnected(cfg.id())).isFalse();
    }
}
```

- [ ] **9.2 — Implement `QuickFIXAdapter.java`:**

```java
package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuickFIXAdapter implements FIXSessionPort {

    private final QuickFIXApplicationAdapter application;
    private final Map<UUID, Connector> connectors = new ConcurrentHashMap<>();
    private final Map<UUID, SessionID> sessions = new ConcurrentHashMap<>();
    private volatile InboundMessageListener listener;

    public QuickFIXAdapter(QuickFIXApplicationAdapter application) {
        this.application = application;
    }

    @Override
    public void setInboundListener(InboundMessageListener listener) {
        this.listener = listener;
    }

    @Override
    public void connect(FIXSessionConfig config) {
        try {
            SessionSettings settings = buildSettings(config);
            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            LogFactory logFactory = new ScreenLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();

            Connector connector = (config.mode() == FIXMode.INITIATOR)
                    ? new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory)
                    : new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
            connector.start();
            connectors.put(config.id(), connector);

            SessionID sid = sessionIdFromConfig(config);
            sessions.put(config.id(), sid);
        } catch (ConfigError e) {
            throw new IllegalStateException("Failed to start FIX session " + config.id(), e);
        }
    }

    @Override
    public void disconnect(UUID sessionId) {
        Connector c = connectors.remove(sessionId);
        if (c != null) c.stop(true);
        sessions.remove(sessionId);
    }

    @Override
    public boolean isConnected(UUID sessionId) {
        SessionID sid = sessions.get(sessionId);
        if (sid == null) return false;
        Session s = Session.lookupSession(sid);
        return s != null && s.isLoggedOn();
    }

    @Override
    public void sendMessage(UUID sessionId, Map<Integer, String> fields) {
        SessionID sid = sessions.get(sessionId);
        if (sid == null) throw new IllegalStateException("Unknown session: " + sessionId);
        Message msg = new Message();
        fields.forEach((tag, value) -> {
            if (tag == 35) msg.getHeader().setString(35, value);
            else msg.setString(tag, value);
        });
        Session.sendToTarget(msg, sid);
    }

    private SessionSettings buildSettings(FIXSessionConfig cfg) {
        Properties defaults = new Properties();
        defaults.setProperty("ConnectionType", cfg.mode() == FIXMode.INITIATOR ? "initiator" : "acceptor");
        defaults.setProperty("HeartBtInt", String.valueOf(cfg.heartbeatInterval()));
        defaults.setProperty("ReconnectInterval", String.valueOf(cfg.reconnectInterval()));
        defaults.setProperty("StartTime", "00:00:00");
        defaults.setProperty("EndTime", "00:00:00");
        defaults.setProperty("ResetOnLogon", String.valueOf(cfg.resetOnLogon()));
        defaults.setProperty("ResetOnLogout", String.valueOf(cfg.resetOnLogout()));
        defaults.setProperty("FileStorePath", "./data/fix-store");
        if (cfg.mode() == FIXMode.INITIATOR) {
            defaults.setProperty("SocketConnectHost", cfg.host());
            defaults.setProperty("SocketConnectPort", String.valueOf(cfg.port()));
        } else {
            defaults.setProperty("SocketAcceptPort", String.valueOf(cfg.port()));
        }

        switch (cfg.fixVersion()) {
            case FIX_42 -> {
                defaults.setProperty("BeginString", "FIX.4.2");
                defaults.setProperty("DataDictionary", "FIX42.xml");
            }
            case FIX_44 -> {
                defaults.setProperty("BeginString", "FIX.4.4");
                defaults.setProperty("DataDictionary", "FIX44.xml");
            }
            case FIXT_11 -> {
                defaults.setProperty("BeginString", "FIXT.1.1");
                defaults.setProperty("DefaultApplVerID",
                        cfg.defaultApplVerID() == null ? "9" : cfg.defaultApplVerID());
                defaults.setProperty("AppDataDictionary", "FIX50SP2.xml");
                defaults.setProperty("TransportDataDictionary", "FIXT11.xml");
            }
        }

        SessionSettings settings = new SessionSettings();
        defaults.forEach((k, v) -> settings.setString(String.valueOf(k), String.valueOf(v)));

        SessionID sid = sessionIdFromConfig(cfg);
        Properties sessionProps = new Properties();
        sessionProps.setProperty("SenderCompID", cfg.senderCompID());
        sessionProps.setProperty("TargetCompID", cfg.targetCompID());
        sessionProps.setProperty("BeginString", defaults.getProperty("BeginString"));
        sessionProps.forEach((k, v) -> settings.setString(sid, String.valueOf(k), String.valueOf(v)));
        return settings;
    }

    private SessionID sessionIdFromConfig(FIXSessionConfig cfg) {
        String beginString = switch (cfg.fixVersion()) {
            case FIX_42 -> "FIX.4.2";
            case FIX_44 -> "FIX.4.4";
            case FIXT_11 -> "FIXT.1.1";
        };
        return new SessionID(beginString, cfg.senderCompID(), cfg.targetCompID());
    }
}
```

- [ ] **9.3 — Implement `FIXSessionManager.java`:**

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FIXSessionManager {

    private final FIXSessionPort port;
    private final Map<UUID, FIXSessionConfig> known = new ConcurrentHashMap<>();

    public FIXSessionManager(FIXSessionPort port) { this.port = port; }

    public void registerListener(InboundMessageListener listener) {
        port.setInboundListener(listener);
    }

    public void connect(FIXSessionConfig cfg) {
        known.put(cfg.id(), cfg);
        port.connect(cfg);
    }

    public void disconnect(UUID id) {
        port.disconnect(id);
    }

    public boolean isConnected(UUID id) { return port.isConnected(id); }

    public void send(UUID id, Map<Integer, String> fields) {
        if (!known.containsKey(id)) throw new IllegalStateException("Session not registered: " + id);
        port.sendMessage(id, fields);
    }
}
```

- [ ] **9.4 — Run** (FakeFixAdapter implemented in Task 10 satisfies the test — order Tasks 9→10 means first commit may need Task 10's fake. Implement `FakeFixAdapter.java` from Task 10 step 10.2 BEFORE running Task 9 tests):
  ```bash
  mvn test -pl fix-flow-engine -Dtest=FIXSessionManagerTest
  ```
  Expected: PASS once both FakeFixAdapter (Task 10) and FIXSessionManager exist.

- [ ] **9.5 — Commit:**
  ```bash
  git add fix-flow-engine fix-flow-adapters
  git commit -m "engine+adapters: FIXSessionManager + QuickFIXAdapter with SessionSettings per FIX version"
  ```

---

### Task 10: FakeFixAdapter for tests

**Files:**
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/FakeFixAdapter.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/FakeFixAdapterTest.java`

**Steps:**

- [ ] **10.1 — Failing test:**

```java
package com.fixflow.engine.fix;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FakeFixAdapterTest {

    @Test
    void capturesSentMessagesAndInjectsInbound() {
        FakeFixAdapter fake = new FakeFixAdapter();
        UUID sid = UUID.randomUUID();

        AtomicReference<Map<Integer, String>> received = new AtomicReference<>();
        fake.setInboundListener((s, f) -> received.set(f));

        fake.sendMessage(sid, Map.of(35, "D", 11, "CL-1"));
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0)).containsEntry(11, "CL-1");

        Map<Integer, String> inbound = new HashMap<>();
        inbound.put(35, "8");
        inbound.put(11, "CL-1");
        fake.injectInbound(sid, inbound);

        assertThat(received.get()).containsEntry(35, "8");
    }
}
```

- [ ] **10.2 — Implement `FakeFixAdapter.java`:**

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeFixAdapter implements FIXSessionPort {

    private final Map<UUID, Boolean> connected = new ConcurrentHashMap<>();
    private final List<Map<Integer, String>> sentMessages = new CopyOnWriteArrayList<>();
    private volatile InboundMessageListener listener;

    @Override
    public void connect(FIXSessionConfig config) {
        connected.put(config.id(), true);
    }

    @Override
    public void disconnect(UUID sessionId) {
        connected.put(sessionId, false);
    }

    @Override
    public boolean isConnected(UUID sessionId) {
        return connected.getOrDefault(sessionId, false);
    }

    @Override
    public void sendMessage(UUID sessionId, Map<Integer, String> fields) {
        sentMessages.add(new HashMap<>(fields));
    }

    @Override
    public void setInboundListener(InboundMessageListener l) { this.listener = l; }

    // Test helpers
    public void injectInbound(UUID sessionId, Map<Integer, String> fields) {
        InboundMessageListener l = listener;
        if (l != null) l.onMessage(sessionId.toString(), fields);
    }

    public List<Map<Integer, String>> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    public void reset() {
        sentMessages.clear();
        connected.clear();
    }
}
```

- [ ] **10.3 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=FakeFixAdapterTest
  ```
  Expected: PASS.

- [ ] **10.4 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine(test): FakeFixAdapter test double for FIXSessionPort"
  ```

---

## Phase 4: ExecutionManager + Basic NodeHandlers

### Task 11: ExecutionContext + ExecutionManager skeleton

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionContext.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/execution/ExecutionManager.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/execution/ExecutionManagerTest.java`

**Note:** Task 11 depends on `NodeDispatcher`/handlers from Task 12. Order of work: stub the dispatcher with two registered handlers (SEND_FIX + END_PASS/END_FAIL) in Task 12, then this test compiles. The commit order in this plan keeps Task 11 first only for the file scaffolding (manager + context); the end-to-end assertion is verified after Task 12.

**Steps:**

- [ ] **11.1 — Implement `ExecutionContext.java`:**

```java
package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext {

    private final UUID executionId;
    private final Scenario scenario;
    private final UUID sessionId;
    private volatile ExecutionStatus status = ExecutionStatus.RUNNING;
    private volatile String currentNodeId;
    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, String>> nodeMessages = new ConcurrentHashMap<>();

    public ExecutionContext(UUID executionId, Scenario scenario, UUID sessionId) {
        this.executionId = executionId;
        this.scenario = scenario;
        this.sessionId = sessionId;
    }

    public UUID executionId() { return executionId; }
    public Scenario scenario() { return scenario; }
    public UUID sessionId() { return sessionId; }
    public ExecutionStatus status() { return status; }
    public void setStatus(ExecutionStatus s) { this.status = s; }
    public String currentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String id) { this.currentNodeId = id; }
    public Map<String, String> variables() { return variables; }
    public void setVariable(String k, String v) { variables.put(k, v); }
    public String getVariable(String k) { return variables.get(k); }

    public void storeNodeMessage(String nodeId, Map<Integer, String> fields) {
        nodeMessages.put(nodeId, Map.copyOf(fields));
    }

    public Map<Integer, String> getNodeMessage(String nodeId) {
        return nodeMessages.get(nodeId);
    }
}
```

- [ ] **11.2 — Implement `ExecutionManager.java`:**

```java
package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExecutionManager {

    private final ScenarioRegistry registry;
    private final NodeDispatcher dispatcher;
    private final Map<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionManager(ScenarioRegistry registry, NodeDispatcher dispatcher) {
        this.registry = registry;
        this.dispatcher = dispatcher;
    }

    public UUID start(UUID scenarioId, UUID sessionId) {
        Scenario scenario = registry.getById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + scenarioId));
        UUID executionId = UUID.randomUUID();
        ExecutionContext ctx = new ExecutionContext(executionId, scenario, sessionId);
        contexts.put(executionId, ctx);

        executor.submit(() -> runScenario(ctx));
        return executionId;
    }

    public void stop(UUID executionId) {
        ExecutionContext ctx = contexts.get(executionId);
        if (ctx != null) ctx.setStatus(ExecutionStatus.STOPPED);
    }

    public ExecutionStatus getStatus(UUID executionId) {
        ExecutionContext ctx = contexts.get(executionId);
        return ctx == null ? null : ctx.status();
    }

    public ExecutionContext getContext(UUID executionId) {
        return contexts.get(executionId);
    }

    private void runScenario(ExecutionContext ctx) {
        try {
            ScenarioNode current = ctx.scenario().startNode()
                    .orElseThrow(() -> new IllegalStateException("Scenario has no START node"));

            while (current != null && ctx.status() == ExecutionStatus.RUNNING) {
                ctx.setCurrentNodeId(current.id());
                NodeHandlerResult result = dispatcher.dispatch(current, ctx);

                if (ctx.status() != ExecutionStatus.RUNNING) break;

                if (!result.success()) {
                    ctx.setStatus(ExecutionStatus.FAILED);
                    break;
                }
                if (result.nextNodeId() == null) break;
                current = ctx.scenario().findNode(result.nextNodeId()).orElse(null);
            }

            if (ctx.status() == ExecutionStatus.RUNNING) {
                ctx.setStatus(ExecutionStatus.PASSED);
            }
        } catch (Throwable t) {
            ctx.setStatus(ExecutionStatus.FAILED);
        }
    }
}
```

- [ ] **11.3 — Failing test** `ExecutionManagerTest.java`:

```java
package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.fix.FakeFixAdapter;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExecutionManagerTest {

    @Test
    void startToEndPassFlow() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new SendFIXHandler(fake),
                new EndHandler()
        ));

        UUID scenarioId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, 5, true, false));

        Scenario scenario = new Scenario(scenarioId, "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START, Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "send",  NodeType.SEND_FIX,
                                Map.of("msgType", "D", "fields", Map.of("11", "REQ-1", "55", "AAPL")),
                                null, null, "n3", null, null),
                        new ScenarioNode("n3", "done", NodeType.END_PASS, Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of());

        registry.register(scenario);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        // Inject sessionId into context
        UUID executionId = mgr.start(scenarioId, sessionId);

        await().atMost(2, TimeUnit.SECONDS).until(() -> mgr.getStatus(executionId) == ExecutionStatus.PASSED);
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0)).containsEntry(11, "REQ-1");
    }
}
```

Add Awaitility to `fix-flow-engine/pom.xml` as a test dependency:
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **11.4 — Run after Task 12 handlers exist:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=ExecutionManagerTest
  ```

- [ ] **11.5 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: ExecutionContext + ExecutionManager (virtual-thread runner)"
  ```

---

### Task 12: NodeHandler interface + SendFIXHandler + EndHandler + NodeDispatcher

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/NodeHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/NodeHandlerResult.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/SendFIXHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/EndHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/NodeDispatcher.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/SendFIXHandlerTest.java`

**Steps:**

- [ ] **12.1 — Failing test:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.fix.FakeFixAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendFIXHandlerTest {

    @Test
    void sendsResolvedFieldsViaPortAndReturnsOnSuccess() {
        FakeFixAdapter fake = new FakeFixAdapter();
        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, 5, true, false));

        SendFIXHandler handler = new SendFIXHandler(fake);

        ScenarioNode node = new ScenarioNode("n2", "send", NodeType.SEND_FIX,
                Map.of("msgType", "D", "fields", Map.of("11", "CL-1", "55", "AAPL")),
                null, null, "n3", null, null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(), List.of(),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, sessionId);

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.nextNodeId()).isEqualTo("n3");
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0))
                .containsEntry(35, "D")
                .containsEntry(11, "CL-1")
                .containsEntry(55, "AAPL");
        assertThat(ctx.getNodeMessage("n2")).isNotNull();
    }
}
```

- [ ] **12.2 — `NodeHandler.java`:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;

public interface NodeHandler {
    NodeType getSupportedType();
    NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException;
}
```

- [ ] **12.3 — `NodeHandlerResult.java`:**

```java
package com.fixflow.engine.handlers;

public record NodeHandlerResult(String nextNodeId, boolean success, String errorMessage) {
    public static NodeHandlerResult success(String next) {
        return new NodeHandlerResult(next, true, null);
    }
    public static NodeHandlerResult failure(String next, String error) {
        return new NodeHandlerResult(next, false, error);
    }
    public static NodeHandlerResult terminal() {
        return new NodeHandlerResult(null, true, null);
    }
}
```

- [ ] **12.4 — `SendFIXHandler.java`:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SendFIXHandler implements NodeHandler {

    private final FIXSessionPort port;

    public SendFIXHandler(FIXSessionPort port) { this.port = port; }

    @Override
    public NodeType getSupportedType() { return NodeType.SEND_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        Map<String, Object> cfg = node.config();
        Map<Integer, String> outFields = new HashMap<>();

        Object msgType = cfg.get("msgType");
        if (msgType != null) outFields.put(35, resolve(String.valueOf(msgType), ctx));

        Object fields = cfg.get("fields");
        if (fields instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                int tag = Integer.parseInt(String.valueOf(e.getKey()));
                outFields.put(tag, resolve(String.valueOf(e.getValue()), ctx));
            }
        }

        port.sendMessage(ctx.sessionId(), outFields);
        ctx.storeNodeMessage(node.id(), outFields);
        return NodeHandlerResult.success(node.onSuccess());
    }

    /** Stub variable resolution — Task in Part 2 introduces a real VariableResolver. */
    private String resolve(String template, ExecutionContext ctx) {
        if (template == null) return null;
        if ("{{uuid}}".equals(template)) return java.util.UUID.randomUUID().toString();
        return template;
    }
}
```

- [ ] **12.5 — `EndHandler.java`:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class EndHandler implements NodeHandler {

    @Override
    public NodeType getSupportedType() { return NodeType.END_PASS; }

    /** Same instance is registered for END_FAIL via {@link #handleAnyEnd(ScenarioNode, ExecutionContext)}. */
    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        return handleAnyEnd(node, ctx);
    }

    public static NodeHandlerResult handleAnyEnd(ScenarioNode node, ExecutionContext ctx) {
        ctx.setStatus(node.type() == NodeType.END_PASS
                ? ExecutionStatus.PASSED
                : ExecutionStatus.FAILED);
        return NodeHandlerResult.terminal();
    }
}
```

Also register the END_FAIL variant via a sibling component:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
class EndFailHandler implements NodeHandler {
    @Override public NodeType getSupportedType() { return NodeType.END_FAIL; }
    @Override public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        return EndHandler.handleAnyEnd(node, ctx);
    }
}
```

Add a START handler so the engine can leave the START node:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
class StartHandler implements NodeHandler {
    @Override public NodeType getSupportedType() { return NodeType.START; }
    @Override public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) {
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

- [ ] **12.6 — `NodeDispatcher.java`:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NodeDispatcher {

    private final Map<NodeType, NodeHandler> registry = new EnumMap<>(NodeType.class);

    public NodeDispatcher(List<NodeHandler> handlers) {
        for (NodeHandler h : handlers) registry.put(h.getSupportedType(), h);
    }

    public NodeHandlerResult dispatch(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        NodeHandler h = registry.get(node.type());
        if (h == null) {
            return NodeHandlerResult.failure(null, "No handler for node type " + node.type());
        }
        return h.handle(node, ctx);
    }
}
```

- [ ] **12.7 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=SendFIXHandlerTest
  mvn test -pl fix-flow-engine -Dtest=ExecutionManagerTest
  ```
  Expected: both pass. Update `ExecutionManagerTest` handler list to include `StartHandler` if test fails because START has no handler:
  ```java
  new NodeDispatcher(List.of(new StartHandler(), new SendFIXHandler(fake), new EndHandler(), new EndFailHandler()));
  ```
  (Make `StartHandler`/`EndFailHandler` package-public for the test to instantiate them.)

- [ ] **12.8 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: NodeHandler API + SendFIXHandler + EndHandler + NodeDispatcher"
  ```

---

### Task 13: ExpectFIXHandler with timeout

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ExpectFIXHandler.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/ExpectFIXHandlerTest.java`

**Note:** This task introduces a thin `CorrelationEngine` stub interface so the handler is testable in isolation. Full engine implemented in Task 15.

**Steps:**

- [ ] **13.1 — Failing test:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectFIXHandlerTest {

    @Test
    void returnsSuccessWhenMatchingMessageArrives() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        ExpectFIXHandler handler = new ExpectFIXHandler(engine);

        ScenarioNode node = new ScenarioNode(
                "n3", "expect", NodeType.EXPECT_FIX,
                Map.of("correlationTag", 131, "expectedValue", "REQ-1"),
                new TimeoutConfig(2, TimeUnit.SECONDS, TimeoutAction.FAIL, null),
                null, "n4", null, null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n3", 131, 2000)),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            engine.onMessage("sess", Map.of(131, "REQ-1", 35, "8"));
        });

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.nextNodeId()).isEqualTo("n4");
        assertThat(ctx.getNodeMessage("n3")).containsEntry(131, "REQ-1");
    }

    @Test
    void returnsFailureOnTimeoutWithFailAction() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        ExpectFIXHandler handler = new ExpectFIXHandler(engine);

        ScenarioNode node = new ScenarioNode(
                "n3", "expect", NodeType.EXPECT_FIX,
                Map.of("correlationTag", 131, "expectedValue", "REQ-X"),
                new TimeoutConfig(100, TimeUnit.MILLISECONDS, TimeoutAction.FAIL, null),
                null, "n4", "nf", null);

        Scenario s = new Scenario(UUID.randomUUID(), "demo", "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n3", 131, 100)),
                List.of(node), List.of(), Map.of());
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), s, UUID.randomUUID());

        NodeHandlerResult result = handler.handle(node, ctx);

        assertThat(result.success()).isFalse();
        assertThat(result.nextNodeId()).isEqualTo("nf");
    }
}
```

- [ ] **13.2 — Implement `ExpectFIXHandler.java`:**

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ExpectFIXHandler implements NodeHandler {

    private final CorrelationEngine correlation;

    public ExpectFIXHandler(CorrelationEngine correlation) { this.correlation = correlation; }

    @Override
    public NodeType getSupportedType() { return NodeType.EXPECT_FIX; }

    @Override
    public NodeHandlerResult handle(ScenarioNode node, ExecutionContext ctx) throws InterruptedException {
        Map<String, Object> cfg = node.config();
        int tag = ((Number) cfg.get("correlationTag")).intValue();
        String expected = String.valueOf(cfg.get("expectedValue"));

        CorrelationRule rule = ctx.scenario().correlationRules().stream()
                .filter(r -> r.sourceTag() == tag)
                .findFirst()
                .orElse(new CorrelationRule(tag, node.id(), tag, 0));

        CompletableFuture<Map<Integer, String>> future =
                correlation.register(ctx.executionId().toString(), rule, expected);

        long timeoutMs = node.timeout() == null ? 5_000L : node.timeout().toMillis();

        try {
            Map<Integer, String> fields = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            ctx.storeNodeMessage(node.id(), fields);
            return NodeHandlerResult.success(node.onSuccess());
        } catch (TimeoutException timeout) {
            correlation.cancel(ctx.executionId().toString());
            return onTimeout(node);
        } catch (Exception other) {
            correlation.cancel(ctx.executionId().toString());
            return NodeHandlerResult.failure(node.onFailure(), other.getMessage());
        }
    }

    private NodeHandlerResult onTimeout(ScenarioNode node) {
        TimeoutAction action = node.timeout() == null ? TimeoutAction.FAIL : node.timeout().onTimeout();
        return switch (action) {
            case FAIL     -> NodeHandlerResult.failure(node.onFailure(), "timeout");
            case CONTINUE -> NodeHandlerResult.success(node.onSuccess());
            case RETRY    -> NodeHandlerResult.failure(node.onFailure(), "timeout-retry-exhausted");
            case JUMP     -> NodeHandlerResult.success(node.timeout().jumpTo());
        };
    }
}
```

- [ ] **13.3 — Run** (requires `CorrelationEngine` from Task 15 to exist — implement that file first or stub it):
  ```bash
  mvn test -pl fix-flow-engine -Dtest=ExpectFIXHandlerTest
  ```
  Expected: 2/2 pass.

- [ ] **13.4 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: ExpectFIXHandler with timeout actions (FAIL/CONTINUE/RETRY/JUMP)"
  ```

---

## Phase 5: MessageRouter + CorrelationEngine + MessageBuffer

### Task 14: MessageBuffer (ring buffer per session)

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/MessageBuffer.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/MessageBufferTest.java`

**Steps:**

- [ ] **14.1 — Failing test:**

```java
package com.fixflow.engine.fix;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBufferTest {

    @Test
    void parkAndPollExactMatch() {
        MessageBuffer buf = new MessageBuffer(10, 60_000);
        buf.park("s1", Map.of(35, "8", 131, "REQ-1"));

        Optional<Map<Integer, String>> found =
                buf.poll("s1", f -> "REQ-1".equals(f.get(131)));

        assertThat(found).isPresent();
        assertThat(found.get()).containsEntry(35, "8");
        assertThat(buf.poll("s1", f -> "REQ-1".equals(f.get(131)))).isEmpty();
    }

    @Test
    void capacityEvictsOldestOnOverflow() {
        MessageBuffer buf = new MessageBuffer(2, 60_000);
        buf.park("s1", Map.of(11, "A"));
        buf.park("s1", Map.of(11, "B"));
        buf.park("s1", Map.of(11, "C"));

        assertThat(buf.poll("s1", f -> "A".equals(f.get(11)))).isEmpty();
        assertThat(buf.poll("s1", f -> "B".equals(f.get(11)))).isPresent();
        assertThat(buf.poll("s1", f -> "C".equals(f.get(11)))).isPresent();
    }

    @Test
    void ttlExpiryRemovesStaleEntries() throws Exception {
        MessageBuffer buf = new MessageBuffer(10, 50);
        buf.park("s1", Map.of(11, "X"));
        Thread.sleep(100);
        assertThat(buf.poll("s1", f -> "X".equals(f.get(11)))).isEmpty();
    }

    @Test
    void pauseAndResume() {
        MessageBuffer buf = new MessageBuffer(10, 60_000);
        assertThat(buf.isPaused()).isFalse();
        buf.pause();
        assertThat(buf.isPaused()).isTrue();
        buf.resume();
        assertThat(buf.isPaused()).isFalse();
    }
}
```

- [ ] **14.2 — Implementation `MessageBuffer.java`:**

```java
package com.fixflow.engine.fix;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@Service
public class MessageBuffer {

    public record BufferedMessage(Map<Integer, String> fields, Instant parkedAt) {}

    private final int capacity;
    private final long ttlMs;
    private final Map<String, Deque<BufferedMessage>> buffers = new ConcurrentHashMap<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public MessageBuffer() {
        this(1024, Duration.ofMinutes(5).toMillis());
    }

    public MessageBuffer(int capacity, long ttlMs) {
        this.capacity = capacity;
        this.ttlMs = ttlMs;
    }

    public void park(String sessionId, Map<Integer, String> fields) {
        Deque<BufferedMessage> deque =
                buffers.computeIfAbsent(sessionId, k -> new ConcurrentLinkedDeque<>());
        deque.addFirst(new BufferedMessage(Map.copyOf(fields), Instant.now()));
        while (deque.size() > capacity) deque.pollLast();
    }

    public Optional<Map<Integer, String>> poll(String sessionId, Predicate<Map<Integer, String>> matcher) {
        Deque<BufferedMessage> deque = buffers.get(sessionId);
        if (deque == null) return Optional.empty();

        Instant now = Instant.now();
        Iterator<BufferedMessage> it = deque.iterator();
        while (it.hasNext()) {
            BufferedMessage m = it.next();
            if (now.toEpochMilli() - m.parkedAt().toEpochMilli() > ttlMs) {
                it.remove();
                continue;
            }
            if (matcher.test(m.fields())) {
                it.remove();
                return Optional.of(m.fields());
            }
        }
        return Optional.empty();
    }

    public void pause() { paused.set(true); }
    public void resume() { paused.set(false); }
    public boolean isPaused() { return paused.get(); }

    public int size(String sessionId) {
        Deque<BufferedMessage> d = buffers.get(sessionId);
        return d == null ? 0 : d.size();
    }
}
```

- [ ] **14.3 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=MessageBufferTest
  ```
  Expected: 4/4 pass.

- [ ] **14.4 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: per-session MessageBuffer with capacity eviction + TTL + pause/resume"
  ```

---

### Task 15: CorrelationEngine

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/correlation/CorrelationEngine.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/correlation/CorrelationEngineTest.java`

**Steps:**

- [ ] **15.1 — Failing test:**

```java
package com.fixflow.engine.correlation;

import com.fixflow.core.domain.scenario.CorrelationRule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationEngineTest {

    @Test
    void deliversMatchingMessageToWaiter() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationRule rule = new CorrelationRule(131, "n1", 131, 1000);
        CompletableFuture<Map<Integer, String>> f = engine.register("exec-1", rule, "REQ-1");

        engine.onMessage("sess", Map.of(131, "REQ-1", 35, "8"));

        Map<Integer, String> got = f.get(500, TimeUnit.MILLISECONDS);
        assertThat(got).containsEntry(131, "REQ-1");
    }

    @Test
    void nonMatchingMessageLeavesWaiterPending() {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationRule rule = new CorrelationRule(131, "n1", 131, 1000);
        CompletableFuture<Map<Integer, String>> f = engine.register("exec-1", rule, "REQ-1");

        engine.onMessage("sess", Map.of(131, "REQ-OTHER"));

        assertThatThrownBy(() -> f.get(100, TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void cancelCompletesFutureExceptionally() {
        CorrelationEngine engine = new CorrelationEngine();
        CorrelationRule rule = new CorrelationRule(131, "n1", 131, 1000);
        CompletableFuture<Map<Integer, String>> f = engine.register("exec-1", rule, "REQ-1");

        engine.cancel("exec-1");
        assertThat(f).isCancelled();
    }
}
```

- [ ] **15.2 — Implementation:**

```java
package com.fixflow.engine.correlation;

import com.fixflow.core.domain.scenario.CorrelationRule;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CorrelationEngine {

    public record CorrelationWaiter(
            String executionId,
            CorrelationRule rule,
            String expectedValue,
            CompletableFuture<Map<Integer, String>> future) {}

    private final ConcurrentHashMap<String, CorrelationWaiter> waiters = new ConcurrentHashMap<>();

    public CompletableFuture<Map<Integer, String>> register(String executionId,
                                                            CorrelationRule rule,
                                                            String expectedValue) {
        CompletableFuture<Map<Integer, String>> future = new CompletableFuture<>();
        waiters.put(executionId, new CorrelationWaiter(executionId, rule, expectedValue, future));
        return future;
    }

    public boolean onMessage(String sessionId, Map<Integer, String> fields) {
        for (CorrelationWaiter w : waiters.values()) {
            String actual = fields.getOrDefault(w.rule().sourceTag(), "");
            if (actual.equals(w.expectedValue())) {
                waiters.remove(w.executionId());
                w.future().complete(Map.copyOf(fields));
                return true;
            }
        }
        return false;
    }

    public void cancel(String executionId) {
        CorrelationWaiter w = waiters.remove(executionId);
        if (w != null) w.future().cancel(true);
    }

    public int pendingCount() { return waiters.size(); }
}
```

- [ ] **15.3 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=CorrelationEngineTest
  ```
  Expected: 3/3 pass.

- [ ] **15.4 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine: CorrelationEngine with waiter registry + cancel"
  ```

---

### Task 16: MessageRouter

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/MessageRouter.java`
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/MessageRouterTest.java`

**Steps:**

- [ ] **16.1 — Failing test:**

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.scenario.CorrelationRule;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRouterTest {

    @Test
    void messageMatchingActiveWaiterIsDelivered() throws Exception {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(10, 60_000);
        MessageRouter router = new MessageRouter(engine, buffer);

        CompletableFuture<Map<Integer, String>> f =
                engine.register("exec-1", new CorrelationRule(131, "n", 131, 1000), "REQ-1");

        router.onMessage("sess", Map.of(131, "REQ-1"));

        assertThat(f.get(200, TimeUnit.MILLISECONDS)).containsEntry(131, "REQ-1");
        assertThat(buffer.size("sess")).isZero();
    }

    @Test
    void unmatchedMessageIsParked() {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(10, 60_000);
        MessageRouter router = new MessageRouter(engine, buffer);

        router.onMessage("sess", Map.of(131, "REQ-XYZ"));

        assertThat(buffer.size("sess")).isEqualTo(1);
    }

    @Test
    void drainBufferDeliversParkedMessageToNewWaiter() {
        CorrelationEngine engine = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(10, 60_000);
        MessageRouter router = new MessageRouter(engine, buffer);

        router.onMessage("sess", Map.of(131, "REQ-LATE"));
        CompletableFuture<Map<Integer, String>> f =
                engine.register("exec-2", new CorrelationRule(131, "n", 131, 1000), "REQ-LATE");

        router.drain("sess");

        assertThat(f).isCompleted();
    }
}
```

- [ ] **16.2 — Implementation:**

```java
package com.fixflow.engine.fix;

import com.fixflow.core.ports.outbound.InboundMessageListener;
import com.fixflow.engine.correlation.CorrelationEngine;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class MessageRouter implements InboundMessageListener {

    private final CorrelationEngine correlation;
    private final MessageBuffer buffer;

    public MessageRouter(CorrelationEngine correlation, MessageBuffer buffer) {
        this.correlation = correlation;
        this.buffer = buffer;
    }

    @Override
    public void onMessage(String sessionId, Map<Integer, String> fields) {
        if (buffer.isPaused()) {
            buffer.park(sessionId, fields);
            return;
        }
        boolean consumed = correlation.onMessage(sessionId, fields);
        if (!consumed) buffer.park(sessionId, fields);
    }

    /**
     * Called when a new waiter is registered: drain any parked messages
     * that the waiter would have consumed.
     */
    public void drain(String sessionId) {
        Optional<Map<Integer, String>> next;
        do {
            next = buffer.poll(sessionId, fields -> correlation.onMessage(sessionId, fields));
        } while (next.isPresent());
    }
}
```

- [ ] **16.3 — Wire router into adapter:** in `fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java` rely on Spring's component scan — both `MessageRouter` (engine) and `QuickFIXAdapter` (adapters) are discovered. Add a small `@Configuration` so the router becomes the inbound listener on app startup:

```java
package com.fixflow.api.config;

import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.engine.fix.MessageRouter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class InboundWiring {
    private final FIXSessionPort port;
    private final MessageRouter router;

    public InboundWiring(FIXSessionPort port, MessageRouter router) {
        this.port = port;
        this.router = router;
    }

    @PostConstruct
    void wire() { port.setInboundListener(router); }
}
```

- [ ] **16.4 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=MessageRouterTest
  ```
  Expected: 3/3 pass.

- [ ] **16.5 — Commit:**
  ```bash
  git add fix-flow-engine fix-flow-api
  git commit -m "engine: MessageRouter (correlate-or-park) + inbound wiring config"
  ```

---

### Task 17: End-to-end flow test (multi-scenario on one session)

**Files:**
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/MultiScenarioIntegrationTest.java`

**Steps:**

- [ ] **17.1 — Test (no Spring context; constructor injection only):**

```java
package com.fixflow.engine;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.fix.FakeFixAdapter;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultiScenarioIntegrationTest {

    @Test
    void twoScenariosOnSameSessionResolveCorrelatedMessages() {
        FakeFixAdapter fake = new FakeFixAdapter();
        ScenarioRegistry registry = new ScenarioRegistry();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(64, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake),
                new ExpectFIXHandler(correlation),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, 5, true, false));

        Scenario a = scenario("REQ-A");
        Scenario b = scenario("REQ-B");
        registry.register(a);
        registry.register(b);

        ExecutionManager mgr = new ExecutionManager(registry, dispatcher);
        UUID execA = mgr.start(a.id(), sessionId);
        UUID execB = mgr.start(b.id(), sessionId);

        // Give the engine a tick to register both waiters.
        await().atMost(1, TimeUnit.SECONDS).until(() -> correlation.pendingCount() == 2);

        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-B"));
        fake.injectInbound(sessionId, Map.of(35, "8", 131, "REQ-A"));

        await().atMost(3, TimeUnit.SECONDS)
                .until(() -> mgr.getStatus(execA) == ExecutionStatus.PASSED
                          && mgr.getStatus(execB) == ExecutionStatus.PASSED);

        assertThat(fake.getSentMessages()).hasSize(2);
    }

    private Scenario scenario(String reqId) {
        UUID id = UUID.randomUUID();
        return new Scenario(id, "demo-" + reqId, "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n3", 131, 5000)),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,
                                Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "send", NodeType.SEND_FIX,
                                Map.of("msgType", "D", "fields", Map.of("11", reqId, "131", reqId)),
                                null, null, "n3", null, null),
                        new ScenarioNode("n3", "expect", NodeType.EXPECT_FIX,
                                Map.of("correlationTag", 131, "expectedValue", reqId),
                                new TimeoutConfig(3, TimeUnit.SECONDS, TimeoutAction.FAIL, null),
                                null, "n4", "nf", null),
                        new ScenarioNode("n4", "done", NodeType.END_PASS,
                                Map.of(), null, null, null, null, null),
                        new ScenarioNode("nf", "fail", NodeType.END_FAIL,
                                Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of());
    }
}
```

- [ ] **17.2 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=MultiScenarioIntegrationTest
  ```
  Expected: PASS.

- [ ] **17.3 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine(test): multi-scenario integration on shared session"
  ```

---

### Task 18: Timeout + retry integration test

**Files:**
- Create: `fix-flow-engine/src/test/java/com/fixflow/engine/TimeoutRetryTest.java`

**Note:** Real RETRY semantics need a `RetryHandler` introduced in Part 2. For now we validate the FAIL and CONTINUE paths end-to-end and assert that a RETRY without supporting handler degrades to FAIL after the timeout fires (consistent with `ExpectFIXHandler#onTimeout` returning failure for RETRY).

**Steps:**

- [ ] **18.1 — Test:**

```java
package com.fixflow.engine;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.*;
import com.fixflow.core.domain.session.*;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.fix.FakeFixAdapter;
import com.fixflow.engine.fix.MessageBuffer;
import com.fixflow.engine.fix.MessageRouter;
import com.fixflow.engine.handlers.*;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TimeoutRetryTest {

    private record Wiring(ExecutionManager mgr, FakeFixAdapter fake, UUID sessionId) {}

    private Wiring buildWiring() {
        FakeFixAdapter fake = new FakeFixAdapter();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(32, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);

        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(),
                new SendFIXHandler(fake),
                new ExpectFIXHandler(correlation),
                new EndHandler(),
                new EndFailHandler()
        ));

        UUID sessionId = UUID.randomUUID();
        fake.connect(new FIXSessionConfig(sessionId, "s1", FIXMode.INITIATOR, FIXVersion.FIX_44,
                null, "S", "T", "h", 1, 30, 5, true, false));

        return new Wiring(new ExecutionManager(new ScenarioRegistry() {{ }}, dispatcher), fake, sessionId);
    }

    @Test
    void timeoutFailMarksExecutionFailed() {
        FakeFixAdapter fake = new FakeFixAdapter();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(32, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(), new ExpectFIXHandler(correlation),
                new EndHandler(), new EndFailHandler()));

        UUID sessionId = UUID.randomUUID();
        ScenarioRegistry reg = new ScenarioRegistry();
        Scenario s = scenarioExpectOnly(TimeoutAction.FAIL);
        reg.register(s);

        ExecutionManager mgr = new ExecutionManager(reg, dispatcher);
        UUID exec = mgr.start(s.id(), sessionId);

        await().atMost(2, TimeUnit.SECONDS).until(() -> mgr.getStatus(exec) == ExecutionStatus.FAILED);
        assertThat(mgr.getStatus(exec)).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void timeoutContinueSkipsToOnSuccessAndPasses() {
        FakeFixAdapter fake = new FakeFixAdapter();
        CorrelationEngine correlation = new CorrelationEngine();
        MessageBuffer buffer = new MessageBuffer(32, 60_000);
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router);
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new StartHandler(), new ExpectFIXHandler(correlation),
                new EndHandler(), new EndFailHandler()));

        ScenarioRegistry reg = new ScenarioRegistry();
        Scenario s = scenarioExpectOnly(TimeoutAction.CONTINUE);
        reg.register(s);

        ExecutionManager mgr = new ExecutionManager(reg, dispatcher);
        UUID exec = mgr.start(s.id(), UUID.randomUUID());

        await().atMost(2, TimeUnit.SECONDS).until(() -> mgr.getStatus(exec) == ExecutionStatus.PASSED);
    }

    private Scenario scenarioExpectOnly(TimeoutAction action) {
        UUID id = UUID.randomUUID();
        return new Scenario(id, "to-" + action, "", "1.0", "sess",
                RuntimePolicy.PARALLEL, List.of(),
                List.of(new CorrelationRule(131, "n2", 131, 100)),
                List.of(
                        new ScenarioNode("n1", "start", NodeType.START,
                                Map.of(), null, null, "n2", null, null),
                        new ScenarioNode("n2", "expect", NodeType.EXPECT_FIX,
                                Map.of("correlationTag", 131, "expectedValue", "NEVER-COMES"),
                                new TimeoutConfig(100, com.fixflow.core.domain.scenario.TimeUnit.MILLISECONDS,
                                        action, null),
                                new RetryPolicy(2, 10),
                                "n3", "nf", null),
                        new ScenarioNode("n3", "done", NodeType.END_PASS,
                                Map.of(), null, null, null, null, null),
                        new ScenarioNode("nf", "fail", NodeType.END_FAIL,
                                Map.of(), null, null, null, null, null)
                ),
                List.of(), Map.of());
    }
}
```

- [ ] **18.2 — Run:**
  ```bash
  mvn test -pl fix-flow-engine -Dtest=TimeoutRetryTest
  ```
  Expected: both tests pass.

- [ ] **18.3 — Commit:**
  ```bash
  git add fix-flow-engine
  git commit -m "engine(test): timeout FAIL + CONTINUE integration coverage"
  ```


---


## Phase 6: ValidationEngine + DateRuleEngine + VariableResolver (Tasks 19-23)

---

### Task 19: VariableResolver

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolver.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolverPlugin.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/variable/VariableResolverTest.java`

#### Step 1: Write failing test

`fix-flow-engine/src/test/java/com/fixflow/engine/variable/VariableResolverTest.java`:

```java
package com.fixflow.engine.variable;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class VariableResolverTest {

    private VariableResolver resolver;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver();
        ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void resolvesNowAsValidIsoInstant() {
        String out = resolver.resolveAll("{{now}}", ctx);
        Instant parsed = Instant.parse(out);
        assertThat(parsed).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void resolvesUuidAsValidUuid() {
        String out = resolver.resolveAll("{{uuid}}", ctx);
        UUID parsed = UUID.fromString(out);
        assertThat(parsed).isNotNull();
    }

    @Test
    void resolvesSeqIncrementing() {
        String first = resolver.resolveAll("{{seq:orders}}", ctx);
        String second = resolver.resolveAll("{{seq:orders}}", ctx);
        assertThat(first).isEqualTo("1");
        assertThat(second).isEqualTo("2");
    }

    @Test
    void resolvesEnvVariable() {
        String out = resolver.resolveAll("{{env:HOME}}", ctx);
        assertThat(out).isNotNull().isNotBlank();
    }

    @Test
    void resolvesNodeFieldReference() {
        Map<Integer, String> fields = new HashMap<>();
        fields.put(131, "QR-12345");
        ctx.storeNodeMessage("n1", new FIXMessage("R", fields));
        String out = resolver.resolveAll("{{node:n1:tag131}}", ctx);
        assertThat(out).isEqualTo("QR-12345");
    }

    @Test
    void resolvesDateOffsetPlusFiveMinutes() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> fields = new HashMap<>();
        fields.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", fields));
        String out = resolver.resolveAll("{{node:n1:tag60:offset:+5m}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isEqualTo(base.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void resolvesDateOffsetMinusOneHour() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> fields = new HashMap<>();
        fields.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", fields));
        String out = resolver.resolveAll("{{node:n1:tag60:offset:-1h}}", ctx);
        Instant resolved = Instant.parse(out);
        assertThat(resolved).isEqualTo(base.minus(1, ChronoUnit.HOURS));
    }

    @Test
    void resolvesMultipleVariablesInTemplate() {
        Map<Integer, String> fields = new HashMap<>();
        fields.put(131, "QR-1");
        ctx.storeNodeMessage("n1", new FIXMessage("R", fields));
        String out = resolver.resolveAll("ID={{node:n1:tag131}};TS={{now}}", ctx);
        assertThat(out).startsWith("ID=QR-1;TS=");
        assertThat(Pattern.matches("ID=QR-1;TS=.+Z", out)).isTrue();
    }

    private static org.assertj.core.api.InstantAssert within(long amount, ChronoUnit unit) {
        return null; // unused, see assertThat usage
    }
}
```

Note: use `assertThat(parsed).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))` from AssertJ's `Assertions.within`. Replace the placeholder method with `import static org.assertj.core.api.Assertions.within;`.

Run — expect compile failure (no VariableResolver yet).

#### Step 2: Plugin interface

`fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolverPlugin.java`:

```java
package com.fixflow.engine.variable;

import com.fixflow.core.execution.ExecutionContext;

public interface VariableResolverPlugin {
    boolean supports(String expression);
    String resolve(String expression, ExecutionContext ctx);
}
```

#### Step 3: VariableResolver implementation

`fix-flow-engine/src/main/java/com/fixflow/engine/variable/VariableResolver.java`:

```java
package com.fixflow.engine.variable;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VariableResolver {

    private static final Pattern EXPR = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final List<VariableResolverPlugin> plugins;
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public VariableResolver() {
        this.plugins = List.of(
            new NowPlugin(),
            new UuidPlugin(),
            new SeqPlugin(sequences),
            new EnvPlugin(),
            new DateOffsetPlugin(),
            new NodeFieldPlugin()
        );
    }

    public String resolveAll(String template, ExecutionContext ctx) {
        if (template == null) return null;
        Matcher m = EXPR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String value = dispatch(expr, ctx);
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String dispatch(String expression, ExecutionContext ctx) {
        for (VariableResolverPlugin p : plugins) {
            if (p.supports(expression)) {
                return p.resolve(expression, ctx);
            }
        }
        throw new IllegalArgumentException("No plugin handles expression: " + expression);
    }

    // ----- Built-in plugins -----

    static final class NowPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("now"); }
        public String resolve(String e, ExecutionContext c) { return Instant.now().toString(); }
    }

    static final class UuidPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("uuid"); }
        public String resolve(String e, ExecutionContext c) { return UUID.randomUUID().toString(); }
    }

    static final class SeqPlugin implements VariableResolverPlugin {
        private final ConcurrentHashMap<String, AtomicLong> sequences;
        SeqPlugin(ConcurrentHashMap<String, AtomicLong> s) { this.sequences = s; }
        public boolean supports(String e) { return e.startsWith("seq:"); }
        public String resolve(String e, ExecutionContext c) {
            String name = e.substring("seq:".length());
            return Long.toString(sequences.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet());
        }
    }

    static final class EnvPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.startsWith("env:"); }
        public String resolve(String e, ExecutionContext c) {
            String var = e.substring("env:".length());
            String value = System.getenv(var);
            return value == null ? "" : value;
        }
    }

    static final class DateOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile(
            "^node:([^:]+):tag(\\d+):offset:([+\\-])(\\d+)([smhd])$"
        );

        public boolean supports(String e) { return P.matcher(e).matches(); }

        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad date offset: " + e);
            String nodeId = m.group(1);
            int tag = Integer.parseInt(m.group(2));
            String sign = m.group(3);
            long amount = Long.parseLong(m.group(4));
            String unit = m.group(5);
            FIXMessage msg = c.getNodeMessage(nodeId);
            if (msg == null) throw new IllegalStateException("No stored message for node: " + nodeId);
            String raw = msg.fields().get(tag);
            if (raw == null) throw new IllegalStateException("No tag " + tag + " on node " + nodeId);
            Instant base = Instant.parse(raw);
            ChronoUnit cu = switch (unit) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default -> throw new IllegalArgumentException("Bad unit: " + unit);
            };
            Instant result = sign.equals("+") ? base.plus(amount, cu) : base.minus(amount, cu);
            return result.toString();
        }
    }

    static final class NodeFieldPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^node:([^:]+):tag(\\d+)$");

        public boolean supports(String e) { return P.matcher(e).matches(); }

        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad node ref: " + e);
            String nodeId = m.group(1);
            int tag = Integer.parseInt(m.group(2));
            FIXMessage msg = c.getNodeMessage(nodeId);
            if (msg == null) throw new IllegalStateException("No stored message for node: " + nodeId);
            String v = msg.fields().get(tag);
            return v == null ? "" : v;
        }
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=VariableResolverTest
```

Expected: all pass.

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/variable/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/variable/
git commit -m "feat(engine): add VariableResolver with built-in plugins (now, uuid, seq, env, node, date-offset)"
```

---

### Task 20: ValidationRule interface + basic rules

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationResult.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/EqualsRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/NotEqualsRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/EnumRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/RegexRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/NumericMinRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/NumericMaxRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/FieldPresentRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/FieldAbsentRule.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationRulesTest.java`

#### Step 1: Write failing tests

`fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationRulesTest.java`:

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    private final ExecutionContext ctx = new ExecutionContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @Test
    void equalsRulePassesWhenValueMatches() {
        EqualsRule rule = new EqualsRule("S", null);
        ValidationResult r = rule.validate(35, Map.of(35, "S"), ctx);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void equalsRuleFailsWhenValueDiffers() {
        EqualsRule rule = new EqualsRule("S", null);
        ValidationResult r = rule.validate(35, Map.of(35, "D"), ctx);
        assertThat(r.passed()).isFalse();
        assertThat(r.expected()).isEqualTo("S");
        assertThat(r.actual()).isEqualTo("D");
    }

    @Test
    void enumRulePassesWhenInList() {
        EnumRule rule = new EnumRule(List.of("1", "2", "3"));
        assertThat(rule.validate(39, Map.of(39, "2"), ctx).passed()).isTrue();
    }

    @Test
    void enumRuleFailsWhenNotInList() {
        EnumRule rule = new EnumRule(List.of("1", "2", "3"));
        assertThat(rule.validate(39, Map.of(39, "9"), ctx).passed()).isFalse();
    }

    @Test
    void regexRulePassesWhenPatternMatches() {
        RegexRule rule = new RegexRule("^ORD-\\d+$");
        assertThat(rule.validate(11, Map.of(11, "ORD-123"), ctx).passed()).isTrue();
    }

    @Test
    void regexRuleFailsWhenPatternDoesNotMatch() {
        RegexRule rule = new RegexRule("^ORD-\\d+$");
        assertThat(rule.validate(11, Map.of(11, "X"), ctx).passed()).isFalse();
    }

    @Test
    void numericMinRulePassesWhenAboveMin() {
        NumericMinRule rule = new NumericMinRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "200"), ctx).passed()).isTrue();
    }

    @Test
    void numericMinRuleFailsWhenBelowMin() {
        NumericMinRule rule = new NumericMinRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "50"), ctx).passed()).isFalse();
    }

    @Test
    void numericMaxRulePassesWhenBelowMax() {
        NumericMaxRule rule = new NumericMaxRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "50"), ctx).passed()).isTrue();
    }

    @Test
    void numericMaxRuleFailsWhenAboveMax() {
        NumericMaxRule rule = new NumericMaxRule(100.0);
        assertThat(rule.validate(38, Map.of(38, "150"), ctx).passed()).isFalse();
    }

    @Test
    void fieldPresentPassesWhenFieldExists() {
        assertThat(new FieldPresentRule().validate(131, Map.of(131, "X"), ctx).passed()).isTrue();
    }

    @Test
    void fieldPresentFailsWhenFieldMissing() {
        assertThat(new FieldPresentRule().validate(131, Map.of(), ctx).passed()).isFalse();
    }

    @Test
    void fieldAbsentPassesWhenFieldMissing() {
        assertThat(new FieldAbsentRule().validate(999, Map.of(), ctx).passed()).isTrue();
    }

    @Test
    void fieldAbsentFailsWhenFieldPresent() {
        assertThat(new FieldAbsentRule().validate(999, Map.of(999, "X"), ctx).passed()).isFalse();
    }

    @Test
    void notEqualsRulePassesWhenValuesDiffer() {
        assertThat(new NotEqualsRule("D", null).validate(35, Map.of(35, "S"), ctx).passed()).isTrue();
    }

    @Test
    void notEqualsRuleFailsWhenValuesMatch() {
        assertThat(new NotEqualsRule("D", null).validate(35, Map.of(35, "D"), ctx).passed()).isFalse();
    }
}
```

Run — expect compile failures.

#### Step 2: ValidationRule + ValidationResult

`fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRule.java`:

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;

import java.util.Map;

public interface ValidationRule {
    ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx);
}
```

`fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationResult.java`:

```java
package com.fixflow.engine.validation;

public record ValidationResult(
    boolean passed,
    int tag,
    String ruleName,
    String expected,
    String actual,
    String message
) {
    public static ValidationResult pass(int tag, String ruleName) {
        return new ValidationResult(true, tag, ruleName, null, null, null);
    }

    public static ValidationResult fail(int tag, String ruleName, String expected, String actual, String message) {
        return new ValidationResult(false, tag, ruleName, expected, actual, message);
    }
}
```

#### Step 3: Rule implementations

`EqualsRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class EqualsRule implements ValidationRule {
    private final String expected;
    private final String refExpression;

    public EqualsRule(String expected, String refExpression) {
        this.expected = expected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String target = expected;
        if (refExpression != null && ctx != null) {
            // ref expressions like "node:n1:tag131" are resolved upstream; here we accept literal
            target = refExpression;
        }
        if (target != null && target.equals(actual)) {
            return ValidationResult.pass(tag, "EQUALS");
        }
        return ValidationResult.fail(tag, "EQUALS", target, actual, "values differ");
    }
}
```

`NotEqualsRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NotEqualsRule implements ValidationRule {
    private final String unexpected;
    private final String refExpression;

    public NotEqualsRule(String unexpected, String refExpression) {
        this.unexpected = unexpected;
        this.refExpression = refExpression;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        String target = refExpression != null ? refExpression : unexpected;
        if (target == null || !target.equals(actual)) {
            return ValidationResult.pass(tag, "NOT_EQUALS");
        }
        return ValidationResult.fail(tag, "NOT_EQUALS", "!= " + target, actual, "values must differ");
    }
}
```

`EnumRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.List;
import java.util.Map;

public final class EnumRule implements ValidationRule {
    private final List<String> allowed;

    public EnumRule(List<String> allowed) {
        this.allowed = List.copyOf(allowed);
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual != null && allowed.contains(actual)) {
            return ValidationResult.pass(tag, "ENUM");
        }
        return ValidationResult.fail(tag, "ENUM", allowed.toString(), actual, "value not in allowed set");
    }
}
```

`RegexRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;
import java.util.regex.Pattern;

public final class RegexRule implements ValidationRule {
    private final Pattern pattern;
    private final String raw;

    public RegexRule(String pattern) {
        this.pattern = Pattern.compile(pattern);
        this.raw = pattern;
    }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual != null && pattern.matcher(actual).matches()) {
            return ValidationResult.pass(tag, "REGEX");
        }
        return ValidationResult.fail(tag, "REGEX", raw, actual, "value does not match pattern");
    }
}
```

`NumericMinRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NumericMinRule implements ValidationRule {
    private final double min;

    public NumericMinRule(double min) { this.min = min; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual == null) {
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, null, "missing");
        }
        try {
            double v = Double.parseDouble(actual);
            if (v >= min) return ValidationResult.pass(tag, "NUMERIC_MIN");
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, actual, "below minimum");
        } catch (NumberFormatException e) {
            return ValidationResult.fail(tag, "NUMERIC_MIN", ">=" + min, actual, "not numeric");
        }
    }
}
```

`NumericMaxRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class NumericMaxRule implements ValidationRule {
    private final double max;

    public NumericMaxRule(double max) { this.max = max; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        String actual = fields.get(tag);
        if (actual == null) {
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, null, "missing");
        }
        try {
            double v = Double.parseDouble(actual);
            if (v <= max) return ValidationResult.pass(tag, "NUMERIC_MAX");
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, actual, "above maximum");
        } catch (NumberFormatException e) {
            return ValidationResult.fail(tag, "NUMERIC_MAX", "<=" + max, actual, "not numeric");
        }
    }
}
```

`FieldPresentRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class FieldPresentRule implements ValidationRule {
    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        if (fields.containsKey(tag)) return ValidationResult.pass(tag, "FIELD_PRESENT");
        return ValidationResult.fail(tag, "FIELD_PRESENT", "present", "absent", "required field missing");
    }
}
```

`FieldAbsentRule.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class FieldAbsentRule implements ValidationRule {
    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        if (!fields.containsKey(tag)) return ValidationResult.pass(tag, "FIELD_ABSENT");
        return ValidationResult.fail(tag, "FIELD_ABSENT", "absent", fields.get(tag), "field must not be present");
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=ValidationRulesTest
```

Expected: all pass.

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/
git commit -m "feat(engine): add ValidationRule interface and 8 built-in rules"
```

---

### Task 21: DateRuleEngine

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/DateRuleEngine.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/DateRule.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/DateRuleType.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/DateRuleValidator.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/DateRuleEngineTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DateRuleEngineTest {

    private final DateRuleEngine engine = new DateRuleEngine();
    private final ExecutionContext ctx = new ExecutionContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @Test
    void currentTimestampPassesWhenWithinTolerance() {
        Instant now = Instant.now();
        DateRule rule = new DateRule("ct", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0, TimeUnit.SECONDS, 4, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(60, now.toString());
        ValidationResult r = engine.validate(rule, 60, fields, ctx, now);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void currentTimestampFailsWhenOutsideTolerance() {
        Instant now = Instant.now();
        Instant tenMinAgo = now.minus(10, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("ct", DateRuleType.CURRENT_TIMESTAMP, null, 0, 0, TimeUnit.SECONDS, 4, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(60, tenMinAgo.toString());
        ValidationResult r = engine.validate(rule, 60, fields, ctx, now);
        assertThat(r.passed()).isFalse();
    }

    @Test
    void fieldOffsetPassesWhenOffsetMatches() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> source = new HashMap<>();
        source.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", source));
        Instant target = base.plus(5, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("fo", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(126, target.toString());
        ValidationResult r = engine.validate(rule, 126, fields, ctx, Instant.now());
        assertThat(r.passed()).isTrue();
    }

    @Test
    void fieldOffsetFailsWhenOffsetTooLarge() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> source = new HashMap<>();
        source.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", source));
        Instant target = base.plus(10, ChronoUnit.MINUTES);
        DateRule rule = new DateRule("fo", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        Map<Integer, String> fields = Map.of(126, target.toString());
        ValidationResult r = engine.validate(rule, 126, fields, ctx, Instant.now());
        assertThat(r.passed()).isFalse();
    }
}
```

#### Step 2: DateRuleType + DateRule

`DateRuleType.java`:

```java
package com.fixflow.engine.validation;

public enum DateRuleType {
    CURRENT_TIMESTAMP,
    FIELD_OFFSET
}
```

`DateRule.java`:

```java
package com.fixflow.engine.validation;

import java.util.concurrent.TimeUnit;

public record DateRule(
    String id,
    DateRuleType type,
    String sourceNode,
    int sourceTag,
    long offsetValue,
    TimeUnit offsetUnit,
    long toleranceValue,
    TimeUnit toleranceUnit
) {}
```

#### Step 3: DateRuleEngine implementation

`DateRuleEngine.java`:

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class DateRuleEngine {

    public ValidationResult validate(
        DateRule rule,
        int tag,
        Map<Integer, String> fields,
        ExecutionContext ctx,
        Instant messageReceivedAt
    ) {
        String raw = fields.get(tag);
        if (raw == null) {
            return ValidationResult.fail(tag, "DATE_RULE:" + rule.type(),
                "datetime", null, "field missing");
        }
        Instant actual;
        try {
            actual = Instant.parse(raw);
        } catch (Exception e) {
            return ValidationResult.fail(tag, "DATE_RULE:" + rule.type(),
                "iso-8601 datetime", raw, "cannot parse");
        }
        return switch (rule.type()) {
            case CURRENT_TIMESTAMP -> validateCurrentTimestamp(rule, tag, actual, messageReceivedAt);
            case FIELD_OFFSET -> validateFieldOffset(rule, tag, actual, ctx);
        };
    }

    private ValidationResult validateCurrentTimestamp(DateRule rule, int tag, Instant actual, Instant receivedAt) {
        long toleranceMs = rule.toleranceUnit().toMillis(rule.toleranceValue());
        long deltaMs = Math.abs(Duration.between(receivedAt, actual).toMillis());
        if (deltaMs <= toleranceMs) {
            return ValidationResult.pass(tag, "DATE_RULE:CURRENT_TIMESTAMP");
        }
        return ValidationResult.fail(tag, "DATE_RULE:CURRENT_TIMESTAMP",
            "within " + toleranceMs + "ms of " + receivedAt,
            actual.toString(),
            "delta=" + deltaMs + "ms");
    }

    private ValidationResult validateFieldOffset(DateRule rule, int tag, Instant actual, ExecutionContext ctx) {
        FIXMessage src = ctx.getNodeMessage(rule.sourceNode());
        if (src == null) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "source node " + rule.sourceNode(), null, "source node not found");
        }
        String srcRaw = src.fields().get(rule.sourceTag());
        if (srcRaw == null) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "tag " + rule.sourceTag() + " on " + rule.sourceNode(),
                null, "source tag missing");
        }
        Instant srcInstant;
        try {
            srcInstant = Instant.parse(srcRaw);
        } catch (Exception e) {
            return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
                "iso-8601", srcRaw, "source not parseable");
        }
        long offsetMs = rule.offsetUnit().toMillis(rule.offsetValue());
        Instant expected = srcInstant.plusMillis(offsetMs);
        long toleranceMs = rule.toleranceUnit().toMillis(rule.toleranceValue());
        long deltaMs = Math.abs(Duration.between(expected, actual).toMillis());
        if (deltaMs <= toleranceMs) {
            return ValidationResult.pass(tag, "DATE_RULE:FIELD_OFFSET");
        }
        return ValidationResult.fail(tag, "DATE_RULE:FIELD_OFFSET",
            "within " + toleranceMs + "ms of " + expected,
            actual.toString(),
            "delta=" + deltaMs + "ms");
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=DateRuleEngineTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/DateRuleEngineTest.java
git commit -m "feat(engine): add DateRuleEngine with CURRENT_TIMESTAMP and FIELD_OFFSET rule types"
```

---

### Task 22: ValidationEngine (orchestrates all rules)

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationEngine.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationConfig.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationRuleConfig.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/validation/ValidationSummary.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationEngineTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationEngineTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());
    private final ExecutionContext ctx = new ExecutionContext(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @Test
    void passesWhenAllRulesPass() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0),
                new ValidationRuleConfig(131, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "S", 131, "QR-1");
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void failsInStrictModeWhenUnexpectedTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0),
                new ValidationRuleConfig(131, "FIELD_PRESENT", null, null, null, null, null, 0)
            ),
            Map.of(),
            true
        );
        Map<Integer, String> fields = Map.of(35, "S", 131, "QR-1", 999, "EXTRA");
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isFalse();
        assertThat(s.results()).anyMatch(r -> !r.passed() && r.tag() == 999);
    }

    @Test
    void passesInNonStrictModeWhenExtraTagPresent() {
        ValidationConfig cfg = new ValidationConfig(
            List.of(
                new ValidationRuleConfig(35, "EQUALS", "S", null, null, null, null, 0)
            ),
            Map.of(),
            false
        );
        Map<Integer, String> fields = Map.of(35, "S", 999, "EXTRA");
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }

    @Test
    void appliesDateRuleFromConfig() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Map<Integer, String> source = new HashMap<>();
        source.put(60, base.toString());
        ctx.storeNodeMessage("n1", new FIXMessage("D", source));
        DateRule fo = new DateRule("fo1", DateRuleType.FIELD_OFFSET, "n1", 60, 5, TimeUnit.MINUTES, 1, TimeUnit.SECONDS);
        ValidationConfig cfg = new ValidationConfig(
            List.of(new ValidationRuleConfig(126, "DATE_RULE", null, null, null, "fo1", null, 0)),
            Map.of("fo1", fo),
            false
        );
        Map<Integer, String> fields = Map.of(126, base.plusSeconds(300).toString());
        ValidationSummary s = engine.validate(cfg, fields, ctx, Instant.now());
        assertThat(s.passed()).isTrue();
    }
}
```

#### Step 2: ValidationConfig + ValidationRuleConfig + ValidationSummary

`ValidationRuleConfig.java`:

```java
package com.fixflow.engine.validation;

import java.util.List;

public record ValidationRuleConfig(
    int tag,
    String rule,
    String value,
    List<String> values,
    String ref,
    String dateRule,
    String pattern,
    double numericValue
) {}
```

`ValidationConfig.java`:

```java
package com.fixflow.engine.validation;

import java.util.List;
import java.util.Map;

public record ValidationConfig(
    List<ValidationRuleConfig> validations,
    Map<String, DateRule> dateRules,
    boolean strictMode
) {}
```

`ValidationSummary.java`:

```java
package com.fixflow.engine.validation;

import java.util.List;

public record ValidationSummary(boolean passed, List<ValidationResult> results) {}
```

#### Step 3: ValidationEngine implementation

```java
package com.fixflow.engine.validation;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.rules.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ValidationEngine {

    private final DateRuleEngine dateRuleEngine;

    public ValidationEngine(DateRuleEngine dateRuleEngine) {
        this.dateRuleEngine = dateRuleEngine;
    }

    public ValidationSummary validate(
        ValidationConfig config,
        Map<Integer, String> fields,
        ExecutionContext ctx,
        Instant receivedAt
    ) {
        List<ValidationResult> results = new ArrayList<>();
        Set<Integer> expectedTags = new HashSet<>();

        for (ValidationRuleConfig rc : config.validations()) {
            expectedTags.add(rc.tag());
            ValidationRule rule = build(rc, config);
            if (rule instanceof DateRuleValidator drv) {
                results.add(dateRuleEngine.validate(drv.rule(), rc.tag(), fields, ctx, receivedAt));
            } else {
                results.add(rule.validate(rc.tag(), fields, ctx));
            }
        }

        if (config.strictMode()) {
            for (Integer tag : fields.keySet()) {
                if (!expectedTags.contains(tag) && !isHeaderTag(tag)) {
                    results.add(ValidationResult.fail(
                        tag, "STRICT", "not present", fields.get(tag), "unexpected field"
                    ));
                }
            }
        }

        boolean passed = results.stream().allMatch(ValidationResult::passed);
        return new ValidationSummary(passed, List.copyOf(results));
    }

    private boolean isHeaderTag(int tag) {
        return tag == 8 || tag == 9 || tag == 10 || tag == 34 || tag == 35
            || tag == 49 || tag == 52 || tag == 56;
    }

    private ValidationRule build(ValidationRuleConfig rc, ValidationConfig cfg) {
        return switch (rc.rule()) {
            case "EQUALS" -> new EqualsRule(rc.value(), rc.ref());
            case "NOT_EQUALS" -> new NotEqualsRule(rc.value(), rc.ref());
            case "ENUM" -> new EnumRule(rc.values() == null ? List.of() : rc.values());
            case "REGEX" -> new RegexRule(rc.pattern() == null ? rc.value() : rc.pattern());
            case "NUMERIC_MIN" -> new NumericMinRule(rc.numericValue());
            case "NUMERIC_MAX" -> new NumericMaxRule(rc.numericValue());
            case "FIELD_PRESENT" -> new FieldPresentRule();
            case "FIELD_ABSENT" -> new FieldAbsentRule();
            case "DATE_RULE" -> {
                DateRule dr = cfg.dateRules().get(rc.dateRule());
                if (dr == null) throw new IllegalArgumentException("Unknown dateRule id: " + rc.dateRule());
                yield new DateRuleValidator(dr);
            }
            default -> throw new IllegalArgumentException("Unknown rule type: " + rc.rule());
        };
    }
}
```

`fix-flow-engine/src/main/java/com/fixflow/engine/validation/rules/DateRuleValidator.java`:

```java
package com.fixflow.engine.validation.rules;

import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.validation.DateRule;
import com.fixflow.engine.validation.ValidationResult;
import com.fixflow.engine.validation.ValidationRule;

import java.util.Map;

public final class DateRuleValidator implements ValidationRule {
    private final DateRule rule;

    public DateRuleValidator(DateRule rule) { this.rule = rule; }

    public DateRule rule() { return rule; }

    @Override
    public ValidationResult validate(int tag, Map<Integer, String> fields, ExecutionContext ctx) {
        throw new UnsupportedOperationException("DateRuleValidator must be dispatched via DateRuleEngine");
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=ValidationEngineTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/validation/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/validation/ValidationEngineTest.java
git commit -m "feat(engine): add ValidationEngine orchestrating rules, date rules, and strict mode"
```

---

### Task 23: ValidateHandler + wire VariableResolver into SendFIXHandler

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ValidateHandler.java`
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/SendFIXHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/ValidateHandlerTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.engine.validation.DateRuleEngine;
import com.fixflow.engine.validation.ValidationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateHandlerTest {

    private final ValidationEngine engine = new ValidationEngine(new DateRuleEngine());
    private final ValidateHandler handler = new ValidateHandler(engine);

    @Test
    void returnsOnSuccessWhenAllRulesPass() {
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("v1", new FIXMessage("8", Map.of(35, "8", 39, "2")));
        ScenarioNode node = new ScenarioNode(
            "v1", NodeType.VALIDATE, "validate",
            Map.of("validations", List.of(
                Map.of("tag", 35, "rule", "EQUALS", "value", "8"),
                Map.of("tag", 39, "rule", "EQUALS", "value", "2")
            )),
            null, null, null, null, null, "next", "fail"
        );
        NodeHandlerResult r = handler.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void returnsOnFailureWhenRuleFails() {
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("v1", new FIXMessage("8", Map.of(35, "8", 39, "1")));
        ScenarioNode node = new ScenarioNode(
            "v1", NodeType.VALIDATE, "validate",
            Map.of("validations", List.of(
                Map.of("tag", 39, "rule", "EQUALS", "value", "2")
            )),
            null, null, null, null, null, "next", "fail"
        );
        NodeHandlerResult r = handler.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("fail");
    }
}
```

#### Step 2: ValidateHandler implementation

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.engine.validation.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ValidateHandler implements NodeHandler {

    private final ValidationEngine engine;

    public ValidateHandler(ValidationEngine engine) { this.engine = engine; }

    @Override
    public NodeType supports() { return NodeType.VALIDATE; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        FIXMessage msg = ctx.getNodeMessage(node.id());
        Map<Integer, String> fields = msg == null ? Map.of() : msg.fields();

        ValidationConfig cfg = toConfig(node.config());
        ValidationSummary summary = engine.validate(cfg, fields, ctx, Instant.now());
        ctx.storeValidationSummary(node.id(), summary);

        return summary.passed()
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "validation failed");
    }

    @SuppressWarnings("unchecked")
    private ValidationConfig toConfig(Map<String, Object> raw) {
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) raw.getOrDefault("validations", List.of());
        List<ValidationRuleConfig> rules = new ArrayList<>();
        for (Map<String, Object> rr : rawRules) {
            int tag = ((Number) rr.get("tag")).intValue();
            String rule = (String) rr.get("rule");
            String value = (String) rr.get("value");
            List<String> values = (List<String>) rr.get("values");
            String ref = (String) rr.get("ref");
            String dateRule = (String) rr.get("dateRule");
            String pattern = (String) rr.get("pattern");
            double num = rr.get("numericValue") == null ? 0 : ((Number) rr.get("numericValue")).doubleValue();
            rules.add(new ValidationRuleConfig(tag, rule, value, values, ref, dateRule, pattern, num));
        }
        boolean strict = Boolean.TRUE.equals(raw.get("strictMode"));
        Map<String, DateRule> dateRules = (Map<String, DateRule>) raw.getOrDefault("dateRules", Map.of());
        return new ValidationConfig(rules, dateRules, strict);
    }
}
```

#### Step 3: Update SendFIXHandler to resolve variables

Modify `SendFIXHandler.java` — inject `VariableResolver` and resolve each field value template:

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.core.ports.FIXSessionPort;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SendFIXHandler implements NodeHandler {

    private final FIXSessionPort sessionPort;
    private final VariableResolver variableResolver;

    public SendFIXHandler(FIXSessionPort sessionPort, VariableResolver variableResolver) {
        this.sessionPort = sessionPort;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType supports() { return NodeType.SEND_FIX; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        Map<String, Object> cfg = node.config();
        String msgType = (String) cfg.get("msgType");
        @SuppressWarnings("unchecked")
        Map<String, Object> rawFields = (Map<String, Object>) cfg.getOrDefault("fields", Map.of());

        Map<Integer, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : rawFields.entrySet()) {
            int tag = Integer.parseInt(e.getKey());
            String template = String.valueOf(e.getValue());
            String value = variableResolver.resolveAll(template, ctx);
            resolved.put(tag, value);
        }

        FIXMessage msg = new FIXMessage(msgType, resolved);
        sessionPort.send(ctx.sessionId(), msg);
        ctx.storeNodeMessage(node.id(), msg);
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=ValidateHandlerTest
mvn test -pl fix-flow-engine -Dtest=SendFIXHandlerTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/ \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/ValidateHandlerTest.java
git commit -m "feat(engine): add ValidateHandler and wire VariableResolver into SendFIXHandler"
```

---

## Phase 7: Advanced Node Types (Tasks 24-26)

---

### Task 24: DecisionHandler + WaitHandler + DelayHandler

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DecisionHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/WaitHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DelayHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/DecisionHandlerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.domain.TimeoutConfig;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHandlerTest {

    private final VariableResolver resolver = new VariableResolver();

    @Test
    void decisionGoesOnSuccessWhenConditionTrue() {
        DecisionHandler h = new DecisionHandler(resolver);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("n1", new FIXMessage("8", Map.of(39, "2")));
        ScenarioNode node = new ScenarioNode("d", NodeType.DECISION, "decide",
            Map.of("condition", "{{node:n1:tag39}} == 2"),
            null, null, null, null, null, "yes", "no");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("yes");
    }

    @Test
    void decisionGoesOnFailureWhenConditionFalse() {
        DecisionHandler h = new DecisionHandler(resolver);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ctx.storeNodeMessage("n1", new FIXMessage("8", Map.of(39, "1")));
        ScenarioNode node = new ScenarioNode("d", NodeType.DECISION, "decide",
            Map.of("condition", "{{node:n1:tag39}} == 2"),
            null, null, null, null, null, "yes", "no");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("no");
    }

    @Test
    void waitBlocksForConfiguredDuration() {
        WaitHandler h = new WaitHandler();
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        TimeoutConfig t = new TimeoutConfig(50, TimeUnit.MILLISECONDS);
        ScenarioNode node = new ScenarioNode("w", NodeType.WAIT, "wait",
            Map.of(), t, null, null, null, null, "next", "fail");
        long start = System.nanoTime();
        NodeHandlerResult r = h.execute(node, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(50L);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }

    @Test
    void delayBlocksForConfiguredMs() {
        DelayHandler h = new DelayHandler();
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("d", NodeType.DELAY, "delay",
            Map.of("delayMs", 50), null, null, null, null, null, "next", "fail");
        long start = System.nanoTime();
        NodeHandlerResult r = h.execute(node, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(50L);
        assertThat(r.nextNodeId()).isEqualTo("next");
    }
}
```

#### Step 2: DecisionHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DecisionHandler implements NodeHandler {

    private static final Pattern COND = Pattern.compile(
        "^\\s*(.+?)\\s*(==|!=|contains)\\s*(.+?)\\s*$"
    );

    private final VariableResolver resolver;

    public DecisionHandler(VariableResolver resolver) { this.resolver = resolver; }

    @Override
    public NodeType supports() { return NodeType.DECISION; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        String condition = (String) node.config().get("condition");
        if (condition == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing condition");
        }
        String resolvedCond = resolver.resolveAll(condition, ctx);
        boolean result = evaluate(resolvedCond);
        return result
            ? NodeHandlerResult.success(node.onSuccess())
            : NodeHandlerResult.failure(node.onFailure(), "condition false");
    }

    private boolean evaluate(String expr) {
        Matcher m = COND.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported condition: " + expr);
        }
        String left = m.group(1).trim();
        String op = m.group(2);
        String right = m.group(3).trim();
        left = unquote(left);
        right = unquote(right);
        return switch (op) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "contains" -> left.contains(right);
            default -> throw new IllegalArgumentException("Unknown operator: " + op);
        };
    }

    private String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
```

#### Step 3: WaitHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.domain.TimeoutConfig;
import com.fixflow.core.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class WaitHandler implements NodeHandler {

    @Override
    public NodeType supports() { return NodeType.WAIT; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        TimeoutConfig t = node.timeout();
        long ms = t == null ? 0L : t.unit().toMillis(t.value());
        try {
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeHandlerResult.failure(node.onFailure(), "wait interrupted");
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 4: DelayHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class DelayHandler implements NodeHandler {

    @Override
    public NodeType supports() { return NodeType.DELAY; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        Object raw = node.config().get("delayMs");
        long ms = raw == null ? 0L : ((Number) raw).longValue();
        try {
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeHandlerResult.failure(node.onFailure(), "delay interrupted");
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 5: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=DecisionHandlerTest
```

#### Step 6: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DecisionHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/WaitHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/DelayHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/DecisionHandlerTest.java
git commit -m "feat(engine): add Decision/Wait/Delay node handlers"
```

---

### Task 25: RetryHandler + LoopHandler

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RetryHandler.java`
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/handlers/LoopHandler.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RetryHandlerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.RetryPolicy;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.NodeDispatcher;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryHandlerTest {

    @Test
    void succeedsBeforeMaxAttempts() {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch(eq("inner"), any())).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            return n < 3 ? NodeHandlerResult.failure("retry", "fail")
                          : NodeHandlerResult.success("after");
        });
        RetryHandler h = new RetryHandler(dispatcher);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("r", NodeType.RETRY, "retry",
            Map.of("targetNodeId", "inner", "delayMs", 1),
            null, new RetryPolicy(3, 1L), null, null, null, "ok", "ko");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void failsWhenExceedsMaxAttempts() {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        when(dispatcher.dispatch(eq("inner"), any()))
            .thenReturn(NodeHandlerResult.failure("retry", "x"));
        RetryHandler h = new RetryHandler(dispatcher);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("r", NodeType.RETRY, "retry",
            Map.of("targetNodeId", "inner", "delayMs", 1),
            null, new RetryPolicy(2, 1L), null, null, null, "ok", "ko");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("ko");
    }

    @Test
    void loopRunsTargetTheConfiguredNumberOfTimes() {
        NodeDispatcher dispatcher = mock(NodeDispatcher.class);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch(eq("body"), any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return NodeHandlerResult.success("body");
        });
        LoopHandler h = new LoopHandler(dispatcher);
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScenarioNode node = new ScenarioNode("l", NodeType.LOOP, "loop",
            Map.of("targetNodeId", "body", "iterations", 4),
            null, null, null, null, null, "done", "fail");
        NodeHandlerResult r = h.execute(node, ctx);
        assertThat(r.nextNodeId()).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(4);
    }
}
```

#### Step 2: RetryHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.RetryPolicy;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.NodeDispatcher;
import org.springframework.stereotype.Component;

@Component
public class RetryHandler implements NodeHandler {

    private final NodeDispatcher dispatcher;

    public RetryHandler(NodeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public NodeType supports() { return NodeType.RETRY; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        RetryPolicy policy = node.retryPolicy() == null
            ? new RetryPolicy(1, 0L)
            : node.retryPolicy();
        long delayMs = node.config().get("delayMs") == null
            ? policy.delayMs()
            : ((Number) node.config().get("delayMs")).longValue();
        int max = policy.maxAttempts();
        NodeHandlerResult last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            last = dispatcher.dispatch(targetId, ctx);
            if (last.success()) {
                return NodeHandlerResult.success(node.onSuccess());
            }
            if (attempt < max && delayMs > 0) {
                try { Thread.sleep(delayMs); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return NodeHandlerResult.failure(node.onFailure(), "interrupted");
                }
            }
        }
        return NodeHandlerResult.failure(node.onFailure(),
            "exhausted retries: " + (last == null ? "no attempts" : last.reason()));
    }
}
```

#### Step 3: LoopHandler

```java
package com.fixflow.engine.handlers;

import com.fixflow.core.domain.NodeType;
import com.fixflow.core.domain.ScenarioNode;
import com.fixflow.core.execution.ExecutionContext;
import com.fixflow.engine.NodeDispatcher;
import org.springframework.stereotype.Component;

@Component
public class LoopHandler implements NodeHandler {

    private final NodeDispatcher dispatcher;

    public LoopHandler(NodeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public NodeType supports() { return NodeType.LOOP; }

    @Override
    public NodeHandlerResult execute(ScenarioNode node, ExecutionContext ctx) {
        String targetId = (String) node.config().get("targetNodeId");
        if (targetId == null) {
            return NodeHandlerResult.failure(node.onFailure(), "missing targetNodeId");
        }
        Object rawIter = node.config().get("iterations");
        int iterations = rawIter == null ? 1 : ((Number) rawIter).intValue();
        for (int i = 0; i < iterations; i++) {
            ctx.setLoopIndex(node.id(), i);
            NodeHandlerResult r = dispatcher.dispatch(targetId, ctx);
            if (!r.success()) {
                return NodeHandlerResult.failure(node.onFailure(),
                    "loop iteration " + i + " failed: " + r.reason());
            }
        }
        return NodeHandlerResult.success(node.onSuccess());
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=RetryHandlerTest
```

#### Step 5: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/handlers/RetryHandler.java \
        fix-flow-engine/src/main/java/com/fixflow/engine/handlers/LoopHandler.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/handlers/RetryHandlerTest.java
git commit -m "feat(engine): add Retry and Loop node handlers with NodeDispatcher delegation"
```

---

### Task 26: HotReloadService

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/fix/HotReloadService.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/fix/HotReloadServiceTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import com.fixflow.engine.MessageBuffer;
import com.fixflow.engine.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HotReloadServiceTest {

    @Test
    void pausesBufferReloadsRegistryThenResumes() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        MessageBuffer buffer = mock(MessageBuffer.class);
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        UUID scenarioId = UUID.randomUUID();
        Scenario latest = mock(Scenario.class);
        when(repo.findById(scenarioId)).thenReturn(Optional.of(latest));

        HotReloadService svc = new HotReloadService(registry, buffer, repo);
        svc.reload(scenarioId);

        var inOrder = inOrder(buffer, registry);
        inOrder.verify(buffer).pause();
        inOrder.verify(registry).reload(latest);
        inOrder.verify(buffer).resume();
    }

    @Test
    void resumesBufferEvenIfReloadThrows() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        MessageBuffer buffer = mock(MessageBuffer.class);
        ScenarioRepositoryPort repo = mock(ScenarioRepositoryPort.class);
        UUID scenarioId = UUID.randomUUID();
        Scenario latest = mock(Scenario.class);
        when(repo.findById(scenarioId)).thenReturn(Optional.of(latest));
        doThrow(new RuntimeException("boom")).when(registry).reload(any());

        HotReloadService svc = new HotReloadService(registry, buffer, repo);
        try { svc.reload(scenarioId); } catch (RuntimeException expected) {}

        verify(buffer).pause();
        verify(buffer).resume();
    }
}
```

#### Step 2: HotReloadService

```java
package com.fixflow.engine.fix;

import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import com.fixflow.engine.MessageBuffer;
import com.fixflow.engine.ScenarioRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HotReloadService {

    private final ScenarioRegistry registry;
    private final MessageBuffer buffer;
    private final ScenarioRepositoryPort scenarioRepo;

    public HotReloadService(ScenarioRegistry registry, MessageBuffer buffer, ScenarioRepositoryPort scenarioRepo) {
        this.registry = registry;
        this.buffer = buffer;
        this.scenarioRepo = scenarioRepo;
    }

    public void reload(UUID scenarioId) {
        buffer.pause();
        try {
            Scenario latest = scenarioRepo.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("scenario not found: " + scenarioId));
            registry.reload(latest);
        } finally {
            buffer.resume();
        }
    }
}
```

#### Step 3: Run tests

```bash
mvn test -pl fix-flow-engine -Dtest=HotReloadServiceTest
```

#### Step 4: Commit

```bash
git add fix-flow-engine/src/main/java/com/fixflow/engine/fix/HotReloadService.java \
        fix-flow-engine/src/test/java/com/fixflow/engine/fix/HotReloadServiceTest.java
git commit -m "feat(engine): add HotReloadService with buffer pause/resume around registry reload"
```

---

## Phase 8: REST API + WebSocket STOMP (Tasks 27-33)

---

### Task 27: Spring Boot app entry point + WebSocket config

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/WebSocketConfig.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/AppConfig.java`
- Create: `fix-flow-api/src/main/resources/application.yml`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/FixFlowApplicationTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FixFlowApplicationTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextLoads() {
        assertThat(ctx).isNotNull();
    }
}
```

#### Step 2: application.yml

```yaml
spring:
  application:
    name: fix-flow
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:h2:file:./data/fixflow;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ''
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

server:
  port: 8080

logging:
  level:
    com.fixflow: DEBUG
```

#### Step 3: FixFlowApplication

```java
package com.fixflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.fixflow")
public class FixFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixFlowApplication.class, args);
    }
}
```

#### Step 4: WebSocketConfig

```java
package com.fixflow.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

#### Step 5: AppConfig

```java
package com.fixflow.api.config;

import com.fixflow.adapters.persistence.ExecutionRepositoryAdapter;
import com.fixflow.adapters.persistence.ScenarioRepositoryAdapter;
import com.fixflow.adapters.quickfix.QuickFIXAdapter;
import com.fixflow.core.ports.ExecutionRepositoryPort;
import com.fixflow.core.ports.FIXSessionPort;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ScenarioRepositoryPort scenarioRepositoryPort(ScenarioRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public ExecutionRepositoryPort executionRepositoryPort(ExecutionRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public FIXSessionPort fixSessionPort(QuickFIXAdapter adapter) {
        return adapter;
    }
}
```

#### Step 6: Run test

```bash
mvn test -pl fix-flow-api -Dtest=FixFlowApplicationTest
```

#### Step 7: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/FixFlowApplication.java \
        fix-flow-api/src/main/java/com/fixflow/api/config/ \
        fix-flow-api/src/main/resources/application.yml \
        fix-flow-api/src/test/java/com/fixflow/api/FixFlowApplicationTest.java
git commit -m "feat(api): bootstrap Spring Boot app with WebSocket STOMP and adapter wiring"
```

---

### Task 28: StompEventPublisher

**Files:**
- Create: `fix-flow-adapters/src/main/java/com/fixflow/adapters/events/StompEventPublisher.java`
- Test: `fix-flow-adapters/src/test/java/com/fixflow/adapters/events/StompEventPublisherTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.adapters.events;

import com.fixflow.core.domain.ExecutionEvent;
import com.fixflow.core.domain.ExecutionEventType;
import com.fixflow.core.fix.FIXMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class StompEventPublisherTest {

    @Test
    void publishesEventToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID execId = UUID.randomUUID();
        ExecutionEvent event = new ExecutionEvent(
            UUID.randomUUID(), execId, ExecutionEventType.NODE_ENTER,
            "n1", "info", Instant.now(), Map.of()
        );

        pub.publish(event);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/events", event);
    }

    @Test
    void publishesFixMessageToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID execId = UUID.randomUUID();
        FIXMessage msg = new FIXMessage("D", Map.of(35, "D"));

        pub.publishFIXMessage(execId, msg);

        verify(messaging).convertAndSend("/topic/executions/" + execId + "/messages", msg);
    }

    @Test
    void publishesSessionStatusToCorrectTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompEventPublisher pub = new StompEventPublisher(messaging);

        UUID sessionId = UUID.randomUUID();
        pub.publishSessionStatus(sessionId, "CONNECTED");

        verify(messaging).convertAndSend(
            eq("/topic/sessions/" + sessionId + "/status"),
            (Object) argThat((Object o) -> o instanceof Map<?,?> m
                && "CONNECTED".equals(m.get("status"))
                && sessionId.equals(m.get("sessionId")))
        );
    }
}
```

#### Step 2: StompEventPublisher

```java
package com.fixflow.adapters.events;

import com.fixflow.core.domain.ExecutionEvent;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.core.ports.EventPublisherPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class StompEventPublisher implements EventPublisherPort {

    private final SimpMessagingTemplate messaging;

    public StompEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(ExecutionEvent event) {
        messaging.convertAndSend(
            "/topic/executions/" + event.executionId() + "/events",
            event
        );
    }

    public void publishFIXMessage(UUID executionId, FIXMessage msg) {
        messaging.convertAndSend(
            "/topic/executions/" + executionId + "/messages",
            msg
        );
    }

    public void publishSessionStatus(UUID sessionId, String status) {
        messaging.convertAndSend(
            "/topic/sessions/" + sessionId + "/status",
            Map.of("sessionId", sessionId, "status", status)
        );
    }
}
```

#### Step 3: Run test

```bash
mvn test -pl fix-flow-adapters -Dtest=StompEventPublisherTest
```

#### Step 4: Commit

```bash
git add fix-flow-adapters/src/main/java/com/fixflow/adapters/events/StompEventPublisher.java \
        fix-flow-adapters/src/test/java/com/fixflow/adapters/events/StompEventPublisherTest.java
git commit -m "feat(adapters): add StompEventPublisher for execution/message/session topics"
```

---

### Task 29: ScenarioController (CRUD + import/export)

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/ScenarioController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ScenarioDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ScenarioRequest.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ValidationErrorDto.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/ScenarioControllerTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ScenarioRepositoryPort repo;

    @Test
    void postCreatesScenario() throws Exception {
        UUID id = UUID.randomUUID();
        Scenario saved = new Scenario(id, "s1", "desc", "1", "sess1",
            "scenario:\n  nodes: []", List.of(), List.of(), Instant.now());
        when(repo.save(any())).thenReturn(saved);

        ScenarioRequest req = new ScenarioRequest("s1", "desc", "sess1", "scenario:\n  nodes: []");
        mvc.perform(post("/api/v1/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("s1"));
    }

    @Test
    void getListsScenarios() throws Exception {
        when(repo.findAll()).thenReturn(List.of(
            new Scenario(UUID.randomUUID(), "a", null, "1", null, "", List.of(), List.of(), Instant.now())
        ));
        mvc.perform(get("/api/v1/scenarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getOneReturnsScenario() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Scenario(id, "x", null, "1", null, "", List.of(), List.of(), Instant.now())
        ));
        mvc.perform(get("/api/v1/scenarios/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/scenarios/" + id))
            .andExpect(status().isNoContent());
    }

    @Test
    void exportReturnsYaml() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Scenario(id, "x", null, "1", null, "scenario: {}", List.of(), List.of(), Instant.now())
        ));
        mvc.perform(get("/api/v1/scenarios/" + id + "/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/x-yaml"))
            .andExpect(content().string("scenario: {}"));
    }
}
```

#### Step 2: DTOs

`ScenarioRequest.java`:

```java
package com.fixflow.api.rest.dto;

public record ScenarioRequest(
    String name,
    String description,
    String sessionRef,
    String yamlDsl
) {}
```

`ScenarioDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.Scenario;

import java.time.Instant;
import java.util.UUID;

public record ScenarioDto(
    UUID id,
    String name,
    String description,
    String version,
    String sessionRef,
    String yamlDsl,
    Instant createdAt
) {
    public static ScenarioDto from(Scenario s) {
        return new ScenarioDto(
            s.id(), s.name(), s.description(), s.version(),
            s.sessionRef(), s.yamlDsl(), s.createdAt()
        );
    }
}
```

`ValidationErrorDto.java`:

```java
package com.fixflow.api.rest.dto;

import java.util.List;

public record ValidationErrorDto(boolean valid, List<String> errors) {}
```

#### Step 3: ScenarioController

```java
package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ScenarioDto;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.api.rest.dto.ValidationErrorDto;
import com.fixflow.core.domain.Scenario;
import com.fixflow.core.ports.ScenarioRepositoryPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioRepositoryPort repo;

    public ScenarioController(ScenarioRepositoryPort repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@RequestBody ScenarioRequest req) {
        Scenario s = new Scenario(
            UUID.randomUUID(), req.name(), req.description(), "1",
            req.sessionRef(), req.yamlDsl(), List.of(), List.of(), Instant.now()
        );
        Scenario saved = repo.save(s);
        return ResponseEntity.status(201).body(ScenarioDto.from(saved));
    }

    @GetMapping
    public List<ScenarioDto> list() {
        return repo.findAll().stream().map(ScenarioDto::from).toList();
    }

    @GetMapping("/{id}")
    public ScenarioDto get(@PathVariable UUID id) {
        return ScenarioDto.from(repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<ScenarioDto> importYaml(@RequestParam("file") MultipartFile file) throws Exception {
        String yaml = new String(file.getBytes());
        Scenario s = new Scenario(
            UUID.randomUUID(), file.getOriginalFilename(), "imported", "1",
            null, yaml, List.of(), List.of(), Instant.now()
        );
        return ResponseEntity.status(201).body(ScenarioDto.from(repo.save(s)));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found"));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/x-yaml"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + s.name() + ".yaml");
        return new ResponseEntity<>(s.yamlDsl(), h, 200);
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationErrorDto> validate(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found"));
        List<String> errors = validateScenario(s);
        if (errors.isEmpty()) {
            return ResponseEntity.ok(new ValidationErrorDto(true, List.of()));
        }
        return ResponseEntity.status(400).body(new ValidationErrorDto(false, errors));
    }

    private List<String> validateScenario(Scenario s) {
        List<String> errs = new ArrayList<>();
        var nodeIds = s.nodes().stream().map(n -> n.id()).toList();
        for (var n : s.nodes()) {
            if (n.onSuccess() != null && !nodeIds.contains(n.onSuccess())) {
                errs.add("node " + n.id() + " references missing onSuccess: " + n.onSuccess());
            }
            if (n.onFailure() != null && !nodeIds.contains(n.onFailure())) {
                errs.add("node " + n.id() + " references missing onFailure: " + n.onFailure());
            }
        }
        return errs;
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=ScenarioControllerTest
```

#### Step 5: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/ScenarioController.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ \
        fix-flow-api/src/test/java/com/fixflow/api/rest/ScenarioControllerTest.java
git commit -m "feat(api): add ScenarioController with CRUD, import/export YAML, validate"
```

---

### Task 30: ExecutionController

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/StartExecutionRequest.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionReportDto.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.domain.Execution;
import com.fixflow.core.domain.ExecutionStatus;
import com.fixflow.core.ports.ExecutionRepositoryPort;
import com.fixflow.engine.ExecutionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ExecutionManager manager;
    @MockBean ExecutionRepositoryPort repo;

    @Test
    void startsExecutionReturns202() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(manager.start(any(), any())).thenReturn(execId);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new StartExecutionRequest(sessionId))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value(execId.toString()));
    }

    @Test
    void stopReturns200() throws Exception {
        UUID execId = UUID.randomUUID();
        mvc.perform(post("/api/v1/executions/" + execId + "/stop"))
            .andExpect(status().isOk());
    }

    @Test
    void getExecutionReturnsDto() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, "n1",
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getEventsReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.RUNNING, Instant.now(), null, null,
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id + "/events"))
            .andExpect(status().isOk());
    }

    @Test
    void getMessagesReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.PASSED, Instant.now(), Instant.now(), null,
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id + "/messages"))
            .andExpect(status().isOk());
    }

    @Test
    void getReportReturnsJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Execution(id, UUID.randomUUID(), "1", UUID.randomUUID(),
                ExecutionStatus.PASSED, Instant.now(), Instant.now(), null,
                List.of(), List.of(), List.of())
        ));
        mvc.perform(get("/api/v1/executions/" + id + "/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.execution.id").value(id.toString()));
    }
}
```

#### Step 2: DTOs

`StartExecutionRequest.java`:

```java
package com.fixflow.api.rest.dto;

import java.util.UUID;

public record StartExecutionRequest(UUID sessionId) {}
```

`ExecutionDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.Execution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionDto(
    UUID id,
    UUID scenarioId,
    String scenarioVersion,
    UUID sessionId,
    String status,
    Instant startTime,
    Instant endTime,
    String currentNodeId
) {
    public static ExecutionDto from(Execution e) {
        return new ExecutionDto(
            e.id(), e.scenarioId(), e.scenarioVersion(), e.sessionId(),
            e.status().name(), e.startTime(), e.endTime(), e.currentNodeId()
        );
    }
}
```

`ExecutionReportDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.Execution;
import com.fixflow.core.domain.ExecutionEvent;
import com.fixflow.core.domain.NodeResult;
import com.fixflow.core.fix.FIXMessage;

import java.util.List;

public record ExecutionReportDto(
    ExecutionDto execution,
    List<ExecutionEvent> events,
    List<FIXMessage> messages,
    List<NodeResult> nodeResults
) {
    public static ExecutionReportDto from(Execution e) {
        return new ExecutionReportDto(
            ExecutionDto.from(e),
            e.events(),
            e.messages(),
            e.nodeResults()
        );
    }
}
```

#### Step 3: ExecutionController

```java
package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ExecutionDto;
import com.fixflow.api.rest.dto.ExecutionReportDto;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.domain.Execution;
import com.fixflow.core.ports.ExecutionRepositoryPort;
import com.fixflow.engine.ExecutionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class ExecutionController {

    private final ExecutionManager manager;
    private final ExecutionRepositoryPort repo;

    public ExecutionController(ExecutionManager manager, ExecutionRepositoryPort repo) {
        this.manager = manager;
        this.repo = repo;
    }

    @PostMapping("/api/v1/scenarios/{scenarioId}/execute")
    public ResponseEntity<Map<String, UUID>> start(
        @PathVariable UUID scenarioId,
        @RequestBody StartExecutionRequest req
    ) {
        UUID execId = manager.start(scenarioId, req.sessionId());
        return ResponseEntity
            .accepted()
            .location(URI.create("/api/v1/executions/" + execId))
            .body(Map.of("executionId", execId));
    }

    @PostMapping("/api/v1/executions/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable UUID id) {
        manager.stop(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/v1/executions/{id}")
    public ExecutionDto get(@PathVariable UUID id) {
        return ExecutionDto.from(load(id));
    }

    @GetMapping("/api/v1/executions/{id}/events")
    public List<?> events(@PathVariable UUID id) {
        return load(id).events();
    }

    @GetMapping("/api/v1/executions/{id}/messages")
    public List<?> messages(@PathVariable UUID id) {
        return load(id).messages();
    }

    @GetMapping("/api/v1/executions/{id}/report")
    public ExecutionReportDto report(@PathVariable UUID id) {
        return ExecutionReportDto.from(load(id));
    }

    private Execution load(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("execution not found: " + id));
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=ExecutionControllerTest
```

#### Step 5: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionDto.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/StartExecutionRequest.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ExecutionReportDto.java \
        fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java
git commit -m "feat(api): add ExecutionController with start/stop/get/events/messages/report"
```

---

### Task 31: SessionController

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/SessionController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionRequest.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/SessionControllerTest.java`

#### Step 1: Write failing tests

```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.FIXSessionConfig;
import com.fixflow.core.ports.FIXSessionPort;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FIXSessionManager manager;
    @MockBean FIXSessionPort port;
    @MockBean HotReloadService hotReload;

    @Test
    void createSessionReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig saved = new FIXSessionConfig(id, "s1", "ACCEPTOR",
            "FIX.4.4", null, "SENDER", "TARGET", "localhost", 9999, 30, 5,
            true, true, Instant.now());
        when(manager.create(any())).thenReturn(saved);

        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR",
            "FIX.4.4", null, "SENDER", "TARGET", "localhost", 9999, 30, 5, true, true);
        mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void putUpdatesSession() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig existing = new FIXSessionConfig(id, "s1", "ACCEPTOR",
            "FIX.4.4", null, "S", "T", "h", 9, 30, 5, true, true, Instant.now());
        when(manager.findById(id)).thenReturn(Optional.of(existing));
        when(manager.isConnected(id)).thenReturn(false);
        when(manager.update(any(), any())).thenReturn(existing);

        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR",
            "FIX.4.4", null, "S", "T", "h", 9, 30, 5, true, true);
        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    @Test
    void connectReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put("/api/v1/sessions/" + id + "/connect"))
            .andExpect(status().isOk());
    }

    @Test
    void statusReturnsConnectedFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(manager.isConnected(id)).thenReturn(true);
        mvc.perform(get("/api/v1/sessions/" + id + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void putWhileConnectedChangingFixVersionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        FIXSessionConfig existing = new FIXSessionConfig(id, "s1", "ACCEPTOR",
            "FIX.4.4", null, "S", "T", "h", 9, 30, 5, true, true, Instant.now());
        when(manager.findById(id)).thenReturn(Optional.of(existing));
        when(manager.isConnected(id)).thenReturn(true);

        FIXSessionRequest req = new FIXSessionRequest("s1", "ACCEPTOR",
            "FIX.5.0", null, "S", "T", "h", 9, 30, 5, true, true);
        mvc.perform(put("/api/v1/sessions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }
}
```

#### Step 2: DTOs

`FIXSessionRequest.java`:

```java
package com.fixflow.api.rest.dto;

public record FIXSessionRequest(
    String name,
    String mode,
    String fixVersion,
    String defaultApplVerID,
    String senderCompID,
    String targetCompID,
    String host,
    int port,
    int heartbeatInterval,
    int reconnectInterval,
    boolean resetOnLogon,
    boolean resetOnLogout
) {}
```

`FIXSessionDto.java`:

```java
package com.fixflow.api.rest.dto;

import com.fixflow.core.domain.FIXSessionConfig;

import java.time.Instant;
import java.util.UUID;

public record FIXSessionDto(
    UUID id,
    String name,
    String mode,
    String fixVersion,
    String defaultApplVerID,
    String senderCompID,
    String targetCompID,
    String host,
    int port,
    int heartbeatInterval,
    int reconnectInterval,
    boolean resetOnLogon,
    boolean resetOnLogout,
    Instant createdAt,
    boolean connected
) {
    public static FIXSessionDto from(FIXSessionConfig c, boolean connected) {
        return new FIXSessionDto(
            c.id(), c.name(), c.mode(), c.fixVersion(), c.defaultApplVerID(),
            c.senderCompID(), c.targetCompID(), c.host(), c.port(),
            c.heartbeatInterval(), c.reconnectInterval(),
            c.resetOnLogon(), c.resetOnLogout(), c.createdAt(), connected
        );
    }
}
```

#### Step 3: SessionController

```java
package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.FIXSessionDto;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.core.domain.FIXSessionConfig;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.fix.HotReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final FIXSessionManager manager;
    private final HotReloadService hotReload;

    public SessionController(FIXSessionManager manager, HotReloadService hotReload) {
        this.manager = manager;
        this.hotReload = hotReload;
    }

    @PostMapping
    public ResponseEntity<FIXSessionDto> create(@RequestBody FIXSessionRequest req) {
        FIXSessionConfig saved = manager.create(req);
        return ResponseEntity.status(201)
            .body(FIXSessionDto.from(saved, false));
    }

    @GetMapping
    public List<FIXSessionDto> list() {
        return manager.findAll().stream()
            .map(c -> FIXSessionDto.from(c, manager.isConnected(c.id())))
            .toList();
    }

    @GetMapping("/{id}")
    public FIXSessionDto get(@PathVariable UUID id) {
        FIXSessionConfig c = manager.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found"));
        return FIXSessionDto.from(c, manager.isConnected(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody FIXSessionRequest req) {
        FIXSessionConfig existing = manager.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found"));
        boolean connected = manager.isConnected(id);
        if (connected && !existing.fixVersion().equals(req.fixVersion())) {
            return ResponseEntity.status(409).body(Map.of(
                "error", "Conflict",
                "message", "Disconnect session before changing FIX version"
            ));
        }
        FIXSessionConfig updated = manager.update(id, req);
        return ResponseEntity.ok(FIXSessionDto.from(updated, connected));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        manager.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/connect")
    public ResponseEntity<Void> connect(@PathVariable UUID id) {
        manager.connect(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/disconnect")
    public ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        manager.disconnect(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable UUID id) {
        return Map.of("sessionId", id, "connected", manager.isConnected(id));
    }

    @PostMapping("/{id}/reload")
    public ResponseEntity<Void> reload(@PathVariable UUID id) {
        hotReload.reload(id);
        return ResponseEntity.ok().build();
    }
}
```

#### Step 4: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=SessionControllerTest
```

#### Step 5: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/rest/SessionController.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionDto.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/FIXSessionRequest.java \
        fix-flow-api/src/test/java/com/fixflow/api/rest/SessionControllerTest.java
git commit -m "feat(api): add SessionController with CRUD, connect/disconnect, status, hot reload"
```

---

### Task 32: GlobalExceptionHandler + CORS config

**Files:**
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/GlobalExceptionHandler.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/config/CorsConfig.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ErrorResponse.java`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/config/GlobalExceptionHandlerTest.java`

#### Step 1: Write failing test

```java
package com.fixflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @Test
    void noSuchElementReturns404() throws Exception {
        mvc.perform(get("/test/notfound"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void illegalArgumentReturns400() throws Exception {
        mvc.perform(get("/test/badarg"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void genericExceptionReturns500() throws Exception {
        mvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/notfound")
        public String notFound() { throw new NoSuchElementException("missing"); }

        @GetMapping("/test/badarg")
        public String bad() { throw new IllegalArgumentException("bad"); }

        @GetMapping("/test/boom")
        public String boom() { throw new RuntimeException("kaboom"); }
    }
}
```

#### Step 2: ErrorResponse

```java
package com.fixflow.api.rest.dto;

import java.time.Instant;

public record ErrorResponse(int status, String error, String message, Instant timestamp) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
```

#### Step 3: GlobalExceptionHandler

```java
package com.fixflow.api.config;

import com.fixflow.api.rest.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(
            ErrorResponse.of(404, "Not Found", ex.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400).body(
            ErrorResponse.of(400, "Bad Request", ex.getMessage())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(500).body(
            ErrorResponse.of(500, "Internal Server Error", ex.getMessage())
        );
    }
}
```

#### Step 4: CorsConfig

```java
package com.fixflow.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173", "http://localhost:8080")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

#### Step 5: Run tests

```bash
mvn test -pl fix-flow-api -Dtest=GlobalExceptionHandlerTest
```

#### Step 6: Commit

```bash
git add fix-flow-api/src/main/java/com/fixflow/api/config/GlobalExceptionHandler.java \
        fix-flow-api/src/main/java/com/fixflow/api/config/CorsConfig.java \
        fix-flow-api/src/main/java/com/fixflow/api/rest/dto/ErrorResponse.java \
        fix-flow-api/src/test/java/com/fixflow/api/config/GlobalExceptionHandlerTest.java
git commit -m "feat(api): add GlobalExceptionHandler with 404/400/500 mapping and CORS config"
```

---

### Task 33: Full API integration test (Spring Boot test)

**Files:**
- Test: `fix-flow-api/src/test/java/com/fixflow/api/FullApiIntegrationTest.java`

#### Step 1: Write the integration test

```java
package com.fixflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.adapters.fake.FakeFixAdapter;
import com.fixflow.api.rest.dto.FIXSessionRequest;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.api.rest.dto.StartExecutionRequest;
import com.fixflow.core.fix.FIXMessage;
import com.fixflow.core.ports.FIXSessionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(FullApiIntegrationTest.TestConfig.class)
class FullApiIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired FakeFixAdapter fakeAdapter;

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        FIXSessionPort fixSessionPort() {
            return new FakeFixAdapter();
        }
        @Bean
        FakeFixAdapter fakeFixAdapter(FIXSessionPort port) {
            return (FakeFixAdapter) port;
        }
    }

    @Test
    void endToEndScenarioExecution() throws Exception {
        // 1. Create session
        FIXSessionRequest sessionReq = new FIXSessionRequest(
            "test-session", "ACCEPTOR", "FIX.4.4", null,
            "SENDER", "TARGET", "localhost", 9999, 30, 5, true, true
        );
        ResponseEntity<JsonNode> sessionResp = rest.postForEntity(
            "/api/v1/sessions", sessionReq, JsonNode.class
        );
        assertThat(sessionResp.getStatusCode().value()).isEqualTo(201);
        UUID sessionId = UUID.fromString(sessionResp.getBody().get("id").asText());

        // 2. Create scenario
        String yaml = """
            scenario:
              name: e2e
              nodes:
                - id: start
                  type: START
                  onSuccess: send
                - id: send
                  type: SEND_FIX
                  config:
                    msgType: D
                    fields: {35: D, 11: ORDER-1}
                  onSuccess: expect
                - id: expect
                  type: EXPECT_FIX
                  config:
                    msgType: 8
                  timeout: {value: 3000, unit: MILLISECONDS}
                  onSuccess: end
                  onFailure: failend
                - id: end
                  type: END
                  config: {status: PASSED}
                - id: failend
                  type: END
                  config: {status: FAILED}
            """;
        ScenarioRequest scenarioReq = new ScenarioRequest("e2e", "test", sessionId.toString(), yaml);
        ResponseEntity<JsonNode> scenarioResp = rest.postForEntity(
            "/api/v1/scenarios", scenarioReq, JsonNode.class
        );
        assertThat(scenarioResp.getStatusCode().value()).isEqualTo(201);
        UUID scenarioId = UUID.fromString(scenarioResp.getBody().get("id").asText());

        // 3. Validate scenario
        ResponseEntity<JsonNode> validateResp = rest.postForEntity(
            "/api/v1/scenarios/" + scenarioId + "/validate", null, JsonNode.class
        );
        assertThat(validateResp.getStatusCode().value()).isEqualTo(200);

        // 4. Connect session
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> connectResp = rest.exchange(
            "/api/v1/sessions/" + sessionId + "/connect",
            HttpMethod.PUT, new HttpEntity<>(headers), Void.class
        );
        assertThat(connectResp.getStatusCode().value()).isEqualTo(200);

        // 5. Start execution
        ResponseEntity<JsonNode> execResp = rest.postForEntity(
            "/api/v1/scenarios/" + scenarioId + "/execute",
            new StartExecutionRequest(sessionId), JsonNode.class
        );
        assertThat(execResp.getStatusCode().value()).isEqualTo(202);
        UUID execId = UUID.fromString(execResp.getBody().get("executionId").asText());

        // 6. Inject inbound FIX message via fake adapter
        fakeAdapter.injectInbound(sessionId,
            new FIXMessage("8", Map.of(35, "8", 11, "ORDER-1", 39, "2", 150, "F"))
        );

        // 7. Poll until PASSED
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<JsonNode> r = rest.getForEntity(
                "/api/v1/executions/" + execId, JsonNode.class
            );
            assertThat(r.getBody().get("status").asText()).isEqualTo("PASSED");
        });

        // 8. Verify messages stored
        ResponseEntity<JsonNode> msgs = rest.getForEntity(
            "/api/v1/executions/" + execId + "/messages", JsonNode.class
        );
        assertThat(msgs.getStatusCode().value()).isEqualTo(200);
        assertThat(msgs.getBody().isArray()).isTrue();
        assertThat(msgs.getBody().size()).isGreaterThanOrEqualTo(1);
    }
}
```

#### Step 2: Add Awaitility dependency

Verify `fix-flow-api/pom.xml` contains:

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <version>4.2.0</version>
  <scope>test</scope>
</dependency>
```

If missing, add it before running.

#### Step 3: Run the test

```bash
mvn test -pl fix-flow-api -Dtest=FullApiIntegrationTest
```

Expected: PASS.

#### Step 4: Commit

```bash
git add fix-flow-api/src/test/java/com/fixflow/api/FullApiIntegrationTest.java \
        fix-flow-api/pom.xml
git commit -m "test(api): add end-to-end integration test covering scenario lifecycle via REST"
```


---


## Phase 10: React UI Shell + Canvas + Node Components (Tasks 34-38)

### Task 34: Vite project scaffold + dependencies

**Files:**
- Create: `fix-flow-ui/package.json`
- Create: `fix-flow-ui/vite.config.ts`
- Create: `fix-flow-ui/tailwind.config.ts`
- Create: `fix-flow-ui/postcss.config.js`
- Create: `fix-flow-ui/tsconfig.json`
- Create: `fix-flow-ui/tsconfig.node.json`
- Create: `fix-flow-ui/index.html`
- Create: `fix-flow-ui/src/main.tsx`
- Create: `fix-flow-ui/src/App.tsx`
- Create: `fix-flow-ui/src/index.css`
- Create: `fix-flow-ui/src/theme/colors.ts`

**Steps:**

1. Create `fix-flow-ui/package.json`:
```json
{
  "name": "fix-flow-ui",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "@xyflow/react": "^12.0.0",
    "zustand": "^4.5.2",
    "@tanstack/react-query": "^5.40.0",
    "@tanstack/react-query-devtools": "^5.40.0",
    "axios": "^1.7.2",
    "@stomp/stompjs": "^7.0.0",
    "sockjs-client": "^1.6.1",
    "react-router-dom": "^6.23.1",
    "react-hook-form": "^7.52.0",
    "js-yaml": "^4.1.0"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@types/sockjs-client": "^1.5.4",
    "@types/js-yaml": "^4.0.9",
    "@vitejs/plugin-react": "^4.3.1",
    "autoprefixer": "^10.4.19",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5",
    "vite": "^5.3.1"
  }
}
```

2. Create `fix-flow-ui/vite.config.ts`:
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    outDir: 'target/dist',
    emptyOutDir: true,
    sourcemap: true,
  },
});
```

3. Create `fix-flow-ui/tailwind.config.ts`:
```typescript
import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          base: '#0f1117',
          panel: '#1a1d27',
          border: '#2a2d3a',
        },
        node: {
          start: '#3b82f6',
          send: '#22c55e',
          expect: '#eab308',
          validate: '#a855f7',
          decision: '#f97316',
          retry: '#06b6d4',
          wait: '#6b7280',
          endPass: '#22c55e',
          endFail: '#ef4444',
        },
        accent: {
          blue: '#3b82f6',
          green: '#22c55e',
          red: '#ef4444',
          amber: '#f59e0b',
        },
      },
    },
  },
  plugins: [],
};

export default config;
```

4. Create `fix-flow-ui/postcss.config.js`:
```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

5. Create `fix-flow-ui/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "useDefineForClassFields": true,
    "noEmit": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

6. Create `fix-flow-ui/tsconfig.node.json`:
```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

7. Create `fix-flow-ui/index.html`:
```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>FIX Flow Simulator</title>
  </head>
  <body class="bg-bg-base">
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

8. Create `fix-flow-ui/src/theme/colors.ts`:
```typescript
export const colors = {
  bgBase: '#0f1117',
  bgPanel: '#1a1d27',
  bgBorder: '#2a2d3a',
  accent: {
    blue: '#3b82f6',
    green: '#22c55e',
    red: '#ef4444',
    amber: '#f59e0b',
    yellow: '#eab308',
    purple: '#a855f7',
    orange: '#f97316',
    cyan: '#06b6d4',
    gray: '#6b7280',
  },
  node: {
    START: '#3b82f6',
    SEND_FIX: '#22c55e',
    EXPECT_FIX: '#eab308',
    VALIDATE: '#a855f7',
    DECISION: '#f97316',
    BRANCH: '#f97316',
    RETRY: '#06b6d4',
    LOOP: '#06b6d4',
    WAIT: '#6b7280',
    DELAY: '#6b7280',
    TIMEOUT: '#6b7280',
    END_PASS: '#22c55e',
    END_FAIL: '#ef4444',
  },
} as const;

export type NodeColorKey = keyof typeof colors.node;
```

9. Create `fix-flow-ui/src/index.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

html, body, #root {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

* { box-sizing: border-box; }

.react-flow__node { font-family: inherit; }
.react-flow__background { background: #0f1117; }
```

10. Create `fix-flow-ui/src/App.tsx` (initial placeholder, replaced in Task 37):
```typescript
export default function App() {
  return (
    <div className="h-screen w-screen flex items-center justify-center bg-bg-base text-gray-100">
      <div className="text-xl">FIX Flow Simulator — boot</div>
    </div>
  );
}
```

11. Create `fix-flow-ui/src/main.tsx`:
```typescript
import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5_000,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>,
);
```

12. Run:
```bash
cd fix-flow-ui && npm install && npm run build
```

13. Commit:
```bash
git add fix-flow-ui
git commit -m "feat(ui): scaffold Vite+React+TS+Tailwind project with dependencies"
```

---

### Task 35: API client + type definitions

**Files:**
- Create: `fix-flow-ui/src/types/index.ts`
- Create: `fix-flow-ui/src/api/client.ts`
- Create: `fix-flow-ui/src/api/scenarios.ts`
- Create: `fix-flow-ui/src/api/executions.ts`
- Create: `fix-flow-ui/src/api/sessions.ts`

**Steps:**

1. Create `fix-flow-ui/src/types/index.ts`:
```typescript
export type NodeType =
  | 'START'
  | 'SEND_FIX'
  | 'EXPECT_FIX'
  | 'VALIDATE'
  | 'WAIT'
  | 'TIMEOUT'
  | 'DECISION'
  | 'BRANCH'
  | 'RETRY'
  | 'LOOP'
  | 'DELAY'
  | 'END_PASS'
  | 'END_FAIL';

export type ExecutionStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'STOPPED';
export type FIXVersion = 'FIX_42' | 'FIX_44' | 'FIXT_11';
export type FIXMode = 'INITIATOR' | 'ACCEPTOR';
export type TimeUnit = 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS';
export type TimeoutAction = 'FAIL' | 'RETRY' | 'CONTINUE' | 'JUMP';

export interface TimeoutConfig {
  value: number;
  unit: TimeUnit;
  onTimeout: TimeoutAction;
  jumpTo?: string;
}

export interface RetryPolicy {
  maxAttempts: number;
  delayMs: number;
}

export interface ScenarioNode {
  id: string;
  name: string;
  type: NodeType;
  config: Record<string, unknown>;
  timeout?: TimeoutConfig;
  retryPolicy?: RetryPolicy;
  onSuccess?: string;
  onFailure?: string;
  onTimeout?: string;
  position?: { x: number; y: number };
}

export interface ScenarioEdge {
  from: string;
  to: string;
  label: string;
}

export interface Scenario {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
  yamlDsl: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScenarioCreateRequest {
  name: string;
  description: string;
  sessionRef: string;
  yamlDsl: string;
}

export interface ScenarioUpdateRequest {
  name?: string;
  description?: string;
  sessionRef?: string;
  yamlDsl: string;
}

export interface FIXSessionConfig {
  id: string;
  name: string;
  mode: FIXMode;
  fixVersion: FIXVersion;
  defaultApplVerID: string;
  senderCompID: string;
  targetCompID: string;
  host: string;
  port: number;
  heartbeatInterval: number;
  reconnectInterval: number;
  resetOnLogon: boolean;
  resetOnLogout: boolean;
  connected: boolean;
}

export interface FIXSessionCreateRequest {
  name: string;
  mode: FIXMode;
  fixVersion: FIXVersion;
  defaultApplVerID: string;
  senderCompID: string;
  targetCompID: string;
  host: string;
  port: number;
  heartbeatInterval: number;
  reconnectInterval: number;
  resetOnLogon: boolean;
  resetOnLogout: boolean;
}

export interface Execution {
  id: string;
  scenarioId: string;
  scenarioVersion: string;
  sessionId: string;
  status: ExecutionStatus;
  startTime: string;
  endTime?: string;
  currentNodeId?: string;
}

export interface ExecutionEvent {
  id: string;
  executionId: string;
  type: string;
  nodeId?: string;
  timestamp: string;
  detail?: string;
  rawFix?: string;
}

export interface FIXMessage {
  id: string;
  executionId: string;
  direction: 'INBOUND' | 'OUTBOUND';
  rawFix: string;
  fields: Record<number, string>;
  receivedAt: string;
}

export interface ValidationError {
  tag: number;
  rule: string;
  expected: string;
  actual: string;
  message?: string;
}

export interface ExecutionReport {
  executionId: string;
  scenarioName: string;
  scenarioVersion: string;
  sessionName: string;
  status: ExecutionStatus;
  startTime: string;
  endTime: string;
  durationMs: number;
  nodeResults: Array<{
    nodeId: string;
    nodeName: string;
    status: string;
    durationMs: number;
  }>;
  rawFIXMessages: string[];
  validationErrors: ValidationError[];
  statistics: Record<string, unknown>;
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
```

2. Create `fix-flow-ui/src/api/client.ts`:
```typescript
import axios, { AxiosInstance, AxiosResponse } from 'axios';

export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
});

apiClient.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response) {
      const data = error.response.data;
      return Promise.reject({
        status: error.response.status,
        code: data?.code ?? 'UNKNOWN',
        message: data?.message ?? error.message,
        details: data?.details,
      });
    }
    return Promise.reject({
      status: 0,
      code: 'NETWORK_ERROR',
      message: error.message,
    });
  },
);

export async function getJson<T>(url: string): Promise<T> {
  const r: AxiosResponse<T> = await apiClient.get(url);
  return r.data;
}

export async function postJson<TReq, TRes>(url: string, body: TReq): Promise<TRes> {
  const r: AxiosResponse<TRes> = await apiClient.post(url, body);
  return r.data;
}

export async function putJson<TReq, TRes>(url: string, body: TReq): Promise<TRes> {
  const r: AxiosResponse<TRes> = await apiClient.put(url, body);
  return r.data;
}

export async function deleteJson(url: string): Promise<void> {
  await apiClient.delete(url);
}
```

3. Create `fix-flow-ui/src/api/scenarios.ts`:
```typescript
import { apiClient, getJson, postJson, putJson, deleteJson } from './client';
import {
  Scenario,
  ScenarioCreateRequest,
  ScenarioUpdateRequest,
  Execution,
} from '../types';

export function getScenarios(): Promise<Scenario[]> {
  return getJson<Scenario[]>('/scenarios');
}

export function getScenario(id: string): Promise<Scenario> {
  return getJson<Scenario>(`/scenarios/${id}`);
}

export function createScenario(req: ScenarioCreateRequest): Promise<Scenario> {
  return postJson<ScenarioCreateRequest, Scenario>('/scenarios', req);
}

export function updateScenario(id: string, req: ScenarioUpdateRequest): Promise<Scenario> {
  return putJson<ScenarioUpdateRequest, Scenario>(`/scenarios/${id}`, req);
}

export function deleteScenario(id: string): Promise<void> {
  return deleteJson(`/scenarios/${id}`);
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

export function validateScenario(id: string): Promise<ValidationResult> {
  return postJson<Record<string, never>, ValidationResult>(`/scenarios/${id}/validate`, {});
}

export async function importScenario(file: File): Promise<Scenario> {
  const form = new FormData();
  form.append('file', file);
  const r = await apiClient.post<Scenario>('/scenarios/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function exportScenario(id: string): Promise<Blob> {
  const r = await apiClient.get(`/scenarios/${id}/export`, { responseType: 'blob' });
  return r.data as Blob;
}

export interface ExecuteRequest {
  sessionId: string;
}

export function executeScenario(id: string, sessionId: string): Promise<Execution> {
  return postJson<ExecuteRequest, Execution>(`/scenarios/${id}/execute`, { sessionId });
}

export function reloadScenario(id: string): Promise<Scenario> {
  return postJson<Record<string, never>, Scenario>(`/scenarios/${id}/reload`, {});
}
```

4. Create `fix-flow-ui/src/api/executions.ts`:
```typescript
import { getJson, postJson } from './client';
import { Execution, ExecutionEvent, FIXMessage, ExecutionReport } from '../types';

export function stopExecution(id: string): Promise<Execution> {
  return postJson<Record<string, never>, Execution>(`/executions/${id}/stop`, {});
}

export function getExecution(id: string): Promise<Execution> {
  return getJson<Execution>(`/executions/${id}`);
}

export function getExecutionEvents(id: string): Promise<ExecutionEvent[]> {
  return getJson<ExecutionEvent[]>(`/executions/${id}/events`);
}

export function getExecutionMessages(id: string): Promise<FIXMessage[]> {
  return getJson<FIXMessage[]>(`/executions/${id}/messages`);
}

export function getExecutionReport(id: string): Promise<ExecutionReport> {
  return getJson<ExecutionReport>(`/executions/${id}/report`);
}

export function getExecutionReportDownloadUrl(id: string): string {
  return `/api/v1/executions/${id}/report/download`;
}
```

5. Create `fix-flow-ui/src/api/sessions.ts`:
```typescript
import { getJson, postJson, putJson, deleteJson } from './client';
import { FIXSessionConfig, FIXSessionCreateRequest } from '../types';

export function getSessions(): Promise<FIXSessionConfig[]> {
  return getJson<FIXSessionConfig[]>('/sessions');
}

export function getSession(id: string): Promise<FIXSessionConfig> {
  return getJson<FIXSessionConfig>(`/sessions/${id}`);
}

export function createSession(req: FIXSessionCreateRequest): Promise<FIXSessionConfig> {
  return postJson<FIXSessionCreateRequest, FIXSessionConfig>('/sessions', req);
}

export function updateSession(
  id: string,
  req: FIXSessionCreateRequest,
): Promise<FIXSessionConfig> {
  return putJson<FIXSessionCreateRequest, FIXSessionConfig>(`/sessions/${id}`, req);
}

export function deleteSession(id: string): Promise<void> {
  return deleteJson(`/sessions/${id}`);
}

export function connectSession(id: string): Promise<FIXSessionConfig> {
  return postJson<Record<string, never>, FIXSessionConfig>(`/sessions/${id}/connect`, {});
}

export function disconnectSession(id: string): Promise<FIXSessionConfig> {
  return postJson<Record<string, never>, FIXSessionConfig>(`/sessions/${id}/disconnect`, {});
}

export interface SessionStatus {
  id: string;
  connected: boolean;
  lastHeartbeat?: string;
  msgSeqNumIn: number;
  msgSeqNumOut: number;
}

export function getSessionStatus(id: string): Promise<SessionStatus> {
  return getJson<SessionStatus>(`/sessions/${id}/status`);
}
```

6. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no TypeScript errors.

7. Commit:
```bash
git add fix-flow-ui/src
git commit -m "feat(ui): add API client, type definitions, and REST functions"
```

---

### Task 36: Zustand stores + WebSocket client

**Files:**
- Create: `fix-flow-ui/src/store/scenarioStore.ts`
- Create: `fix-flow-ui/src/store/executionStore.ts`
- Create: `fix-flow-ui/src/store/sessionStore.ts`
- Create: `fix-flow-ui/src/app/wsClient.ts`

**Steps:**

1. Create `fix-flow-ui/src/store/scenarioStore.ts`:
```typescript
import { create } from 'zustand';
import { Scenario, ScenarioNode, ScenarioEdge } from '../types';

interface ScenarioState {
  scenarios: Scenario[];
  activeScenario: Scenario | null;
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  selectedNodeId: string | null;
  isDirty: boolean;
  setScenarios: (s: Scenario[]) => void;
  setActiveScenario: (s: Scenario | null) => void;
  setNodes: (nodes: ScenarioNode[]) => void;
  setEdges: (edges: ScenarioEdge[]) => void;
  updateNode: (id: string, patch: Partial<ScenarioNode>) => void;
  addNode: (node: ScenarioNode) => void;
  removeNode: (id: string) => void;
  addEdge: (edge: ScenarioEdge) => void;
  removeEdge: (from: string, to: string, label: string) => void;
  setSelectedNode: (id: string | null) => void;
  markDirty: () => void;
  markClean: () => void;
}

export const useScenarioStore = create<ScenarioState>((set) => ({
  scenarios: [],
  activeScenario: null,
  nodes: [],
  edges: [],
  selectedNodeId: null,
  isDirty: false,
  setScenarios: (scenarios) => set({ scenarios }),
  setActiveScenario: (activeScenario) => set({ activeScenario, isDirty: false }),
  setNodes: (nodes) => set({ nodes }),
  setEdges: (edges) => set({ edges }),
  updateNode: (id, patch) =>
    set((s) => ({
      nodes: s.nodes.map((n) => (n.id === id ? { ...n, ...patch } : n)),
      isDirty: true,
    })),
  addNode: (node) => set((s) => ({ nodes: [...s.nodes, node], isDirty: true })),
  removeNode: (id) =>
    set((s) => ({
      nodes: s.nodes.filter((n) => n.id !== id),
      edges: s.edges.filter((e) => e.from !== id && e.to !== id),
      isDirty: true,
    })),
  addEdge: (edge) => set((s) => ({ edges: [...s.edges, edge], isDirty: true })),
  removeEdge: (from, to, label) =>
    set((s) => ({
      edges: s.edges.filter((e) => !(e.from === from && e.to === to && e.label === label)),
      isDirty: true,
    })),
  setSelectedNode: (id) => set({ selectedNodeId: id }),
  markDirty: () => set({ isDirty: true }),
  markClean: () => set({ isDirty: false }),
}));
```

2. Create `fix-flow-ui/src/store/executionStore.ts`:
```typescript
import { create } from 'zustand';
import { ExecutionEvent, ExecutionStatus, FIXMessage } from '../types';

export type NodeRuntimeStatus = 'idle' | 'running' | 'passed' | 'failed';

interface ExecutionState {
  activeExecutionId: string | null;
  executionStatus: ExecutionStatus | 'IDLE';
  events: ExecutionEvent[];
  messages: FIXMessage[];
  nodeStatuses: Record<string, NodeRuntimeStatus>;
  startedAt: string | null;
  endedAt: string | null;
  setActiveExecution: (id: string | null) => void;
  updateStatus: (status: ExecutionStatus | 'IDLE') => void;
  addEvent: (event: ExecutionEvent) => void;
  addMessage: (msg: FIXMessage) => void;
  setNodeStatus: (nodeId: string, status: NodeRuntimeStatus) => void;
  setStartedAt: (iso: string) => void;
  setEndedAt: (iso: string) => void;
  reset: () => void;
}

export const useExecutionStore = create<ExecutionState>((set) => ({
  activeExecutionId: null,
  executionStatus: 'IDLE',
  events: [],
  messages: [],
  nodeStatuses: {},
  startedAt: null,
  endedAt: null,
  setActiveExecution: (id) => set({ activeExecutionId: id }),
  updateStatus: (status) => set({ executionStatus: status }),
  addEvent: (event) => set((s) => ({ events: [...s.events, event] })),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  setNodeStatus: (nodeId, status) =>
    set((s) => ({ nodeStatuses: { ...s.nodeStatuses, [nodeId]: status } })),
  setStartedAt: (iso) => set({ startedAt: iso }),
  setEndedAt: (iso) => set({ endedAt: iso }),
  reset: () =>
    set({
      activeExecutionId: null,
      executionStatus: 'IDLE',
      events: [],
      messages: [],
      nodeStatuses: {},
      startedAt: null,
      endedAt: null,
    }),
}));
```

3. Create `fix-flow-ui/src/store/sessionStore.ts`:
```typescript
import { create } from 'zustand';
import { FIXSessionConfig } from '../types';

interface SessionState {
  sessions: FIXSessionConfig[];
  activeSession: FIXSessionConfig | null;
  setSessions: (s: FIXSessionConfig[]) => void;
  setActiveSession: (s: FIXSessionConfig | null) => void;
  updateSession: (id: string, patch: Partial<FIXSessionConfig>) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  sessions: [],
  activeSession: null,
  setSessions: (sessions) => set({ sessions }),
  setActiveSession: (activeSession) => set({ activeSession }),
  updateSession: (id, patch) =>
    set((s) => ({
      sessions: s.sessions.map((sess) => (sess.id === id ? { ...sess, ...patch } : sess)),
      activeSession:
        s.activeSession?.id === id ? { ...s.activeSession, ...patch } : s.activeSession,
    })),
}));
```

4. Create `fix-flow-ui/src/app/wsClient.ts`:
```typescript
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ExecutionEvent, FIXMessage } from '../types';

export interface SessionStatusEvent {
  sessionId: string;
  status: 'UP' | 'DOWN' | 'LOGON' | 'LOGOUT';
  timestamp: string;
}

class WsClient {
  private client: Client | null = null;
  private connectPromise: Promise<void> | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();

  connect(): Promise<void> {
    if (this.connectPromise) return this.connectPromise;
    this.connectPromise = new Promise((resolve, reject) => {
      this.client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5_000,
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        debug: () => undefined,
        onConnect: () => resolve(),
        onStompError: (frame) => reject(new Error(frame.headers['message'] ?? 'STOMP error')),
        onWebSocketError: (err) => reject(err),
      });
      this.client.activate();
    });
    return this.connectPromise;
  }

  async subscribeExecution(
    executionId: string,
    onEvent: (event: ExecutionEvent) => void,
    onMessage: (msg: FIXMessage) => void,
  ): Promise<() => void> {
    await this.connect();
    if (!this.client) throw new Error('STOMP client not initialised');
    const eventsKey = `events:${executionId}`;
    const messagesKey = `messages:${executionId}`;
    const eventsSub = this.client.subscribe(
      `/topic/executions/${executionId}/events`,
      (frame: IMessage) => onEvent(JSON.parse(frame.body) as ExecutionEvent),
    );
    const messagesSub = this.client.subscribe(
      `/topic/executions/${executionId}/messages`,
      (frame: IMessage) => onMessage(JSON.parse(frame.body) as FIXMessage),
    );
    this.subscriptions.set(eventsKey, eventsSub);
    this.subscriptions.set(messagesKey, messagesSub);
    return () => {
      eventsSub.unsubscribe();
      messagesSub.unsubscribe();
      this.subscriptions.delete(eventsKey);
      this.subscriptions.delete(messagesKey);
    };
  }

  async subscribeSession(
    sessionId: string,
    onStatus: (status: SessionStatusEvent) => void,
  ): Promise<() => void> {
    await this.connect();
    if (!this.client) throw new Error('STOMP client not initialised');
    const key = `session:${sessionId}`;
    const sub = this.client.subscribe(
      `/topic/sessions/${sessionId}/status`,
      (frame: IMessage) => onStatus(JSON.parse(frame.body) as SessionStatusEvent),
    );
    this.subscriptions.set(key, sub);
    return () => {
      sub.unsubscribe();
      this.subscriptions.delete(key);
    };
  }

  disconnect(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions.clear();
    if (this.client) this.client.deactivate();
    this.client = null;
    this.connectPromise = null;
  }
}

export const wsClient = new WsClient();
```

5. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no errors.

6. Commit:
```bash
git add fix-flow-ui/src/store fix-flow-ui/src/app
git commit -m "feat(ui): add Zustand stores and STOMP/SockJS WebSocket client"
```

---

### Task 37: App layout + TopBar + FlowCanvas skeleton

**Files:**
- Modify: `fix-flow-ui/src/App.tsx`
- Create: `fix-flow-ui/src/components/TopBar.tsx`
- Create: `fix-flow-ui/src/canvas/FlowCanvas.tsx`
- Create: `fix-flow-ui/src/canvas/CanvasToolbar.tsx`
- Create: `fix-flow-ui/src/canvas/edges/FlowEdge.tsx`
- Create: `fix-flow-ui/src/panels/left/LeftPanel.tsx` (stub)
- Create: `fix-flow-ui/src/panels/right/RightPanel.tsx` (stub)
- Create: `fix-flow-ui/src/panels/bottom/BottomPanel.tsx` (stub)

**Steps:**

1. Replace `fix-flow-ui/src/App.tsx`:
```typescript
import TopBar from './components/TopBar';
import FlowCanvas from './canvas/FlowCanvas';
import LeftPanel from './panels/left/LeftPanel';
import RightPanel from './panels/right/RightPanel';
import BottomPanel from './panels/bottom/BottomPanel';

export default function App() {
  return (
    <div className="h-screen flex flex-col bg-[#0f1117] text-gray-100">
      <TopBar />
      <div
        className="flex-1 overflow-hidden"
        style={{
          display: 'grid',
          gridTemplateColumns: '240px 1fr 320px',
          gridTemplateRows: '1fr',
        }}
      >
        <LeftPanel />
        <FlowCanvas />
        <RightPanel />
      </div>
      <BottomPanel />
    </div>
  );
}
```

2. Create `fix-flow-ui/src/components/TopBar.tsx`:
```typescript
import { useMutation } from '@tanstack/react-query';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { executeScenario, validateScenario, updateScenario } from '../api/scenarios';
import { stopExecution } from '../api/executions';
import { serializeToYaml } from '../lib/scenarioSerializer';

export default function TopBar() {
  const activeScenario = useScenarioStore((s) => s.activeScenario);
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const isDirty = useScenarioStore((s) => s.isDirty);
  const markClean = useScenarioStore((s) => s.markClean);
  const activeSession = useSessionStore((s) => s.activeSession);
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  const executionStatus = useExecutionStore((s) => s.executionStatus);
  const setActiveExecution = useExecutionStore((s) => s.setActiveExecution);
  const updateStatus = useExecutionStore((s) => s.updateStatus);

  const runMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario || !activeSession) throw new Error('No scenario or session');
      return executeScenario(activeScenario.id, activeSession.id);
    },
    onSuccess: (exec) => {
      setActiveExecution(exec.id);
      updateStatus('RUNNING');
    },
  });

  const stopMutation = useMutation({
    mutationFn: async () => {
      if (!activeExecutionId) throw new Error('No active execution');
      return stopExecution(activeExecutionId);
    },
    onSuccess: () => updateStatus('STOPPED'),
  });

  const validateMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario) throw new Error('No active scenario');
      return validateScenario(activeScenario.id);
    },
    onSuccess: (res) => {
      if (res.valid) alert('Scenario is valid');
      else alert(`Validation errors:\n${res.errors.join('\n')}`);
    },
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario) throw new Error('No active scenario');
      const yaml = serializeToYaml(nodes, edges, {
        id: activeScenario.id,
        name: activeScenario.name,
        description: activeScenario.description,
        version: activeScenario.version,
        sessionRef: activeScenario.sessionRef,
      });
      return updateScenario(activeScenario.id, {
        name: activeScenario.name,
        description: activeScenario.description,
        sessionRef: activeScenario.sessionRef,
        yamlDsl: yaml,
      });
    },
    onSuccess: () => markClean(),
  });

  const isRunning = executionStatus === 'RUNNING';

  return (
    <div className="h-12 bg-[#1a1d27] border-b border-[#2a2d3a] flex items-center px-4 gap-4">
      <div className="font-semibold text-blue-400">FIX Flow Simulator</div>
      <div className="text-sm text-gray-400">{activeScenario?.name ?? 'No scenario loaded'}</div>
      <div className="flex-1" />
      <button
        className="px-3 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40 text-sm"
        onClick={() => runMutation.mutate()}
        disabled={!activeScenario || !activeSession || isRunning}
      >
        Run
      </button>
      <button
        className="px-3 py-1 rounded bg-red-600 hover:bg-red-500 disabled:opacity-40 text-sm"
        onClick={() => stopMutation.mutate()}
        disabled={!isRunning}
      >
        Stop
      </button>
      <button
        className="px-3 py-1 rounded bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-sm"
        onClick={() => validateMutation.mutate()}
        disabled={!activeScenario}
      >
        Validate
      </button>
      <button
        className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        onClick={() => saveMutation.mutate()}
        disabled={!activeScenario}
      >
        Save{isDirty ? ' •' : ''}
      </button>
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Import</button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Export</button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Settings</button>
    </div>
  );
}
```

3. Create `fix-flow-ui/src/canvas/edges/FlowEdge.tsx`:
```typescript
import { BaseEdge, EdgeProps, getBezierPath } from '@xyflow/react';

export function FlowEdge(props: EdgeProps) {
  const { sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, label } = props;
  const [path] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  const labelStr = String(label ?? '');
  const color =
    labelStr === 'success'
      ? '#22c55e'
      : labelStr === 'failure'
        ? '#ef4444'
        : labelStr === 'timeout'
          ? '#f59e0b'
          : '#6b7280';
  return <BaseEdge id={props.id} path={path} style={{ stroke: color, strokeWidth: 2 }} />;
}
```

4. Create `fix-flow-ui/src/canvas/CanvasToolbar.tsx`:
```typescript
import { useReactFlow } from '@xyflow/react';

export function CanvasToolbar() {
  const { zoomIn, zoomOut, fitView } = useReactFlow();
  return (
    <div className="absolute top-2 right-2 z-10 flex gap-1 bg-[#1a1d27] border border-[#2a2d3a] rounded p-1">
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]"
        onClick={() => zoomIn()}
      >
        +
      </button>
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]"
        onClick={() => zoomOut()}
      >
        -
      </button>
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]"
        onClick={() => fitView()}
      >
        Fit
      </button>
    </div>
  );
}
```

5. Create `fix-flow-ui/src/canvas/FlowCanvas.tsx`:
```typescript
import { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  Connection,
  Node,
  Edge,
  useReactFlow,
  applyNodeChanges,
  applyEdgeChanges,
  NodeChange,
  EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useScenarioStore } from '../store/scenarioStore';
import { useExecutionStore } from '../store/executionStore';
import { CanvasToolbar } from './CanvasToolbar';
import { FlowEdge } from './edges/FlowEdge';
import { nodeTypes } from './nodes/nodeTypes';
import { NodeType, ScenarioNode } from '../types';

const edgeTypes = { default: FlowEdge };

function InnerCanvas() {
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const addNode = useScenarioStore((s) => s.addNode);
  const addEdge = useScenarioStore((s) => s.addEdge);
  const setSelectedNode = useScenarioStore((s) => s.setSelectedNode);
  const nodeStatuses = useExecutionStore((s) => s.nodeStatuses);
  const { screenToFlowPosition } = useReactFlow();

  const rfNodes: Node[] = useMemo(
    () =>
      nodes.map((n) => ({
        id: n.id,
        type: n.type,
        position: n.position ?? { x: 100, y: 100 },
        data: {
          label: n.name,
          config: n.config,
          status: nodeStatuses[n.id] ?? 'idle',
        },
      })),
    [nodes, nodeStatuses],
  );

  const rfEdges: Edge[] = useMemo(
    () =>
      edges.map((e, i) => ({
        id: `e${i}-${e.from}-${e.to}-${e.label}`,
        source: e.from,
        target: e.to,
        label: e.label,
        type: 'default',
      })),
    [edges],
  );

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      const updated = applyNodeChanges(changes, rfNodes);
      setNodes(
        updated.map((rn) => {
          const orig = nodes.find((n) => n.id === rn.id);
          return {
            ...(orig as ScenarioNode),
            position: rn.position,
          };
        }),
      );
    },
    [rfNodes, nodes, setNodes],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      const updated = applyEdgeChanges(changes, rfEdges);
      setEdges(
        updated.map((e) => ({
          from: e.source,
          to: e.target,
          label: String(e.label ?? 'success'),
        })),
      );
    },
    [rfEdges, setEdges],
  );

  const onConnect = useCallback(
    (conn: Connection) => {
      if (conn.source && conn.target) {
        addEdge({ from: conn.source, to: conn.target, label: 'success' });
      }
    },
    [addEdge],
  );

  const onDragOver = useCallback((evt: React.DragEvent) => {
    evt.preventDefault();
    evt.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (evt: React.DragEvent) => {
      evt.preventDefault();
      const type = evt.dataTransfer.getData('application/fix-flow-node-type') as NodeType;
      if (!type) return;
      const pos = screenToFlowPosition({ x: evt.clientX, y: evt.clientY });
      const id = `node-${Date.now()}`;
      addNode({
        id,
        name: type,
        type,
        config: {},
        position: pos,
      });
    },
    [addNode, screenToFlowPosition],
  );

  return (
    <div className="relative w-full h-full" onDrop={onDrop} onDragOver={onDragOver}>
      <ReactFlow
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={(_, n) => setSelectedNode(n.id)}
        onPaneClick={() => setSelectedNode(null)}
        fitView
      >
        <Background color="#2a2d3a" gap={20} />
        <Controls className="!bg-[#1a1d27] !border-[#2a2d3a]" />
      </ReactFlow>
      <CanvasToolbar />
    </div>
  );
}

export default function FlowCanvas() {
  return (
    <div className="w-full h-full bg-[#0f1117]">
      <ReactFlowProvider>
        <InnerCanvas />
      </ReactFlowProvider>
    </div>
  );
}
```

6. Create stub `fix-flow-ui/src/panels/left/LeftPanel.tsx`:
```typescript
export default function LeftPanel() {
  return (
    <div className="bg-[#1a1d27] border-r border-[#2a2d3a] p-2 text-sm text-gray-400">
      Left Panel
    </div>
  );
}
```

7. Create stub `fix-flow-ui/src/panels/right/RightPanel.tsx`:
```typescript
export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] p-2 text-sm text-gray-400">
      Right Panel
    </div>
  );
}
```

8. Create stub `fix-flow-ui/src/panels/bottom/BottomPanel.tsx`:
```typescript
export default function BottomPanel() {
  return (
    <div className="h-48 bg-[#1a1d27] border-t border-[#2a2d3a] p-2 text-sm text-gray-400">
      Bottom Panel
    </div>
  );
}
```

9. Create empty placeholder `fix-flow-ui/src/lib/scenarioSerializer.ts` (full impl in Task 45):
```typescript
import { ScenarioNode, ScenarioEdge } from '../types';

export interface ScenarioMeta {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
}

export function serializeToYaml(
  _nodes: ScenarioNode[],
  _edges: ScenarioEdge[],
  _meta: ScenarioMeta,
): string {
  return '';
}

export function parseFromYaml(_yaml: string): {
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  meta: ScenarioMeta;
} {
  return {
    nodes: [],
    edges: [],
    meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
  };
}
```

10. Create empty placeholder `fix-flow-ui/src/canvas/nodes/nodeTypes.ts` (full impl in Task 38):
```typescript
import { NodeTypes } from '@xyflow/react';

export const nodeTypes: NodeTypes = {};
```

11. Run:
```bash
cd fix-flow-ui && npm run dev
```
Open http://localhost:5173 — verify dark background and top bar render.

12. Commit:
```bash
git add fix-flow-ui/src
git commit -m "feat(ui): add App layout, TopBar, FlowCanvas skeleton with edges"
```

---

### Task 38: Custom node components

**Files:**
- Create: `fix-flow-ui/src/canvas/nodes/BaseNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/StartNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/SendFIXNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/ExpectFIXNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/ValidateNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/DecisionNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/EndPassNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/EndFailNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/RetryNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/WaitNode.tsx`
- Modify: `fix-flow-ui/src/canvas/nodes/nodeTypes.ts`

**Steps:**

1. Create `fix-flow-ui/src/canvas/nodes/BaseNode.tsx`:
```typescript
import { Handle, Position } from '@xyflow/react';
import { ReactNode } from 'react';

interface Props {
  label: string;
  type: string;
  borderColor: string;
  selected?: boolean;
  status?: string;
  filled?: boolean;
  shape?: 'rect' | 'diamond' | 'circle';
  children?: ReactNode;
  handles?: { target: boolean; source: boolean };
}

export function BaseNode({
  label,
  type,
  borderColor,
  selected,
  status,
  filled,
  shape = 'rect',
  children,
  handles = { target: true, source: true },
}: Props) {
  const ringColor =
    status === 'running'
      ? 'animate-pulse ring-2 ring-green-400'
      : status === 'passed'
        ? 'ring-2 ring-green-500'
        : status === 'failed'
          ? 'ring-2 ring-red-500'
          : selected
            ? 'ring-2 ring-blue-400'
            : '';

  const bg = filled ? borderColor : '#1a1d27';
  const textColor = filled ? 'text-white' : 'text-gray-100';
  const isDiamond = shape === 'diamond';
  const isCircle = shape === 'circle';

  return (
    <div
      className={`relative ${isDiamond ? 'rotate-45' : ''} ${isCircle ? 'rounded-full' : 'rounded'} ${ringColor}`}
      style={{
        background: bg,
        border: `2px solid ${borderColor}`,
        minWidth: isCircle ? 60 : 160,
        minHeight: isCircle ? 60 : 60,
        padding: isDiamond ? 16 : 8,
      }}
    >
      {handles.target && <Handle type="target" position={Position.Top} />}
      <div className={`${isDiamond ? '-rotate-45' : ''} ${textColor}`}>
        <div className="text-[10px] uppercase tracking-wide opacity-70">{type}</div>
        <div className="text-sm font-medium truncate" title={label}>
          {label}
        </div>
        {children && <div className="mt-1 text-xs">{children}</div>}
      </div>
      {handles.source && <Handle type="source" position={Position.Bottom} />}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/canvas/nodes/StartNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function StartNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="START"
      borderColor="#3b82f6"
      selected={selected}
      status={data.status as string}
      shape="circle"
      handles={{ target: false, source: true }}
    />
  );
}
```

3. Create `fix-flow-ui/src/canvas/nodes/SendFIXNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function SendFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="SEND_FIX"
      borderColor="#22c55e"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        MsgType: <span className="text-green-400">{cfg.msgType ?? '?'}</span>
      </div>
    </BaseNode>
  );
}
```

4. Create `fix-flow-ui/src/canvas/nodes/ExpectFIXNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function ExpectFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="EXPECT_FIX"
      borderColor="#eab308"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        MsgType: <span className="text-yellow-400">{cfg.msgType ?? '?'}</span>
      </div>
    </BaseNode>
  );
}
```

5. Create `fix-flow-ui/src/canvas/nodes/ValidateNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function ValidateNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { rules?: unknown[] }) ?? {};
  const count = Array.isArray(cfg.rules) ? cfg.rules.length : 0;
  return (
    <BaseNode
      label={data.label as string}
      type="VALIDATE"
      borderColor="#a855f7"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        Rules: <span className="text-purple-400">{count}</span>
      </div>
    </BaseNode>
  );
}
```

6. Create `fix-flow-ui/src/canvas/nodes/DecisionNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function DecisionNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="DECISION"
      borderColor="#f97316"
      selected={selected}
      status={data.status as string}
      shape="diamond"
    />
  );
}
```

7. Create `fix-flow-ui/src/canvas/nodes/EndPassNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function EndPassNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="END_PASS"
      borderColor="#22c55e"
      selected={selected}
      status={data.status as string}
      filled
      handles={{ target: true, source: false }}
    />
  );
}
```

8. Create `fix-flow-ui/src/canvas/nodes/EndFailNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function EndFailNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="END_FAIL"
      borderColor="#ef4444"
      selected={selected}
      status={data.status as string}
      filled
      handles={{ target: true, source: false }}
    />
  );
}
```

9. Create `fix-flow-ui/src/canvas/nodes/RetryNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function RetryNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { maxAttempts?: number }) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="RETRY"
      borderColor="#06b6d4"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        Max attempts: <span className="text-cyan-400">{cfg.maxAttempts ?? '?'}</span>
      </div>
    </BaseNode>
  );
}
```

10. Create `fix-flow-ui/src/canvas/nodes/WaitNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function WaitNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { value?: number; unit?: string }) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="WAIT"
      borderColor="#6b7280"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        {cfg.value ?? '?'} {cfg.unit ?? ''}
      </div>
    </BaseNode>
  );
}
```

11. Replace `fix-flow-ui/src/canvas/nodes/nodeTypes.ts`:
```typescript
import { NodeTypes } from '@xyflow/react';
import { StartNode } from './StartNode';
import { SendFIXNode } from './SendFIXNode';
import { ExpectFIXNode } from './ExpectFIXNode';
import { ValidateNode } from './ValidateNode';
import { DecisionNode } from './DecisionNode';
import { EndPassNode } from './EndPassNode';
import { EndFailNode } from './EndFailNode';
import { RetryNode } from './RetryNode';
import { WaitNode } from './WaitNode';

export const nodeTypes: NodeTypes = {
  START: StartNode,
  SEND_FIX: SendFIXNode,
  EXPECT_FIX: ExpectFIXNode,
  VALIDATE: ValidateNode,
  DECISION: DecisionNode,
  BRANCH: DecisionNode,
  END_PASS: EndPassNode,
  END_FAIL: EndFailNode,
  RETRY: RetryNode,
  LOOP: RetryNode,
  WAIT: WaitNode,
  DELAY: WaitNode,
  TIMEOUT: WaitNode,
};
```

12. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no errors.

13. Commit:
```bash
git add fix-flow-ui/src/canvas/nodes
git commit -m "feat(ui): add custom ReactFlow node components for all node types"
```

---

## Phase 11: Left Panel + Runtime Panel (Tasks 39-41)

### Task 39: NodePalette + ScenarioList

**Files:**
- Create: `fix-flow-ui/src/panels/left/NodePalette.tsx`
- Create: `fix-flow-ui/src/panels/left/ScenarioList.tsx`
- Modify: `fix-flow-ui/src/panels/left/LeftPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/left/NodePalette.tsx`:
```typescript
import { NodeType } from '../../types';
import { colors } from '../../theme/colors';

interface PaletteItem {
  type: NodeType;
  label: string;
}

const GROUPS: Array<{ title: string; items: PaletteItem[] }> = [
  {
    title: 'Messages',
    items: [
      { type: 'SEND_FIX', label: 'Send FIX' },
      { type: 'EXPECT_FIX', label: 'Expect FIX' },
      { type: 'VALIDATE', label: 'Validate' },
    ],
  },
  {
    title: 'Flow Control',
    items: [
      { type: 'DECISION', label: 'Decision' },
      { type: 'RETRY', label: 'Retry' },
      { type: 'LOOP', label: 'Loop' },
      { type: 'WAIT', label: 'Wait' },
      { type: 'DELAY', label: 'Delay' },
    ],
  },
  {
    title: 'Terminals',
    items: [
      { type: 'START', label: 'Start' },
      { type: 'END_PASS', label: 'End Pass' },
      { type: 'END_FAIL', label: 'End Fail' },
    ],
  },
];

export function NodePalette() {
  const onDragStart = (evt: React.DragEvent, type: NodeType) => {
    evt.dataTransfer.setData('application/fix-flow-node-type', type);
    evt.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="p-2 overflow-y-auto">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Palette</div>
      {GROUPS.map((g) => (
        <div key={g.title} className="mb-3">
          <div className="text-[10px] uppercase text-gray-400 mb-1">{g.title}</div>
          <div className="flex flex-col gap-1">
            {g.items.map((it) => (
              <div
                key={it.type}
                draggable
                onDragStart={(e) => onDragStart(e, it.type)}
                className="px-2 py-1 rounded cursor-grab bg-[#0f1117] border text-xs hover:bg-[#22252f]"
                style={{ borderColor: colors.node[it.type] }}
              >
                {it.label}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/panels/left/ScenarioList.tsx`:
```typescript
import { useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getScenarios, createScenario } from '../../api/scenarios';
import { useScenarioStore } from '../../store/scenarioStore';
import { parseFromYaml } from '../../lib/scenarioSerializer';
import { Scenario } from '../../types';

const EMPTY_YAML = `id: new-scenario
name: New Scenario
description: ''
version: '1.0'
sessionRef: default
nodes: []
edges: []
`;

export function ScenarioList() {
  const queryClient = useQueryClient();
  const setScenarios = useScenarioStore((s) => s.setScenarios);
  const setActiveScenario = useScenarioStore((s) => s.setActiveScenario);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const activeScenario = useScenarioStore((s) => s.activeScenario);

  const { data } = useQuery({
    queryKey: ['scenarios'],
    queryFn: getScenarios,
  });

  useEffect(() => {
    if (data) setScenarios(data);
  }, [data, setScenarios]);

  const createMutation = useMutation({
    mutationFn: () =>
      createScenario({
        name: 'New Scenario',
        description: '',
        sessionRef: 'default',
        yamlDsl: EMPTY_YAML,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  });

  const onSelect = (s: Scenario) => {
    setActiveScenario(s);
    try {
      const parsed = parseFromYaml(s.yamlDsl);
      setNodes(parsed.nodes);
      setEdges(parsed.edges);
    } catch {
      setNodes([]);
      setEdges([]);
    }
  };

  return (
    <div className="p-2 overflow-y-auto border-t border-[#2a2d3a]">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs uppercase tracking-wider text-gray-500">Scenarios</div>
        <button
          className="text-xs px-2 py-0.5 rounded bg-blue-600 hover:bg-blue-500"
          onClick={() => createMutation.mutate()}
        >
          + New
        </button>
      </div>
      <div className="flex flex-col gap-1">
        {(data ?? []).map((s) => (
          <button
            key={s.id}
            className={`text-left px-2 py-1 rounded text-xs ${
              activeScenario?.id === s.id
                ? 'bg-blue-700 text-white'
                : 'bg-[#0f1117] hover:bg-[#22252f] text-gray-200'
            }`}
            onClick={() => onSelect(s)}
          >
            <div className="font-medium truncate">{s.name}</div>
            <div className="text-[10px] opacity-70">v{s.version}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
```

3. Replace `fix-flow-ui/src/panels/left/LeftPanel.tsx`:
```typescript
import { NodePalette } from './NodePalette';
import { ScenarioList } from './ScenarioList';

export default function LeftPanel() {
  return (
    <div className="bg-[#1a1d27] border-r border-[#2a2d3a] flex flex-col">
      <div className="flex-1 min-h-0 overflow-hidden">
        <NodePalette />
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <ScenarioList />
      </div>
    </div>
  );
}
```

4. Run:
```bash
cd fix-flow-ui && npm run dev
```
Drag node from palette to canvas — node appears.

5. Commit:
```bash
git add fix-flow-ui/src/panels/left
git commit -m "feat(ui): add NodePalette drag source and ScenarioList loader"
```

---

### Task 40: RuntimePanel + EventLog + FIXMessageLog

**Files:**
- Create: `fix-flow-ui/src/panels/bottom/EventLog.tsx`
- Create: `fix-flow-ui/src/panels/bottom/FIXMessageLog.tsx`
- Create: `fix-flow-ui/src/panels/bottom/ValidationErrors.tsx`
- Create: `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx`
- Modify: `fix-flow-ui/src/panels/bottom/BottomPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/bottom/EventLog.tsx`:
```typescript
import { useEffect, useRef } from 'react';
import { useExecutionStore } from '../../store/executionStore';

const TYPE_COLORS: Record<string, string> = {
  NODE_STARTED: 'bg-blue-700',
  NODE_COMPLETED: 'bg-green-700',
  NODE_FAILED: 'bg-red-700',
  SCENARIO_STARTED: 'bg-blue-700',
  SCENARIO_PASSED: 'bg-green-700',
  SCENARIO_FAILED: 'bg-red-700',
  VALIDATION_FAILED: 'bg-red-700',
  MESSAGE_SENT: 'bg-cyan-700',
  MESSAGE_RECEIVED: 'bg-purple-700',
  TIMEOUT: 'bg-amber-700',
};

export function EventLog() {
  const events = useExecutionStore((s) => s.events);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [events]);

  return (
    <div ref={ref} className="h-full overflow-y-auto px-2 py-1 font-mono text-[11px]">
      {events.length === 0 && <div className="text-gray-500 italic">No events</div>}
      {events.map((e) => (
        <div key={e.id} className="flex gap-2 py-0.5 border-b border-[#2a2d3a]">
          <div className="text-gray-500">{new Date(e.timestamp).toLocaleTimeString()}</div>
          <div
            className={`px-1.5 rounded text-[10px] uppercase ${
              TYPE_COLORS[e.type] ?? 'bg-gray-700'
            }`}
          >
            {e.type}
          </div>
          <div className="text-blue-300">{e.nodeId ?? '-'}</div>
          <div className="text-gray-300 truncate">{e.detail ?? ''}</div>
        </div>
      ))}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/panels/bottom/FIXMessageLog.tsx`:
```typescript
import { useEffect, useMemo, useRef, useState } from 'react';
import { useExecutionStore } from '../../store/executionStore';

export function FIXMessageLog() {
  const messages = useExecutionStore((s) => s.messages);
  const [hideHeartbeats, setHideHeartbeats] = useState(true);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const ref = useRef<HTMLDivElement>(null);

  const visible = useMemo(() => {
    if (!hideHeartbeats) return messages;
    return messages.filter((m) => m.fields[35] !== '0' && m.fields[35] !== '1');
  }, [messages, hideHeartbeats]);

  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [visible]);

  return (
    <div className="h-full flex flex-col">
      <div className="px-2 py-1 border-b border-[#2a2d3a] flex items-center gap-2">
        <label className="text-xs flex items-center gap-1">
          <input
            type="checkbox"
            checked={hideHeartbeats}
            onChange={(e) => setHideHeartbeats(e.target.checked)}
          />
          Hide Heartbeats
        </label>
        <div className="ml-auto text-[10px] text-gray-500">
          {visible.length} / {messages.length} messages
        </div>
      </div>
      <div ref={ref} className="flex-1 overflow-y-auto px-2 py-1 font-mono text-[11px]">
        {visible.length === 0 && <div className="text-gray-500 italic">No messages</div>}
        {visible.map((m) => {
          const isExp = expanded[m.id];
          const display = isExp ? m.rawFix : m.rawFix.slice(0, 80);
          return (
            <div
              key={m.id}
              className="py-0.5 border-b border-[#2a2d3a] cursor-pointer"
              onClick={() => setExpanded((p) => ({ ...p, [m.id]: !p[m.id] }))}
            >
              <div className="flex gap-2">
                <div className="text-gray-500">
                  {new Date(m.receivedAt).toLocaleTimeString()}
                </div>
                <div
                  className={`px-1.5 rounded text-[10px] ${
                    m.direction === 'INBOUND' ? 'bg-green-700' : 'bg-blue-700'
                  }`}
                >
                  {m.direction === 'INBOUND' ? 'IN' : 'OUT'}
                </div>
                <div className="text-amber-300">35={m.fields[35] ?? '?'}</div>
                <div className="text-gray-300 truncate">{display}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

3. Create `fix-flow-ui/src/panels/bottom/ValidationErrors.tsx`:
```typescript
import { useMemo } from 'react';
import { useExecutionStore } from '../../store/executionStore';
import { ValidationError } from '../../types';

export function ValidationErrors() {
  const events = useExecutionStore((s) => s.events);

  const errors: ValidationError[] = useMemo(() => {
    const collected: ValidationError[] = [];
    for (const e of events) {
      if (e.type !== 'VALIDATION_FAILED' || !e.detail) continue;
      try {
        const parsed = JSON.parse(e.detail) as ValidationError | ValidationError[];
        if (Array.isArray(parsed)) collected.push(...parsed);
        else collected.push(parsed);
      } catch {
        collected.push({ tag: 0, rule: 'UNKNOWN', expected: '', actual: e.detail });
      }
    }
    return collected;
  }, [events]);

  return (
    <div className="h-full overflow-y-auto px-2 py-1">
      {errors.length === 0 && (
        <div className="text-gray-500 italic text-xs">No validation errors</div>
      )}
      <table className="w-full text-xs">
        <thead className="text-left text-gray-500">
          <tr>
            <th className="py-1 pr-2">Tag</th>
            <th className="py-1 pr-2">Rule</th>
            <th className="py-1 pr-2">Expected</th>
            <th className="py-1 pr-2">Actual</th>
            <th className="py-1">Message</th>
          </tr>
        </thead>
        <tbody>
          {errors.map((e, i) => (
            <tr key={i} className="border-t border-[#2a2d3a]">
              <td className="py-1 pr-2 text-amber-300">{e.tag}</td>
              <td className="py-1 pr-2 text-blue-300">{e.rule}</td>
              <td className="py-1 pr-2 text-green-300">{e.expected}</td>
              <td className="py-1 pr-2 text-red-300">{e.actual}</td>
              <td className="py-1 text-gray-300">{e.message ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

4. Create `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx`:
```typescript
import { useMemo } from 'react';
import { useExecutionStore } from '../../store/executionStore';

export function ExecutionStats() {
  const events = useExecutionStore((s) => s.events);
  const startedAt = useExecutionStore((s) => s.startedAt);
  const endedAt = useExecutionStore((s) => s.endedAt);
  const executionStatus = useExecutionStore((s) => s.executionStatus);

  const stats = useMemo(() => {
    let nodesPassed = 0;
    let nodesFailed = 0;
    const nodeDurations: number[] = [];
    const nodeStartTimes: Record<string, number> = {};
    for (const e of events) {
      if (e.type === 'NODE_STARTED' && e.nodeId) {
        nodeStartTimes[e.nodeId] = new Date(e.timestamp).getTime();
      }
      if (e.type === 'NODE_COMPLETED' && e.nodeId) {
        nodesPassed += 1;
        if (nodeStartTimes[e.nodeId]) {
          nodeDurations.push(new Date(e.timestamp).getTime() - nodeStartTimes[e.nodeId]);
        }
      }
      if (e.type === 'NODE_FAILED' && e.nodeId) {
        nodesFailed += 1;
      }
    }
    const avgNodeMs =
      nodeDurations.length > 0
        ? Math.round(nodeDurations.reduce((a, b) => a + b, 0) / nodeDurations.length)
        : 0;
    const duration =
      startedAt && endedAt
        ? new Date(endedAt).getTime() - new Date(startedAt).getTime()
        : startedAt
          ? Date.now() - new Date(startedAt).getTime()
          : 0;
    return { nodesPassed, nodesFailed, avgNodeMs, duration };
  }, [events, startedAt, endedAt]);

  return (
    <div className="h-full overflow-y-auto px-3 py-2 grid grid-cols-2 md:grid-cols-4 gap-2 text-xs">
      <Stat label="Status" value={executionStatus} />
      <Stat label="Nodes passed" value={String(stats.nodesPassed)} />
      <Stat label="Nodes failed" value={String(stats.nodesFailed)} />
      <Stat label="Avg node time" value={`${stats.avgNodeMs} ms`} />
      <Stat label="Total duration" value={`${stats.duration} ms`} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-[#0f1117] border border-[#2a2d3a] rounded p-2">
      <div className="text-[10px] uppercase text-gray-500">{label}</div>
      <div className="text-base text-gray-100 mt-1">{value}</div>
    </div>
  );
}
```

5. Replace `fix-flow-ui/src/panels/bottom/BottomPanel.tsx`:
```typescript
import { useState } from 'react';
import { EventLog } from './EventLog';
import { FIXMessageLog } from './FIXMessageLog';
import { ValidationErrors } from './ValidationErrors';
import { ExecutionStats } from './ExecutionStats';

type Tab = 'events' | 'messages' | 'validation' | 'stats';

const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'events', label: 'Events' },
  { id: 'messages', label: 'FIX Messages' },
  { id: 'validation', label: 'Validation Errors' },
  { id: 'stats', label: 'Statistics' },
];

export default function BottomPanel() {
  const [tab, setTab] = useState<Tab>('events');
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div
      className="bg-[#1a1d27] border-t border-[#2a2d3a] flex flex-col"
      style={{ height: collapsed ? 32 : 240 }}
    >
      <div className="h-8 flex items-center border-b border-[#2a2d3a]">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => {
              setTab(t.id);
              setCollapsed(false);
            }}
            className={`px-3 h-full text-xs ${
              tab === t.id
                ? 'text-blue-400 border-b-2 border-blue-400'
                : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            {t.label}
          </button>
        ))}
        <button
          className="ml-auto px-3 text-xs text-gray-400 hover:text-gray-200"
          onClick={() => setCollapsed((c) => !c)}
        >
          {collapsed ? 'Expand' : 'Collapse'}
        </button>
      </div>
      {!collapsed && (
        <div className="flex-1 min-h-0">
          {tab === 'events' && <EventLog />}
          {tab === 'messages' && <FIXMessageLog />}
          {tab === 'validation' && <ValidationErrors />}
          {tab === 'stats' && <ExecutionStats />}
        </div>
      )}
    </div>
  );
}
```

6. Run:
```bash
cd fix-flow-ui && npm run dev
```
Bottom panel opens/collapses, tabs switch.

7. Commit:
```bash
git add fix-flow-ui/src/panels/bottom
git commit -m "feat(ui): add bottom runtime panel with events, messages, validation, stats tabs"
```

---

### Task 41: Live execution wiring (WS → node highlighting)

**Files:**
- Create: `fix-flow-ui/src/hooks/useExecutionSubscription.ts`
- Modify: `fix-flow-ui/src/App.tsx`
- Modify: `fix-flow-ui/src/components/TopBar.tsx`

**Steps:**

1. Create `fix-flow-ui/src/hooks/useExecutionSubscription.ts`:
```typescript
import { useEffect } from 'react';
import { useExecutionStore } from '../store/executionStore';
import { wsClient } from '../app/wsClient';
import { ExecutionEvent, FIXMessage } from '../types';

export function useExecutionSubscription(executionId: string | null): void {
  const addEvent = useExecutionStore((s) => s.addEvent);
  const addMessage = useExecutionStore((s) => s.addMessage);
  const setNodeStatus = useExecutionStore((s) => s.setNodeStatus);
  const updateStatus = useExecutionStore((s) => s.updateStatus);
  const setStartedAt = useExecutionStore((s) => s.setStartedAt);
  const setEndedAt = useExecutionStore((s) => s.setEndedAt);

  useEffect(() => {
    if (!executionId) return;
    let disposer: (() => void) | null = null;
    let cancelled = false;

    const handleEvent = (event: ExecutionEvent) => {
      addEvent(event);
      if (event.type === 'SCENARIO_STARTED') {
        setStartedAt(event.timestamp);
        updateStatus('RUNNING');
      }
      if (event.type === 'NODE_STARTED' && event.nodeId) {
        setNodeStatus(event.nodeId, 'running');
      }
      if (event.type === 'NODE_COMPLETED' && event.nodeId) {
        setNodeStatus(event.nodeId, 'passed');
      }
      if (event.type === 'NODE_FAILED' && event.nodeId) {
        setNodeStatus(event.nodeId, 'failed');
      }
      if (event.type === 'SCENARIO_PASSED') {
        updateStatus('PASSED');
        setEndedAt(event.timestamp);
      }
      if (event.type === 'SCENARIO_FAILED') {
        updateStatus('FAILED');
        setEndedAt(event.timestamp);
      }
      if (event.type === 'SCENARIO_STOPPED') {
        updateStatus('STOPPED');
        setEndedAt(event.timestamp);
      }
    };

    const handleMessage = (msg: FIXMessage) => {
      addMessage(msg);
    };

    wsClient
      .subscribeExecution(executionId, handleEvent, handleMessage)
      .then((d) => {
        if (cancelled) d();
        else disposer = d;
      })
      .catch((err) => console.error('WS subscribe failed', err));

    return () => {
      cancelled = true;
      if (disposer) disposer();
    };
  }, [executionId, addEvent, addMessage, setNodeStatus, updateStatus, setStartedAt, setEndedAt]);
}
```

2. Modify `fix-flow-ui/src/App.tsx` to wire the subscription:
```typescript
import TopBar from './components/TopBar';
import FlowCanvas from './canvas/FlowCanvas';
import LeftPanel from './panels/left/LeftPanel';
import RightPanel from './panels/right/RightPanel';
import BottomPanel from './panels/bottom/BottomPanel';
import { useExecutionStore } from './store/executionStore';
import { useExecutionSubscription } from './hooks/useExecutionSubscription';

export default function App() {
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  useExecutionSubscription(activeExecutionId);

  return (
    <div className="h-screen flex flex-col bg-[#0f1117] text-gray-100">
      <TopBar />
      <div
        className="flex-1 overflow-hidden"
        style={{
          display: 'grid',
          gridTemplateColumns: '240px 1fr 320px',
          gridTemplateRows: '1fr',
        }}
      >
        <LeftPanel />
        <FlowCanvas />
        <RightPanel />
      </div>
      <BottomPanel />
    </div>
  );
}
```

3. Update `fix-flow-ui/src/components/TopBar.tsx` Run mutation to also reset execution state before subscribing:
```typescript
// Replace the runMutation onSuccess body with:
onSuccess: (exec) => {
  useExecutionStore.getState().reset();
  setActiveExecution(exec.id);
  updateStatus('RUNNING');
},
```
The full Run handler now:
```typescript
const runMutation = useMutation({
  mutationFn: async () => {
    if (!activeScenario || !activeSession) throw new Error('No scenario or session');
    return executeScenario(activeScenario.id, activeSession.id);
  },
  onSuccess: (exec) => {
    useExecutionStore.getState().reset();
    setActiveExecution(exec.id);
    updateStatus('RUNNING');
  },
});
```

4. Run:
```bash
cd fix-flow-ui && npm run dev
```
Click Run on a scenario — nodes turn amber while running, green when done.

5. Commit:
```bash
git add fix-flow-ui/src
git commit -m "feat(ui): wire WebSocket execution events to live node status highlighting"
```

---

## Phase 12: Right Panel Config Forms (Tasks 42-45)

### Task 42: PropertiesPanel + SendFIXConfig

**Files:**
- Create: `fix-flow-ui/src/panels/right/PropertiesPanel.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/RightPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx`:
```typescript
import { TimeUnit, TimeoutAction, TimeoutConfig as Cfg } from '../../../types';

const UNITS: TimeUnit[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];
const ACTIONS: TimeoutAction[] = ['FAIL', 'RETRY', 'CONTINUE', 'JUMP'];

interface Props {
  value: Cfg | undefined;
  onChange: (next: Cfg | undefined) => void;
}

export function TimeoutConfig({ value, onChange }: Props) {
  const cfg: Cfg = value ?? { value: 30, unit: 'SECONDS', onTimeout: 'FAIL' };

  const update = (patch: Partial<Cfg>) => onChange({ ...cfg, ...patch });

  return (
    <div className="border border-[#2a2d3a] rounded p-2 mt-2">
      <div className="text-[10px] uppercase text-gray-500 mb-1">Timeout</div>
      <div className="flex gap-1">
        <input
          type="number"
          className="w-20 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.value}
          onChange={(e) => update({ value: Number(e.target.value) })}
        />
        <select
          className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.unit}
          onChange={(e) => update({ unit: e.target.value as TimeUnit })}
        >
          {UNITS.map((u) => (
            <option key={u}>{u}</option>
          ))}
        </select>
      </div>
      <div className="mt-1">
        <label className="text-[10px] text-gray-500">On Timeout</label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.onTimeout}
          onChange={(e) => update({ onTimeout: e.target.value as TimeoutAction })}
        >
          {ACTIONS.map((a) => (
            <option key={a}>{a}</option>
          ))}
        </select>
      </div>
      {cfg.onTimeout === 'JUMP' && (
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">Jump To Node</label>
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
            value={cfg.jumpTo ?? ''}
            onChange={(e) => update({ jumpTo: e.target.value })}
          />
        </div>
      )}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface FieldRow {
  tag: number;
  value: string;
}

interface SendCfg {
  msgType?: string;
  fields?: FieldRow[];
}

interface Props {
  node: ScenarioNode;
}

export function SendFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as SendCfg) ?? {};
  const fields = cfg.fields ?? [];

  const patchConfig = (patch: Partial<SendCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateField = (i: number, patch: Partial<FieldRow>) => {
    const next = fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f));
    patchConfig({ fields: next });
  };

  const addField = () => patchConfig({ fields: [...fields, { tag: 0, value: '' }] });
  const removeField = (i: number) =>
    patchConfig({ fields: fields.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">MsgType (tag 35)</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''}
          onChange={(e) => patchConfig({ msgType: e.target.value })}
        />
      </div>
      <div>
        <div className="flex items-center justify-between">
          <label className="text-[10px] text-gray-500">Fields</label>
          <button
            className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
            onClick={addField}
          >
            + Field
          </button>
        </div>
        <table className="w-full mt-1">
          <thead className="text-[10px] text-gray-500">
            <tr>
              <th className="text-left">Tag</th>
              <th className="text-left">Value</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {fields.map((f, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input
                    type="number"
                    className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.tag}
                    onChange={(e) =>
                      updateField(i, { tag: Number(e.target.value) })
                    }
                  />
                </td>
                <td className="pr-1">
                  <input
                    type="text"
                    className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.value}
                    onChange={(e) => updateField(i, { value: e.target.value })}
                  />
                </td>
                <td>
                  <button
                    className="text-red-400 hover:text-red-300 text-xs"
                    onClick={() => removeField(i)}
                  >
                    x
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(next) => updateNode(node.id, { timeout: next })}
      />
    </div>
  );
}
```

3. Create `fix-flow-ui/src/panels/right/PropertiesPanel.tsx`:
```typescript
import { useScenarioStore } from '../../store/scenarioStore';
import { SendFIXConfig } from './NodeConfig/SendFIXConfig';
import { ExpectFIXConfig } from './NodeConfig/ExpectFIXConfig';
import { ValidateConfig } from './NodeConfig/ValidateConfig';
import { RetryConfig } from './NodeConfig/RetryConfig';

export function PropertiesPanel() {
  const selectedNodeId = useScenarioStore((s) => s.selectedNodeId);
  const node = useScenarioStore((s) =>
    selectedNodeId ? s.nodes.find((n) => n.id === selectedNodeId) ?? null : null,
  );

  return (
    <div className="p-2 overflow-y-auto border-b border-[#2a2d3a]">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Properties</div>
      {!node && <div className="text-xs text-gray-500 italic">Select a node to configure</div>}
      {node?.type === 'SEND_FIX' && <SendFIXConfig node={node} />}
      {node?.type === 'EXPECT_FIX' && <ExpectFIXConfig node={node} />}
      {node?.type === 'VALIDATE' && <ValidateConfig node={node} />}
      {(node?.type === 'RETRY' || node?.type === 'LOOP') && <RetryConfig node={node} />}
      {node &&
        !['SEND_FIX', 'EXPECT_FIX', 'VALIDATE', 'RETRY', 'LOOP'].includes(node.type) && (
          <div className="text-xs text-gray-500 italic">
            No configuration available for {node.type}
          </div>
        )}
    </div>
  );
}
```

4. Add stub files (overridden in Task 43) so PropertiesPanel compiles. Create `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';

export function ExpectFIXConfig({ node }: { node: ScenarioNode }) {
  return <div className="text-xs text-gray-500">ExpectFIX config — see Task 43 ({node.id})</div>;
}
```

5. Create `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';

export function ValidateConfig({ node }: { node: ScenarioNode }) {
  return <div className="text-xs text-gray-500">Validate config — see Task 43 ({node.id})</div>;
}
```

6. Create `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';

export function RetryConfig({ node }: { node: ScenarioNode }) {
  return <div className="text-xs text-gray-500">Retry config — see Task 43 ({node.id})</div>;
}
```

7. Replace `fix-flow-ui/src/panels/right/RightPanel.tsx`:
```typescript
import { PropertiesPanel } from './PropertiesPanel';

export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] flex flex-col">
      <div className="flex-1 min-h-0 overflow-hidden">
        <PropertiesPanel />
      </div>
    </div>
  );
}
```

8. Run:
```bash
cd fix-flow-ui && npm run dev
```
Click a SEND_FIX node — right panel shows config form, edits persist.

9. Commit:
```bash
git add fix-flow-ui/src/panels/right
git commit -m "feat(ui): add PropertiesPanel with SendFIX config and timeout editor"
```

---

### Task 43: ExpectFIXConfig + ValidateConfig + DateRulesEditor

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/DateRulesEditor.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`

**Steps:**

1. Replace `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface CorrelationCfg {
  sourceTag?: number;
  fromNode?: string;
  targetTag?: number;
}

interface ExpectCfg {
  msgType?: string;
  correlation?: CorrelationCfg;
}

interface Props {
  node: ScenarioNode;
}

export function ExpectFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as ExpectCfg) ?? {};
  const corr = cfg.correlation ?? {};

  const patchConfig = (patch: Partial<ExpectCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });
  const patchCorr = (patch: Partial<CorrelationCfg>) =>
    patchConfig({ correlation: { ...corr, ...patch } });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">MsgType (tag 35)</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''}
          onChange={(e) => patchConfig({ msgType: e.target.value })}
        />
      </div>
      <div className="border border-[#2a2d3a] rounded p-2">
        <div className="text-[10px] uppercase text-gray-500 mb-1">Correlation</div>
        <div>
          <label className="text-[10px] text-gray-500">Source Tag (in received)</label>
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.sourceTag ?? 0}
            onChange={(e) => patchCorr({ sourceTag: Number(e.target.value) })}
          />
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">From Node</label>
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.fromNode ?? ''}
            onChange={(e) => patchCorr({ fromNode: e.target.value })}
          >
            <option value="">-- select --</option>
            {allNodes
              .filter((n) => n.id !== node.id)
              .map((n) => (
                <option key={n.id} value={n.id}>
                  {n.name} ({n.type})
                </option>
              ))}
          </select>
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">Target Tag (in source node)</label>
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.targetTag ?? 0}
            onChange={(e) => patchCorr({ targetTag: Number(e.target.value) })}
          />
        </div>
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(next) => updateNode(node.id, { timeout: next })}
      />
    </div>
  );
}
```

2. Replace `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { DateRulesEditor, DateRule } from './DateRulesEditor';

type RuleKind =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'ENUM'
  | 'REGEX'
  | 'NUMERIC_MIN'
  | 'NUMERIC_MAX'
  | 'FIELD_PRESENT'
  | 'FIELD_ABSENT'
  | 'DATE_RULE';

interface ValidationRule {
  tag: number;
  rule: RuleKind;
  value?: string;
  values?: string[];
  pattern?: string;
  numericValue?: number;
  ref?: string;
  dateRuleId?: string;
}

interface ValidateCfg {
  strictMode?: boolean;
  rules?: ValidationRule[];
  dateRules?: DateRule[];
}

interface Props {
  node: ScenarioNode;
}

const RULES: RuleKind[] = [
  'EQUALS',
  'NOT_EQUALS',
  'ENUM',
  'REGEX',
  'NUMERIC_MIN',
  'NUMERIC_MAX',
  'FIELD_PRESENT',
  'FIELD_ABSENT',
  'DATE_RULE',
];

export function ValidateConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as ValidateCfg) ?? {};
  const rules = cfg.rules ?? [];
  const dateRules = cfg.dateRules ?? [];

  const patchConfig = (patch: Partial<ValidateCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateRule = (i: number, patch: Partial<ValidationRule>) => {
    const next = rules.map((r, idx) => (idx === i ? { ...r, ...patch } : r));
    patchConfig({ rules: next });
  };

  const addRule = () =>
    patchConfig({ rules: [...rules, { tag: 0, rule: 'EQUALS', value: '' }] });
  const removeRule = (i: number) =>
    patchConfig({ rules: rules.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={cfg.strictMode ?? false}
          onChange={(e) => patchConfig({ strictMode: e.target.checked })}
        />
        Strict Mode
      </label>
      <div className="flex items-center justify-between">
        <div className="text-[10px] uppercase text-gray-500">Rules</div>
        <button
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={addRule}
        >
          + Rule
        </button>
      </div>
      <div className="space-y-1">
        {rules.map((r, i) => (
          <div key={i} className="border border-[#2a2d3a] rounded p-2">
            <div className="flex gap-1 items-center">
              <input
                type="number"
                className="w-16 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.tag}
                onChange={(e) => updateRule(i, { tag: Number(e.target.value) })}
                placeholder="tag"
              />
              <select
                className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.rule}
                onChange={(e) => updateRule(i, { rule: e.target.value as RuleKind })}
              >
                {RULES.map((rk) => (
                  <option key={rk}>{rk}</option>
                ))}
              </select>
              <button
                className="text-red-400 hover:text-red-300"
                onClick={() => removeRule(i)}
              >
                x
              </button>
            </div>
            {(r.rule === 'EQUALS' || r.rule === 'NOT_EQUALS') && (
              <input
                type="text"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.value ?? ''}
                onChange={(e) => updateRule(i, { value: e.target.value })}
                placeholder="value"
              />
            )}
            {r.rule === 'ENUM' && (
              <input
                type="text"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={(r.values ?? []).join(',')}
                onChange={(e) =>
                  updateRule(i, {
                    values: e.target.value.split(',').map((s) => s.trim()),
                  })
                }
                placeholder="comma,separated,values"
              />
            )}
            {r.rule === 'REGEX' && (
              <input
                type="text"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.pattern ?? ''}
                onChange={(e) => updateRule(i, { pattern: e.target.value })}
                placeholder="regex pattern"
              />
            )}
            {(r.rule === 'NUMERIC_MIN' || r.rule === 'NUMERIC_MAX') && (
              <input
                type="number"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.numericValue ?? 0}
                onChange={(e) => updateRule(i, { numericValue: Number(e.target.value) })}
                placeholder="numeric value"
              />
            )}
            {r.rule === 'DATE_RULE' && (
              <select
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.dateRuleId ?? ''}
                onChange={(e) => updateRule(i, { dateRuleId: e.target.value })}
              >
                <option value="">-- select date rule --</option>
                {dateRules.map((dr) => (
                  <option key={dr.ruleId} value={dr.ruleId}>
                    {dr.ruleId}
                  </option>
                ))}
              </select>
            )}
            <input
              type="text"
              className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              value={r.ref ?? ''}
              onChange={(e) => updateRule(i, { ref: e.target.value })}
              placeholder="cross-node ref (optional)"
            />
          </div>
        ))}
      </div>
      <DateRulesEditor
        value={dateRules}
        onChange={(next) => patchConfig({ dateRules: next })}
      />
    </div>
  );
}
```

3. Create `fix-flow-ui/src/panels/right/NodeConfig/DateRulesEditor.tsx`:
```typescript
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeUnit } from '../../../types';

export type DateRuleType = 'CURRENT_TIMESTAMP' | 'FIELD_OFFSET';

export interface DateRule {
  ruleId: string;
  type: DateRuleType;
  sourceNode?: string;
  sourceTag?: number;
  offsetValue?: number;
  offsetUnit?: TimeUnit;
  toleranceValue: number;
  toleranceUnit: TimeUnit;
}

const UNITS: TimeUnit[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];

interface Props {
  value: DateRule[];
  onChange: (next: DateRule[]) => void;
}

export function DateRulesEditor({ value, onChange }: Props) {
  const allNodes = useScenarioStore((s) => s.nodes);

  const add = () =>
    onChange([
      ...value,
      {
        ruleId: `dr-${Date.now()}`,
        type: 'CURRENT_TIMESTAMP',
        toleranceValue: 1,
        toleranceUnit: 'SECONDS',
      },
    ]);
  const remove = (i: number) => onChange(value.filter((_, idx) => idx !== i));
  const update = (i: number, patch: Partial<DateRule>) =>
    onChange(value.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  return (
    <div className="border border-[#2a2d3a] rounded p-2 mt-2">
      <div className="flex items-center justify-between mb-1">
        <div className="text-[10px] uppercase text-gray-500">Date Rules</div>
        <button
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={add}
        >
          + Date Rule
        </button>
      </div>
      <div className="space-y-2">
        {value.map((r, i) => (
          <div key={i} className="border border-[#2a2d3a] rounded p-2 space-y-1">
            <div className="flex gap-1">
              <input
                type="text"
                className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.ruleId}
                onChange={(e) => update(i, { ruleId: e.target.value })}
                placeholder="ruleId"
              />
              <button
                className="text-red-400 hover:text-red-300"
                onClick={() => remove(i)}
              >
                x
              </button>
            </div>
            <select
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              value={r.type}
              onChange={(e) => update(i, { type: e.target.value as DateRuleType })}
            >
              <option value="CURRENT_TIMESTAMP">CURRENT_TIMESTAMP</option>
              <option value="FIELD_OFFSET">FIELD_OFFSET</option>
            </select>
            {r.type === 'FIELD_OFFSET' && (
              <>
                <select
                  className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                  value={r.sourceNode ?? ''}
                  onChange={(e) => update(i, { sourceNode: e.target.value })}
                >
                  <option value="">-- source node --</option>
                  {allNodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.name}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                  value={r.sourceTag ?? 0}
                  onChange={(e) => update(i, { sourceTag: Number(e.target.value) })}
                  placeholder="source tag"
                />
                <div className="flex gap-1">
                  <input
                    type="number"
                    className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                    value={r.offsetValue ?? 0}
                    onChange={(e) =>
                      update(i, { offsetValue: Number(e.target.value) })
                    }
                    placeholder="offset"
                  />
                  <select
                    className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                    value={r.offsetUnit ?? 'SECONDS'}
                    onChange={(e) =>
                      update(i, { offsetUnit: e.target.value as TimeUnit })
                    }
                  >
                    {UNITS.map((u) => (
                      <option key={u}>{u}</option>
                    ))}
                  </select>
                </div>
              </>
            )}
            <div className="flex gap-1">
              <input
                type="number"
                className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.toleranceValue}
                onChange={(e) => update(i, { toleranceValue: Number(e.target.value) })}
                placeholder="tolerance"
              />
              <select
                className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.toleranceUnit}
                onChange={(e) => update(i, { toleranceUnit: e.target.value as TimeUnit })}
              >
                {UNITS.map((u) => (
                  <option key={u}>{u}</option>
                ))}
              </select>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

4. Replace `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface RetryCfg {
  targetNodeId?: string;
}

interface Props {
  node: ScenarioNode;
}

export function RetryConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RetryCfg) ?? {};
  const policy = node.retryPolicy ?? { maxAttempts: 3, delayMs: 1000 };

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Max Attempts</label>
        <input
          type="number"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.maxAttempts}
          onChange={(e) =>
            updateNode(node.id, {
              retryPolicy: { ...policy, maxAttempts: Number(e.target.value) },
            })
          }
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Delay (ms)</label>
        <input
          type="number"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.delayMs}
          onChange={(e) =>
            updateNode(node.id, {
              retryPolicy: { ...policy, delayMs: Number(e.target.value) },
            })
          }
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Target Node</label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''}
          onChange={(e) =>
            updateNode(node.id, {
              config: { ...cfg, targetNodeId: e.target.value },
            })
          }
        >
          <option value="">-- select --</option>
          {allNodes
            .filter((n) => n.id !== node.id)
            .map((n) => (
              <option key={n.id} value={n.id}>
                {n.name}
              </option>
            ))}
        </select>
      </div>
    </div>
  );
}
```

5. Run:
```bash
cd fix-flow-ui && npm run dev
```
Click a VALIDATE node — rules editor with date rules appears.

6. Commit:
```bash
git add fix-flow-ui/src/panels/right/NodeConfig
git commit -m "feat(ui): add ExpectFIX, Validate, DateRules and Retry config editors"
```

---

### Task 44: SessionPanel (FIX version + CompIDs configurable)

**Files:**
- Create: `fix-flow-ui/src/panels/right/SessionPanel.tsx`
- Modify: `fix-flow-ui/src/panels/right/RightPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/right/SessionPanel.tsx`:
```typescript
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getSessions,
  createSession,
  updateSession,
  connectSession,
  disconnectSession,
} from '../../api/sessions';
import { useSessionStore } from '../../store/sessionStore';
import { FIXSessionConfig, FIXSessionCreateRequest, FIXVersion, FIXMode } from '../../types';

const FIX_VERSIONS: Array<{ value: FIXVersion; label: string }> = [
  { value: 'FIX_42', label: 'FIX 4.2' },
  { value: 'FIX_44', label: 'FIX 4.4' },
  { value: 'FIXT_11', label: 'FIX 5.0 SP2 (FIXT.1.1)' },
];

const MODES: FIXMode[] = ['INITIATOR', 'ACCEPTOR'];

type FormValues = FIXSessionCreateRequest;

const DEFAULTS: FormValues = {
  name: 'default',
  mode: 'INITIATOR',
  fixVersion: 'FIX_44',
  defaultApplVerID: 'FIX.5.0SP2',
  senderCompID: 'CLIENT',
  targetCompID: 'SERVER',
  host: 'localhost',
  port: 9876,
  heartbeatInterval: 30,
  reconnectInterval: 5,
  resetOnLogon: true,
  resetOnLogout: false,
};

export function SessionPanel() {
  const queryClient = useQueryClient();
  const setSessions = useSessionStore((s) => s.setSessions);
  const activeSession = useSessionStore((s) => s.activeSession);
  const setActiveSession = useSessionStore((s) => s.setActiveSession);
  const [editingId, setEditingId] = useState<string | null>(null);

  const { register, handleSubmit, reset, watch, setValue } = useForm<FormValues>({
    defaultValues: DEFAULTS,
  });
  const fixVersion = watch('fixVersion');

  const { data: sessions } = useQuery({
    queryKey: ['sessions'],
    queryFn: getSessions,
  });

  useEffect(() => {
    if (sessions) setSessions(sessions);
  }, [sessions, setSessions]);

  useEffect(() => {
    if (activeSession) {
      reset({
        name: activeSession.name,
        mode: activeSession.mode,
        fixVersion: activeSession.fixVersion,
        defaultApplVerID: activeSession.defaultApplVerID,
        senderCompID: activeSession.senderCompID,
        targetCompID: activeSession.targetCompID,
        host: activeSession.host,
        port: activeSession.port,
        heartbeatInterval: activeSession.heartbeatInterval,
        reconnectInterval: activeSession.reconnectInterval,
        resetOnLogon: activeSession.resetOnLogon,
        resetOnLogout: activeSession.resetOnLogout,
      });
      setEditingId(activeSession.id);
    } else {
      reset(DEFAULTS);
      setEditingId(null);
    }
  }, [activeSession, reset]);

  const saveMutation = useMutation({
    mutationFn: async (values: FormValues): Promise<FIXSessionConfig> => {
      return editingId ? updateSession(editingId, values) : createSession(values);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(saved);
    },
  });

  const connectMutation = useMutation({
    mutationFn: async () => {
      if (!editingId) throw new Error('Save session first');
      return connectSession(editingId);
    },
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(s);
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: async () => {
      if (!editingId) throw new Error('No session');
      return disconnectSession(editingId);
    },
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(s);
    },
  });

  const connected = activeSession?.connected ?? false;

  return (
    <div className="p-2 overflow-y-auto border-t border-[#2a2d3a]">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs uppercase tracking-wider text-gray-500">Session</div>
        <div
          className={`px-2 py-0.5 rounded text-[10px] ${
            connected ? 'bg-green-700 text-white' : 'bg-gray-700 text-gray-300'
          }`}
        >
          {connected ? 'CONNECTED' : 'DISCONNECTED'}
        </div>
      </div>
      <div className="mb-2">
        <label className="text-[10px] text-gray-500">Active session</label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={activeSession?.id ?? ''}
          onChange={(e) => {
            const s = (sessions ?? []).find((x) => x.id === e.target.value) ?? null;
            setActiveSession(s);
          }}
        >
          <option value="">-- new session --</option>
          {(sessions ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <form
        onSubmit={handleSubmit((values) => saveMutation.mutate(values))}
        className="text-xs space-y-2"
      >
        <Field label="Name">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('name', { required: true })}
          />
        </Field>
        <Field label="Mode">
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('mode')}
          >
            {MODES.map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
        </Field>
        <Field label="FIX Version">
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            disabled={connected}
            {...register('fixVersion', {
              onChange: (e) => {
                if (e.target.value === 'FIXT_11') {
                  setValue('defaultApplVerID', 'FIX.5.0SP2');
                }
              },
            })}
          >
            {FIX_VERSIONS.map((v) => (
              <option key={v.value} value={v.value}>
                {v.label}
              </option>
            ))}
          </select>
          {connected && (
            <div className="text-[10px] text-amber-400 mt-1">
              Disconnect before changing FIX version
            </div>
          )}
        </Field>
        {fixVersion === 'FIXT_11' && (
          <Field label="DefaultApplVerID">
            <input
              type="text"
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              {...register('defaultApplVerID')}
            />
          </Field>
        )}
        <Field label="SenderCompID">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('senderCompID', { required: true })}
          />
        </Field>
        <Field label="TargetCompID">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('targetCompID', { required: true })}
          />
        </Field>
        <Field label="Host">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('host')}
          />
        </Field>
        <Field label="Port">
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('port', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Heartbeat Interval (sec)">
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('heartbeatInterval', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Reconnect Interval (sec)">
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('reconnectInterval', { valueAsNumber: true })}
          />
        </Field>
        <label className="flex items-center gap-2">
          <input type="checkbox" {...register('resetOnLogon')} /> Reset on Logon
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" {...register('resetOnLogout')} /> Reset on Logout
        </label>
        <div className="flex gap-1">
          <button
            type="submit"
            className="flex-1 px-2 py-1 rounded bg-gray-700 hover:bg-gray-600"
          >
            Save
          </button>
          {connected ? (
            <button
              type="button"
              className="flex-1 px-2 py-1 rounded bg-red-600 hover:bg-red-500"
              onClick={() => disconnectMutation.mutate()}
            >
              Disconnect
            </button>
          ) : (
            <button
              type="button"
              className="flex-1 px-2 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40"
              disabled={!editingId}
              onClick={() => connectMutation.mutate()}
            >
              Connect
            </button>
          )}
        </div>
      </form>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-[10px] text-gray-500">{label}</label>
      {children}
    </div>
  );
}
```

2. Replace `fix-flow-ui/src/panels/right/RightPanel.tsx`:
```typescript
import { PropertiesPanel } from './PropertiesPanel';
import { SessionPanel } from './SessionPanel';

export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] flex flex-col">
      <div className="flex-1 min-h-0 overflow-hidden">
        <PropertiesPanel />
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <SessionPanel />
      </div>
    </div>
  );
}
```

3. Run:
```bash
cd fix-flow-ui && npm run dev
```
Session panel shows all fields, FIX version dropdown works, DefaultApplVerID appears only for FIX 5.0.

4. Commit:
```bash
git add fix-flow-ui/src/panels/right
git commit -m "feat(ui): add SessionPanel with FIX version, CompIDs and connect controls"
```

---

### Task 45: Save scenario + YAML sync

**Files:**
- Modify: `fix-flow-ui/src/lib/scenarioSerializer.ts`
- Already wired: `fix-flow-ui/src/components/TopBar.tsx` (Save handler from Task 37)
- Already wired: `fix-flow-ui/src/panels/left/ScenarioList.tsx` (parse on select from Task 39)

**Steps:**

1. Replace `fix-flow-ui/src/lib/scenarioSerializer.ts` with a full implementation backed by `js-yaml`:
```typescript
import yaml from 'js-yaml';
import {
  NodeType,
  RetryPolicy,
  ScenarioEdge,
  ScenarioNode,
  TimeoutConfig,
} from '../types';

export interface ScenarioMeta {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
}

interface YamlNode {
  id: string;
  name: string;
  type: NodeType;
  config?: Record<string, unknown>;
  timeout?: TimeoutConfig;
  retryPolicy?: RetryPolicy;
  onSuccess?: string;
  onFailure?: string;
  onTimeout?: string;
  position?: { x: number; y: number };
}

interface YamlEdge {
  from: string;
  to: string;
  label: string;
}

interface YamlDoc extends ScenarioMeta {
  nodes: YamlNode[];
  edges: YamlEdge[];
}

export function serializeToYaml(
  nodes: ScenarioNode[],
  edges: ScenarioEdge[],
  meta: ScenarioMeta,
): string {
  const doc: YamlDoc = {
    ...meta,
    nodes: nodes.map((n) => ({
      id: n.id,
      name: n.name,
      type: n.type,
      config: n.config ?? {},
      timeout: n.timeout,
      retryPolicy: n.retryPolicy,
      onSuccess: n.onSuccess,
      onFailure: n.onFailure,
      onTimeout: n.onTimeout,
      position: n.position,
    })),
    edges: edges.map((e) => ({ from: e.from, to: e.to, label: e.label })),
  };
  return yaml.dump(doc, { noRefs: true, sortKeys: false, lineWidth: 120 });
}

export function parseFromYaml(yamlStr: string): {
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  meta: ScenarioMeta;
} {
  if (!yamlStr.trim()) {
    return {
      nodes: [],
      edges: [],
      meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
    };
  }
  const doc = yaml.load(yamlStr) as YamlDoc | null;
  if (!doc) {
    return {
      nodes: [],
      edges: [],
      meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
    };
  }
  const meta: ScenarioMeta = {
    id: doc.id ?? '',
    name: doc.name ?? '',
    description: doc.description ?? '',
    version: doc.version ?? '1.0',
    sessionRef: doc.sessionRef ?? '',
  };
  const nodes: ScenarioNode[] = (doc.nodes ?? []).map((n) => ({
    id: n.id,
    name: n.name,
    type: n.type,
    config: n.config ?? {},
    timeout: n.timeout,
    retryPolicy: n.retryPolicy,
    onSuccess: n.onSuccess,
    onFailure: n.onFailure,
    onTimeout: n.onTimeout,
    position: n.position,
  }));
  const edges: ScenarioEdge[] = (doc.edges ?? []).map((e) => ({
    from: e.from,
    to: e.to,
    label: e.label,
  }));
  return { nodes, edges, meta };
}
```

2. Run:
```bash
cd fix-flow-ui && npm run dev
```
Create a flow, save, reload the page, then re-open the scenario from ScenarioList — nodes and edges restore from YAML.

3. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no errors.

4. Commit:
```bash
git add fix-flow-ui/src/lib
git commit -m "feat(ui): implement YAML scenario serializer and parser with round-trip support"
```

---

## Phase 13: Reporting (Task 46)

### Task 46: Execution report download

**Files:**
- Modify: `fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/dto/ReportDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/service/ReportService.java`
- Create: `fix-flow-ui/src/panels/bottom/ExecutionReport.tsx`
- Modify: `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java`

**Steps:**

1. Create `fix-flow-api/src/main/java/com/fixflow/api/dto/ReportDto.java`:
```java
package com.fixflow.api.dto;

import java.util.List;
import java.util.Map;

public record ReportDto(
    String executionId,
    String scenarioName,
    String scenarioVersion,
    String sessionName,
    String status,
    String startTime,
    String endTime,
    long durationMs,
    List<NodeResultDto> nodeResults,
    List<String> rawFIXMessages,
    List<ValidationErrorDto> validationErrors,
    Map<String, Object> statistics
) {
    public record NodeResultDto(
        String nodeId,
        String nodeName,
        String status,
        long durationMs
    ) {}

    public record ValidationErrorDto(
        int tag,
        String rule,
        String expected,
        String actual,
        String message
    ) {}
}
```

2. Create `fix-flow-api/src/main/java/com/fixflow/api/service/ReportService.java`:
```java
package com.fixflow.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.api.dto.ReportDto.NodeResultDto;
import com.fixflow.api.dto.ReportDto.ValidationErrorDto;
import com.fixflow.persistence.entity.ExecutionEntity;
import com.fixflow.persistence.entity.ExecutionEventEntity;
import com.fixflow.persistence.entity.FIXMessageEntity;
import com.fixflow.persistence.entity.ScenarioEntity;
import com.fixflow.persistence.entity.FIXSessionEntity;
import com.fixflow.persistence.repository.ExecutionEventRepository;
import com.fixflow.persistence.repository.ExecutionRepository;
import com.fixflow.persistence.repository.FIXMessageRepository;
import com.fixflow.persistence.repository.ScenarioRepository;
import com.fixflow.persistence.repository.FIXSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class ReportService {

    private final ExecutionRepository executions;
    private final ScenarioRepository scenarios;
    private final FIXSessionRepository sessions;
    private final ExecutionEventRepository events;
    private final FIXMessageRepository messages;
    private final ObjectMapper objectMapper;

    public ReportService(
            ExecutionRepository executions,
            ScenarioRepository scenarios,
            FIXSessionRepository sessions,
            ExecutionEventRepository events,
            FIXMessageRepository messages,
            ObjectMapper objectMapper) {
        this.executions = executions;
        this.scenarios = scenarios;
        this.sessions = sessions;
        this.events = events;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    public ReportDto buildReport(UUID executionId) {
        ExecutionEntity exec = executions.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        ScenarioEntity scen = scenarios.findById(exec.getScenarioId()).orElse(null);
        FIXSessionEntity sess = sessions.findById(exec.getSessionId()).orElse(null);
        List<ExecutionEventEntity> eventList = events.findByExecutionIdOrderByTimestampAsc(executionId);
        List<FIXMessageEntity> messageList = messages.findByExecutionIdOrderByReceivedAtAsc(executionId);

        long durationMs = 0L;
        if (exec.getStartTime() != null && exec.getEndTime() != null) {
            durationMs = Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis();
        }

        Map<String, Long> nodeStartTimes = new HashMap<>();
        List<NodeResultDto> nodeResults = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        List<ValidationErrorDto> validationErrors = new ArrayList<>();

        for (ExecutionEventEntity e : eventList) {
            String type = e.getType();
            String nodeId = e.getNodeId();
            long ts = e.getTimestamp().toEpochMilli();
            if ("NODE_STARTED".equals(type) && nodeId != null) {
                nodeStartTimes.put(nodeId, ts);
            } else if (("NODE_COMPLETED".equals(type) || "NODE_FAILED".equals(type)) && nodeId != null) {
                Long start = nodeStartTimes.get(nodeId);
                long dur = start != null ? ts - start : 0L;
                String status = "NODE_COMPLETED".equals(type) ? "PASSED" : "FAILED";
                if ("PASSED".equals(status)) passed++; else failed++;
                nodeResults.add(new NodeResultDto(nodeId, nodeId, status, dur));
            } else if ("VALIDATION_FAILED".equals(type) && e.getDetail() != null) {
                validationErrors.add(parseValidationError(e.getDetail()));
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodesTotal", nodeResults.size());
        stats.put("nodesPassed", passed);
        stats.put("nodesFailed", failed);
        stats.put("messagesTotal", messageList.size());
        stats.put("eventsTotal", eventList.size());

        List<String> rawMessages = messageList.stream().map(FIXMessageEntity::getRawFix).toList();

        return new ReportDto(
                executionId.toString(),
                scen != null ? scen.getName() : "",
                scen != null ? scen.getVersion() : "",
                sess != null ? sess.getName() : "",
                exec.getStatus().name(),
                String.valueOf(exec.getStartTime()),
                exec.getEndTime() != null ? exec.getEndTime().toString() : "",
                durationMs,
                nodeResults,
                rawMessages,
                validationErrors,
                stats
        );
    }

    private ValidationErrorDto parseValidationError(String json) {
        try {
            Map<?, ?> m = objectMapper.readValue(json, Map.class);
            int tag = m.get("tag") instanceof Number n ? n.intValue() : 0;
            return new ValidationErrorDto(
                    tag,
                    String.valueOf(m.getOrDefault("rule", "")),
                    String.valueOf(m.getOrDefault("expected", "")),
                    String.valueOf(m.getOrDefault("actual", "")),
                    String.valueOf(m.getOrDefault("message", ""))
            );
        } catch (Exception ex) {
            return new ValidationErrorDto(0, "UNKNOWN", "", json, ex.getMessage());
        }
    }
}
```

3. Update `fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java` — add report endpoints:
```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.api.service.ReportService;
// ... existing imports
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;
    // ... existing fields and constructor injection adds reportService + objectMapper

    public ExecutionController(/* existing args */ ReportService reportService, ObjectMapper objectMapper) {
        // ... existing assignments
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{id}/report")
    public ReportDto getReport(@PathVariable UUID id) {
        return reportService.buildReport(id);
    }

    @GetMapping("/{id}/report/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id) throws Exception {
        ReportDto report = reportService.buildReport(id);
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"execution-" + id + "-report.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }
}
```

4. Create `fix-flow-ui/src/panels/bottom/ExecutionReport.tsx`:
```typescript
import { useExecutionStore } from '../../store/executionStore';
import { getExecutionReport } from '../../api/executions';

export function ExecutionReport() {
  const executionId = useExecutionStore((s) => s.activeExecutionId);

  const download = async () => {
    if (!executionId) return;
    const report = await getExecutionReport(executionId);
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `execution-${executionId}-report.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <button
      className="px-2 py-1 rounded bg-blue-600 hover:bg-blue-500 text-xs disabled:opacity-40"
      onClick={download}
      disabled={!executionId}
    >
      Download Report
    </button>
  );
}
```

5. Modify `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx` — add the download button at the top:
```typescript
// At the top of the rendered JSX inside the outer div, add a row containing <ExecutionReport />.
import { ExecutionReport } from './ExecutionReport';

// Inside the returned grid div, wrap with a flex container:
return (
  <div className="h-full overflow-y-auto px-3 py-2 space-y-2">
    <div className="flex justify-end">
      <ExecutionReport />
    </div>
    <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs">
      <Stat label="Status" value={executionStatus} />
      <Stat label="Nodes passed" value={String(stats.nodesPassed)} />
      <Stat label="Nodes failed" value={String(stats.nodesFailed)} />
      <Stat label="Avg node time" value={`${stats.avgNodeMs} ms`} />
      <Stat label="Total duration" value={`${stats.duration} ms`} />
    </div>
  </div>
);
```

6. Create `fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java`:
```java
package com.fixflow.api.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerTest {

    @Autowired private MockMvc mvc;

    @Test
    void downloadReportReturnsAttachment() throws Exception {
        UUID id = TestExecutionFactory.createCompletedExecution();
        mvc.perform(get("/api/v1/executions/{id}/report/download", id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                    "attachment; filename=\"execution-" + id + "-report.json\""))
            .andExpect(content().contentType("application/json"));
    }

    @Test
    void getReportReturnsJsonWithStatistics() throws Exception {
        UUID id = TestExecutionFactory.createCompletedExecution();
        mvc.perform(get("/api/v1/executions/{id}/report", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(id.toString()))
            .andExpect(jsonPath("$.statistics.nodesTotal").exists())
            .andExpect(jsonPath("$.statistics.messagesTotal").exists());
    }
}
```
Note: `TestExecutionFactory.createCompletedExecution()` is a helper that seeds a scenario, session, execution, events and messages directly via repositories. Implement it under `src/test/java/com/fixflow/api/rest/TestExecutionFactory.java` using the same repositories that Task 36 (backend) set up.

7. Run:
```bash
mvn test -pl fix-flow-api -Dtest=ExecutionControllerTest
```
Expect both tests to pass.

8. Commit:
```bash
git add fix-flow-api fix-flow-ui/src
git commit -m "feat(report): add execution report endpoint, download, and stats panel button"
```

---

## Phase 14: Full Test Suite (Tasks 47-49)

### Task 47: Unit test coverage for engine

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/scenario/ScenarioValidator.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/variable/DateOffsetPluginTest.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/StrictModeValidationTest.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioValidatorTest.java`

**Steps:**

1. Create `fix-flow-engine/src/test/java/com/fixflow/engine/variable/DateOffsetPluginTest.java`:
```java
package com.fixflow.engine.variable;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateOffsetPluginTest {

    private final DateOffsetPlugin plugin = new DateOffsetPlugin();

    @Test
    void plusFiveMinutes() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "+5m");
        assertEquals(base.plus(5, ChronoUnit.MINUTES), result);
    }

    @Test
    void minusThirtySeconds() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "-30s");
        assertEquals(base.minus(30, ChronoUnit.SECONDS), result);
    }

    @Test
    void plusTwoHours() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "+2h");
        assertEquals(base.plus(2, ChronoUnit.HOURS), result);
    }

    @Test
    void plusOneDay() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "+1d");
        assertEquals(base.plus(1, ChronoUnit.DAYS), result);
    }

    @Test
    void parsesIsoStringWithinOneSecond() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        String iso = plugin.format(plugin.applyOffset(base, "+5m"));
        Instant parsed = Instant.parse(iso);
        assertTrue(Math.abs(parsed.toEpochMilli() - base.plus(5, ChronoUnit.MINUTES).toEpochMilli()) < 1000);
    }
}
```

2. Create `fix-flow-engine/src/test/java/com/fixflow/engine/validation/StrictModeValidationTest.java`:
```java
package com.fixflow.engine.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictModeValidationTest {

    private final ValidationEngine engine = new ValidationEngine();

    private static final Map<Integer, String> MESSAGE = Map.of(
            35, "D",
            49, "CLIENT",
            56, "SERVER",
            11, "ORDER-1"
    );

    @Test
    void strictModeFailsOnUnvalidatedTags() {
        ValidationConfig cfg = new ValidationConfig(
                true,
                List.of(
                        new ValidationRule(35, RuleKind.EQUALS, "D", null, null, null, null),
                        new ValidationRule(11, RuleKind.EQUALS, "ORDER-1", null, null, null, null)
                ),
                List.of()
        );
        List<ValidationResult> results = engine.validate(MESSAGE, cfg, Map.of());
        long failures = results.stream().filter(r -> !r.passed()).count();
        assertEquals(2, failures, "Tags 49 and 56 should fail in strict mode");
    }

    @Test
    void nonStrictModePassesUnvalidatedTags() {
        ValidationConfig cfg = new ValidationConfig(
                false,
                List.of(
                        new ValidationRule(35, RuleKind.EQUALS, "D", null, null, null, null),
                        new ValidationRule(11, RuleKind.EQUALS, "ORDER-1", null, null, null, null)
                ),
                List.of()
        );
        List<ValidationResult> results = engine.validate(MESSAGE, cfg, Map.of());
        assertTrue(results.stream().allMatch(ValidationResult::passed));
    }
}
```

3. Create `fix-flow-engine/src/main/java/com/fixflow/engine/scenario/ScenarioValidator.java`:
```java
package com.fixflow.engine.scenario;

import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScenarioValidator {

    public List<String> validate(Scenario scenario) {
        List<String> errors = new ArrayList<>();
        if (scenario == null) {
            errors.add("Scenario is null");
            return errors;
        }
        Set<String> ids = new HashSet<>();
        for (ScenarioNode n : scenario.nodes()) {
            if (!ids.add(n.id())) {
                errors.add("Duplicate node id: " + n.id());
            }
        }
        for (ScenarioNode n : scenario.nodes()) {
            checkRef(errors, ids, n.id(), "onSuccess", n.onSuccess());
            checkRef(errors, ids, n.id(), "onFailure", n.onFailure());
            checkRef(errors, ids, n.id(), "onTimeout", n.onTimeout());
            if (n.timeout() != null && "JUMP".equals(n.timeout().onTimeout())) {
                checkRef(errors, ids, n.id(), "timeout.jumpTo", n.timeout().jumpTo());
            }
        }
        scenario.edges().forEach(e -> {
            if (!ids.contains(e.from())) errors.add("Edge from unknown node: " + e.from());
            if (!ids.contains(e.to())) errors.add("Edge to unknown node: " + e.to());
        });
        boolean hasStart = scenario.nodes().stream().anyMatch(n -> "START".equals(n.type()));
        if (!hasStart) errors.add("Scenario has no START node");
        return errors;
    }

    private void checkRef(List<String> errors, Set<String> ids, String nodeId, String field, String ref) {
        if (ref != null && !ref.isEmpty() && !ids.contains(ref)) {
            errors.add("Node " + nodeId + "." + field + " references unknown node: " + ref);
        }
    }
}
```

4. Create `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioValidatorTest.java`:
```java
package com.fixflow.engine.scenario;

import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioValidatorTest {

    private final ScenarioValidator validator = new ScenarioValidator();

    @Test
    void detectsUnknownOnSuccessReference() {
        Scenario scen = new Scenario(
                "s1", "S1", "", "1.0", "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "missing", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of()
        );
        List<String> errors = validator.validate(scen);
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing")));
    }

    @Test
    void validScenarioYieldsNoErrors() {
        Scenario scen = new Scenario(
                "s1", "S1", "", "1.0", "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "end", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of(new ScenarioEdge("start", "end", "success"))
        );
        assertEquals(List.of(), validator.validate(scen));
    }

    @Test
    void detectsMissingStart() {
        Scenario scen = new Scenario(
                "s1", "S1", "", "1.0", "default",
                List.of(new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)),
                List.of()
        );
        assertTrue(validator.validate(scen).stream().anyMatch(e -> e.contains("no START")));
    }
}
```

5. Run:
```bash
mvn test -pl fix-flow-engine
```
Expect all tests PASS.

6. Commit:
```bash
git add fix-flow-engine
git commit -m "test(engine): add DateOffsetPlugin, StrictModeValidation, and ScenarioValidator tests"
```

---

### Task 48: FakeFixAdapter multi-scenario system test

**Files:**
- Modify: `fix-flow-engine/pom.xml` (add awaitility)
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/SystemTest.java`

**Steps:**

1. Add awaitility dependency to `fix-flow-engine/pom.xml`:
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.1</version>
    <scope>test</scope>
</dependency>
```

2. Create `fix-flow-engine/src/test/java/com/fixflow/engine/SystemTest.java`:
```java
package com.fixflow.engine;

import com.fixflow.engine.adapter.FakeFixAdapter;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.ExecutionStatus;
import com.fixflow.engine.handler.EndHandler;
import com.fixflow.engine.handler.ExpectFIXHandler;
import com.fixflow.engine.handler.NodeDispatcher;
import com.fixflow.engine.handler.SendFIXHandler;
import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import com.fixflow.engine.publisher.NoOpEventPublisher;
import com.fixflow.engine.routing.MessageBuffer;
import com.fixflow.engine.routing.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.session.FIXSessionManager;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemTest {

    @Test
    void threeParallelScenariosOnOneSession() throws Exception {
        FakeFixAdapter fake = new FakeFixAdapter();
        MessageBuffer buffer = new MessageBuffer(1000, 60_000L);
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router::route);

        ScenarioRegistry registry = new ScenarioRegistry();
        NoOpEventPublisher publisher = new NoOpEventPublisher();
        VariableResolver resolver = new VariableResolver();
        FIXSessionManager sessionManager = new FIXSessionManager(fake);
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new SendFIXHandler(sessionManager, resolver),
                new ExpectFIXHandler(correlation),
                new EndHandler()
        ));
        ExecutionManager manager = new ExecutionManager(dispatcher, registry, publisher);

        UUID sessionId = UUID.randomUUID();
        Scenario s1 = buildScenario("S1", "REQ-001");
        Scenario s2 = buildScenario("S2", "REQ-002");
        Scenario s3 = buildScenario("S3", "REQ-003");
        registry.register(s1);
        registry.register(s2);
        registry.register(s3);

        UUID e1 = manager.start(s1.id(), sessionId);
        UUID e2 = manager.start(s2.id(), sessionId);
        UUID e3 = manager.start(s3.id(), sessionId);

        Thread.sleep(50);
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-003"));
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-001"));
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-002"));

        await().atMost(5, SECONDS).until(() ->
                manager.getStatus(e1) == ExecutionStatus.PASSED
             && manager.getStatus(e2) == ExecutionStatus.PASSED
             && manager.getStatus(e3) == ExecutionStatus.PASSED
        );

        assertEquals(ExecutionStatus.PASSED, manager.getStatus(e1));
        assertEquals(ExecutionStatus.PASSED, manager.getStatus(e2));
        assertEquals(ExecutionStatus.PASSED, manager.getStatus(e3));
    }

    private Scenario buildScenario(String id, String clOrdId) {
        ScenarioNode start = new ScenarioNode("start", "Start", "START", Map.of(),
                null, null, "send", null, null, null);
        ScenarioNode send = new ScenarioNode("send", "Send NOS", "SEND_FIX",
                Map.of("msgType", "D", "fields", List.of(Map.of("tag", 11, "value", clOrdId))),
                null, null, "expect", null, null, null);
        ScenarioNode expect = new ScenarioNode("expect", "Expect ER", "EXPECT_FIX",
                Map.of("msgType", "8",
                       "correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11)),
                null, null, "end", null, null, null);
        ScenarioNode end = new ScenarioNode("end", "End", "END_PASS",
                Map.of(), null, null, null, null, null, null);
        List<ScenarioEdge> edges = List.of(
                new ScenarioEdge("start", "send", "success"),
                new ScenarioEdge("send", "expect", "success"),
                new ScenarioEdge("expect", "end", "success")
        );
        return new Scenario(id, id, "", "1.0", "default",
                List.of(start, send, expect, end), edges);
    }
}
```

3. Run:
```bash
mvn test -pl fix-flow-engine -Dtest=SystemTest
```
Expect PASS.

4. Commit:
```bash
git add fix-flow-engine
git commit -m "test(engine): add multi-scenario parallel execution system test on shared session"
```

---

### Task 49: Hot reload + session failure test

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/adapter/FakeFixAdapter.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/HotReloadTest.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/SessionFailureTest.java`

**Steps:**

1. Add `simulateSessionDown` to `fix-flow-engine/src/main/java/com/fixflow/engine/adapter/FakeFixAdapter.java`:
```java
public void simulateSessionDown(UUID sessionId) {
    if (sessionStatusListener != null) {
        sessionStatusListener.onSessionStatus(sessionId, SessionStatus.DOWN, Instant.now());
    }
}
```
Also expose a `setSessionStatusListener(SessionStatusListener listener)` setter, and add the `SessionStatusListener` functional interface in the engine package if not already present:
```java
@FunctionalInterface
public interface SessionStatusListener {
    void onSessionStatus(UUID sessionId, SessionStatus status, Instant timestamp);
}

public enum SessionStatus { UP, DOWN, LOGON, LOGOUT }
```

2. Create `fix-flow-engine/src/test/java/com/fixflow/engine/HotReloadTest.java`:
```java
package com.fixflow.engine;

import com.fixflow.engine.adapter.FakeFixAdapter;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.ExecutionStatus;
import com.fixflow.engine.handler.EndHandler;
import com.fixflow.engine.handler.ExpectFIXHandler;
import com.fixflow.engine.handler.NodeDispatcher;
import com.fixflow.engine.handler.SendFIXHandler;
import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import com.fixflow.engine.publisher.NoOpEventPublisher;
import com.fixflow.engine.routing.MessageBuffer;
import com.fixflow.engine.routing.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.session.FIXSessionManager;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HotReloadTest {

    @Test
    void inFlightExecutionUsesOldVersionAfterReload() throws Exception {
        FakeFixAdapter fake = new FakeFixAdapter();
        MessageBuffer buffer = new MessageBuffer(1000, 60_000L);
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router::route);

        ScenarioRegistry registry = new ScenarioRegistry();
        ExecutionManager manager = new ExecutionManager(
                new NodeDispatcher(List.of(
                        new SendFIXHandler(new FIXSessionManager(fake), new VariableResolver()),
                        new ExpectFIXHandler(correlation),
                        new EndHandler()
                )),
                registry,
                new NoOpEventPublisher()
        );

        UUID sessionId = UUID.randomUUID();
        Scenario v1 = scenario("scen", "1.0", "REQ-A");
        registry.register(v1);
        UUID inFlight = manager.start(v1.id(), sessionId);

        Thread.sleep(100);

        Scenario v2 = scenario("scen", "2.0", "REQ-B");
        registry.reload(v2);

        // Inject the v1 expected response
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-A"));
        await().atMost(5, SECONDS).until(() -> manager.getStatus(inFlight) == ExecutionStatus.PASSED);

        UUID newExec = manager.start(v2.id(), sessionId);
        Thread.sleep(50);
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-B"));
        await().atMost(5, SECONDS).until(() -> manager.getStatus(newExec) == ExecutionStatus.PASSED);

        assertEquals("1.0", manager.getScenarioVersion(inFlight));
        assertEquals("2.0", manager.getScenarioVersion(newExec));
    }

    private Scenario scenario(String id, String version, String clOrdId) {
        return new Scenario(id, id, "", version, "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "send", null, null, null),
                        new ScenarioNode("send", "Send", "SEND_FIX",
                                Map.of("msgType", "D",
                                        "fields", List.of(Map.of("tag", 11, "value", clOrdId))),
                                null, null, "expect", null, null, null),
                        new ScenarioNode("expect", "Expect", "EXPECT_FIX",
                                Map.of("msgType", "8",
                                        "correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11)),
                                null, null, "end", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of(
                        new ScenarioEdge("start", "send", "success"),
                        new ScenarioEdge("send", "expect", "success"),
                        new ScenarioEdge("expect", "end", "success")
                )
        );
    }
}
```

3. Create `fix-flow-engine/src/test/java/com/fixflow/engine/SessionFailureTest.java`:
```java
package com.fixflow.engine;

import com.fixflow.engine.adapter.FakeFixAdapter;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.ExecutionStatus;
import com.fixflow.engine.handler.EndHandler;
import com.fixflow.engine.handler.ExpectFIXHandler;
import com.fixflow.engine.handler.NodeDispatcher;
import com.fixflow.engine.handler.SendFIXHandler;
import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import com.fixflow.engine.publisher.NoOpEventPublisher;
import com.fixflow.engine.routing.MessageBuffer;
import com.fixflow.engine.routing.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.session.FIXSessionManager;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionFailureTest {

    @Test
    void sessionDownFailsAffectedExecutionOnly() throws Exception {
        FakeFixAdapter fake = new FakeFixAdapter();
        MessageBuffer buffer = new MessageBuffer(1000, 60_000L);
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router::route);
        FIXSessionManager sessionManager = new FIXSessionManager(fake);
        fake.setSessionStatusListener(sessionManager::onSessionStatusChange);

        ScenarioRegistry registry = new ScenarioRegistry();
        ExecutionManager manager = new ExecutionManager(
                new NodeDispatcher(List.of(
                        new SendFIXHandler(sessionManager, new VariableResolver()),
                        new ExpectFIXHandler(correlation),
                        new EndHandler()
                )),
                registry,
                new NoOpEventPublisher()
        );
        sessionManager.setExecutionManager(manager);

        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        Scenario s = scenario("s1", "1.0", "REQ-X");
        registry.register(s);

        UUID exA = manager.start(s.id(), sessionA);
        UUID exB = manager.start(s.id(), sessionB);

        Thread.sleep(100);
        fake.simulateSessionDown(sessionA);

        await().atMost(5, SECONDS).until(() -> manager.getStatus(exA) == ExecutionStatus.FAILED);

        // exB completes normally
        fake.injectInbound(sessionB, Map.of(35, "8", 11, "REQ-X"));
        await().atMost(5, SECONDS).until(() -> manager.getStatus(exB) == ExecutionStatus.PASSED);

        assertEquals(ExecutionStatus.FAILED, manager.getStatus(exA));
        assertEquals(ExecutionStatus.PASSED, manager.getStatus(exB));
    }

    private Scenario scenario(String id, String version, String clOrdId) {
        return new Scenario(id, id, "", version, "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "send", null, null, null),
                        new ScenarioNode("send", "Send", "SEND_FIX",
                                Map.of("msgType", "D",
                                        "fields", List.of(Map.of("tag", 11, "value", clOrdId))),
                                null, null, "expect", null, null, null),
                        new ScenarioNode("expect", "Expect", "EXPECT_FIX",
                                Map.of("msgType", "8",
                                        "correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11)),
                                null, null, "end", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of(
                        new ScenarioEdge("start", "send", "success"),
                        new ScenarioEdge("send", "expect", "success"),
                        new ScenarioEdge("expect", "end", "success")
                )
        );
    }
}
```

4. Run:
```bash
mvn test -pl fix-flow-engine
```
Expect all tests PASS.

5. Commit:
```bash
git add fix-flow-engine
git commit -m "test(engine): add hot reload and session failure integration tests"
```

---

## Phase 15: Documentation (Tasks 50-52)

### Task 50: README + local setup guide

**Files:**
- Create: `README.md`
- Create: `docs/setup.md`

**Steps:**

1. Create `README.md`:
````markdown
# FIX Flow Simulator

Visual FIX protocol scenario designer, runtime, and monitor.

## Quick Start

Prerequisites: Java 21+, Maven 3.9+, Node.js 20+.

```bash
# Build everything
mvn clean package -DskipTests

# Run
java -jar fix-flow-api/target/fix-flow-api-1.0.0.jar

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

- `fix-flow-engine` — execution engine, validation, correlation
- `fix-flow-persistence` — H2 + JPA repositories
- `fix-flow-api` — Spring Boot REST + WebSocket + static UI bundle
- `fix-flow-ui` — React + ReactFlow + Tailwind UI

## Documentation

- [Setup guide](docs/setup.md)
- [DSL reference](docs/dsl-reference.md)
- [API reference](docs/api-reference.md)
````

2. Create `docs/setup.md`:
````markdown
# Setup Guide

## Prerequisites

- Java 21 or later (`java -version`)
- Maven 3.9 or later (`mvn -version`)
- Node.js 20 or later (`node --version`) — only needed for UI development

## Production build

```bash
mvn clean package -DskipTests
java -jar fix-flow-api/target/fix-flow-api-1.0.0.jar
```

The fat JAR bundles the React UI build under `/static`. Open
`http://localhost:8080` once the application logs `Started FixFlowApplication`.

## Development mode

Run the backend with hot reload:

```bash
mvn -pl fix-flow-api spring-boot:run
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
````

3. Commit:
```bash
git add README.md docs/setup.md
git commit -m "docs: add README and local setup guide"
```

---

### Task 51: DSL reference + API reference

**Files:**
- Create: `docs/dsl-reference.md`
- Create: `docs/api-reference.md`

**Steps:**

1. Create `docs/dsl-reference.md`:
````markdown
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
| `DECISION` | Branch based on previous result. |
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

## Variable syntax

| placeholder | meaning |
|---|---|
| `{{now}}` | current UTC ISO timestamp |
| `{{uuid}}` | random UUID |
| `{{seq:name}}` | monotonic sequence keyed by `name` |
| `{{env:VAR}}` | environment variable |
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
````

2. Create `docs/api-reference.md`:
````markdown
# REST + WebSocket API

Base URL: `/api/v1`. All requests/responses are JSON unless noted.

## Error format

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Scenario has no START node",
  "details": { "errors": ["Scenario has no START node"] }
}
```

## Scenarios

| Method | Path | Notes |
|---|---|---|
| `GET` | `/scenarios` | list all |
| `GET` | `/scenarios/{id}` | get one |
| `POST` | `/scenarios` | create — body `ScenarioCreateRequest` |
| `PUT` | `/scenarios/{id}` | update — body `ScenarioUpdateRequest` |
| `DELETE` | `/scenarios/{id}` | delete |
| `POST` | `/scenarios/{id}/validate` | returns `{valid, errors[]}` |
| `POST` | `/scenarios/import` | multipart `file` |
| `GET` | `/scenarios/{id}/export` | downloads YAML |
| `POST` | `/scenarios/{id}/execute` | body `{ sessionId }` |
| `POST` | `/scenarios/{id}/reload` | swap in-place |

### Example: create

Request:
```json
POST /api/v1/scenarios
{
  "name": "RFQ",
  "description": "Quote flow",
  "sessionRef": "default",
  "yamlDsl": "id: rfq\nname: RFQ\n..."
}
```
Response:
```json
{
  "id": "8f4...",
  "name": "RFQ",
  "version": "1.0",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z",
  "yamlDsl": "..."
}
```

## Sessions

| Method | Path |
|---|---|
| `GET` | `/sessions` |
| `GET` | `/sessions/{id}` |
| `POST` | `/sessions` |
| `PUT` | `/sessions/{id}` |
| `DELETE` | `/sessions/{id}` |
| `POST` | `/sessions/{id}/connect` |
| `POST` | `/sessions/{id}/disconnect` |
| `GET` | `/sessions/{id}/status` |

### Create body

```json
{
  "name": "default",
  "mode": "INITIATOR",
  "fixVersion": "FIXT_11",
  "defaultApplVerID": "FIX.5.0SP2",
  "senderCompID": "CLIENT",
  "targetCompID": "SERVER",
  "host": "localhost",
  "port": 9876,
  "heartbeatInterval": 30,
  "reconnectInterval": 5,
  "resetOnLogon": true,
  "resetOnLogout": false
}
```

## Executions

| Method | Path |
|---|---|
| `GET` | `/executions/{id}` |
| `GET` | `/executions/{id}/events` |
| `GET` | `/executions/{id}/messages` |
| `GET` | `/executions/{id}/report` |
| `GET` | `/executions/{id}/report/download` |
| `POST` | `/executions/{id}/stop` |

## WebSocket

Endpoint: `http://localhost:8080/ws` (SockJS + STOMP).

Topics:

| Topic | Payload |
|---|---|
| `/topic/executions/{executionId}/events` | `ExecutionEvent` |
| `/topic/executions/{executionId}/messages` | `FIXMessage` |
| `/topic/sessions/{sessionId}/status` | `{sessionId, status, timestamp}` |

### ExecutionEvent payload

```json
{
  "id": "evt-1",
  "executionId": "exec-1",
  "type": "NODE_STARTED",
  "nodeId": "send-nos",
  "timestamp": "2026-01-01T10:00:00Z",
  "detail": null,
  "rawFix": null
}
```

Event types: `SCENARIO_STARTED`, `SCENARIO_PASSED`, `SCENARIO_FAILED`,
`SCENARIO_STOPPED`, `NODE_STARTED`, `NODE_COMPLETED`, `NODE_FAILED`,
`VALIDATION_FAILED`, `MESSAGE_SENT`, `MESSAGE_RECEIVED`, `TIMEOUT`.
````

3. Commit:
```bash
git add docs/dsl-reference.md docs/api-reference.md
git commit -m "docs: add DSL reference and REST/WebSocket API reference"
```

---

### Task 52: Final Maven build — fat JAR + frontend bundled

**Files:**
- Create: `fix-flow-ui/pom.xml`
- Modify: `fix-flow-api/pom.xml`
- Modify: `pom.xml` (root, add `fix-flow-ui` module)

**Steps:**

1. Create `fix-flow-ui/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-parent</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>fix-flow-ui</artifactId>
    <packaging>pom</packaging>

    <properties>
        <node.version>v20.12.2</node.version>
        <npm.version>10.5.0</npm.version>
        <frontend-maven-plugin.version>1.15.0</frontend-maven-plugin.version>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>com.github.eirslett</groupId>
                <artifactId>frontend-maven-plugin</artifactId>
                <version>${frontend-maven-plugin.version}</version>
                <configuration>
                    <installDirectory>target</installDirectory>
                    <workingDirectory>${project.basedir}</workingDirectory>
                </configuration>
                <executions>
                    <execution>
                        <id>install-node-and-npm</id>
                        <goals><goal>install-node-and-npm</goal></goals>
                        <phase>generate-resources</phase>
                        <configuration>
                            <nodeVersion>${node.version}</nodeVersion>
                            <npmVersion>${npm.version}</npmVersion>
                        </configuration>
                    </execution>
                    <execution>
                        <id>npm-install</id>
                        <goals><goal>npm</goal></goals>
                        <phase>generate-resources</phase>
                        <configuration><arguments>install --no-audit --no-fund</arguments></configuration>
                    </execution>
                    <execution>
                        <id>npm-build</id>
                        <goals><goal>npm</goal></goals>
                        <phase>generate-resources</phase>
                        <configuration><arguments>run build</arguments></configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

2. Add `fix-flow-ui` module to root `pom.xml`:
```xml
<modules>
    <module>fix-flow-engine</module>
    <module>fix-flow-persistence</module>
    <module>fix-flow-api</module>
    <module>fix-flow-ui</module>
</modules>
```

3. Modify `fix-flow-api/pom.xml` — add a dependency declaration on `fix-flow-ui` so Maven builds it first, plus a resources plugin that copies the Vite output into `target/classes/static`, and the Spring Boot fat-JAR plugin:
```xml
<dependencies>
    <!-- existing dependencies ... -->
    <dependency>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-ui</artifactId>
        <version>${project.version}</version>
        <type>pom</type>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-resources-plugin</artifactId>
            <version>3.3.1</version>
            <executions>
                <execution>
                    <id>copy-ui-bundle</id>
                    <phase>process-resources</phase>
                    <goals><goal>copy-resources</goal></goals>
                    <configuration>
                        <outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
                        <resources>
                            <resource>
                                <directory>${project.basedir}/../fix-flow-ui/target/dist</directory>
                                <filtering>false</filtering>
                            </resource>
                        </resources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
                <execution>
                    <goals><goal>repackage</goal></goals>
                </execution>
            </executions>
            <configuration>
                <mainClass>com.fixflow.api.FixFlowApplication</mainClass>
            </configuration>
        </plugin>
    </plugins>
</build>
```

4. Run:
```bash
mvn clean package -DskipTests
```
Expect `fix-flow-api/target/fix-flow-api-1.0.0.jar` to exist and exceed 10 MB.

5. Verify:
```bash
ls -lh fix-flow-api/target/fix-flow-api-1.0.0.jar
java -jar fix-flow-api/target/fix-flow-api-1.0.0.jar
```
Open `http://localhost:8080` — UI loads from the embedded static bundle.

6. Final commit:
```bash
git add -A
git commit -m "feat: complete FIX Flow Simulator — full system build + frontend bundled"
```

