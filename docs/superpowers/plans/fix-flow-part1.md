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

*Part 1 of 3 — continues in fix-flow-part2.md (Phases 6-9)*
