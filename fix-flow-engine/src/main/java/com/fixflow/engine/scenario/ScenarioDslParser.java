package com.fixflow.engine.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fixflow.core.domain.scenario.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScenarioDslParser {

    private final ObjectMapper mapper;

    public ScenarioDslParser() {
        YAMLFactory yf = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.mapper = new ObjectMapper(yf)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Scenario parseYaml(String yaml) {
        try {
            ScenarioDto dto = mapper.readValue(yaml, ScenarioDto.class);
            return dto.toDomain(yaml);
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
        Scenario toDomain(String rawYaml) {
            return new Scenario(
                    id != null ? id : java.util.UUID.randomUUID(),
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
                                    Map.Entry::getKey, e -> e.getValue().toDomain())),
                    rawYaml
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
        RoutingRule toDomain() { return new RoutingRule(criteria == null ? Map.of() : criteria, scenarioId, priority); }
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
