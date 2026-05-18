package com.fixflow.api;

import com.fixflow.adapters.quickfixj.QuickFIXAdapter;
import com.fixflow.api.support.FakeFixAdapter;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@MockBean(QuickFIXAdapter.class)
class FullApiIntegrationTest {

    @TestConfiguration
    static class FakeAdapterConfig {
        @Bean
        @Primary
        FakeFixAdapter fakeFixAdapter() {
            return new FakeFixAdapter();
        }
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    FakeFixAdapter fakeAdapter;

    @Test
    void fullScenarioLifecycle() {
        // Step 1: create session
        Map<String, Object> sessionReq = Map.ofEntries(
            Map.entry("name", "test-session"),
            Map.entry("mode", "ACCEPTOR"),
            Map.entry("fixVersion", "FIX_44"),
            Map.entry("senderCompID", "SENDER"),
            Map.entry("targetCompID", "TARGET"),
            Map.entry("host", "localhost"),
            Map.entry("port", 9999),
            Map.entry("heartbeatInterval", 30),
            Map.entry("resetOnLogon", false),
            Map.entry("resetOnLogout", false)
        );
        ResponseEntity<Map> sessionResp = rest.postForEntity("/api/v1/sessions", sessionReq, Map.class);
        assertThat(sessionResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String sessionId = (String) sessionResp.getBody().get("id");
        assertThat(sessionId).isNotNull();

        // Step 2: create scenario
        String yaml = """
                id: null
                name: e2e-test
                version: "1"
                sessionRef: %s
                nodes:
                  - id: start
                    type: START
                    onSuccess: send
                  - id: send
                    type: SEND_FIX
                    config:
                      fields:
                        "35": D
                        "11": ORDER-1
                    onSuccess: expect
                  - id: expect
                    type: EXPECT_FIX
                    config:
                      correlationTag: 11
                      expectedValue: ORDER-1
                    timeout:
                      value: 3000
                      unit: MILLISECONDS
                      onTimeout: FAIL
                    onSuccess: end
                    onFailure: failend
                  - id: end
                    type: END_PASS
                  - id: failend
                    type: END_FAIL
                """.formatted(sessionId);

        Map<String, Object> scenarioReq = Map.of("yamlDsl", yaml);
        ResponseEntity<Map> scenarioResp = rest.postForEntity("/api/v1/scenarios", scenarioReq, Map.class);
        assertThat(scenarioResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String scenarioId = (String) scenarioResp.getBody().get("id");
        assertThat(scenarioId).isNotNull();

        // Step 3: connect session
        ResponseEntity<Void> connectResp = rest.exchange(
            "/api/v1/sessions/" + sessionId + "/connect",
            org.springframework.http.HttpMethod.PUT,
            null, Void.class);
        assertThat(connectResp.getStatusCode().is2xxSuccessful()).isTrue();

        // Step 4: start execution
        Map<String, Object> execReq = Map.of("sessionId", sessionId);
        ResponseEntity<Map> execResp = rest.postForEntity(
            "/api/v1/scenarios/" + scenarioId + "/execute", execReq, Map.class);
        assertThat(execResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String execId = execResp.getBody().get("executionId").toString();
        assertThat(execId).isNotNull();

        // Step 5: inject inbound message to satisfy EXPECT_FIX
        // Give engine a moment to reach the EXPECT_FIX node
        await().atMost(2, TimeUnit.SECONDS)
               .pollInterval(50, TimeUnit.MILLISECONDS)
               .until(() -> !fakeAdapter.getSent().isEmpty());

        fakeAdapter.injectInbound(UUID.fromString(sessionId), Map.of(11, "ORDER-1", 35, "8"));

        // Step 6: await PASSED status via REST
        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   ResponseEntity<Map> status = rest.getForEntity(
                       "/api/v1/executions/" + execId, Map.class);
                   assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
                   assertThat(status.getBody().get("status")).isEqualTo("PASSED");
               });
    }
}
