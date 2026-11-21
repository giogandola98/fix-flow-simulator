package com.fixflow.api.rest;

import com.fixflow.engine.operator.OperatorGateway;
import com.fixflow.engine.operator.OperatorGateway.OpenPrompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Lets a person answer the prompt of an OPERATOR node that is waiting. */
@RestController
public class OperatorController {

    public record OperatorAnswerRequest(boolean ok, String note) {}

    private final OperatorGateway gateway;

    public OperatorController(OperatorGateway gateway) { this.gateway = gateway; }

    @GetMapping("/api/v1/executions/{id}/operator")
    public ResponseEntity<OpenPrompt> prompt(@PathVariable UUID id) {
        return gateway.open(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/executions/{id}/operator/{nodeId}")
    public ResponseEntity<Void> answer(@PathVariable UUID id, @PathVariable String nodeId,
                                       @RequestBody OperatorAnswerRequest body) {
        return gateway.answer(id, nodeId, body.ok(), body.note())
                ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
