package com.fixflow.engine.scenario;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioEdge;
import com.fixflow.core.domain.scenario.ScenarioNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the structural integrity of a {@link Scenario} before execution.
 * Returns a list of error messages; an empty list means the scenario is valid.
 */
@Component
public class ScenarioValidator {

    public List<String> validate(Scenario scenario) {
        List<String> errors = new ArrayList<>();

        // Collect all node ids and check for duplicates
        Set<String> nodeIds = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (ScenarioNode node : scenario.nodes()) {
            if (!nodeIds.add(node.id())) {
                duplicates.add(node.id());
            }
        }
        for (String dup : duplicates) {
            errors.add("Duplicate node id: " + dup);
        }

        // At least one START node required
        boolean hasStart = scenario.nodes().stream()
                .anyMatch(n -> n.type() == NodeType.START);
        if (!hasStart) {
            errors.add("Scenario has no START node");
        }

        // Validate onSuccess / onFailure / onTimeout references
        for (ScenarioNode node : scenario.nodes()) {
            if (node.onSuccess() != null && !nodeIds.contains(node.onSuccess())) {
                errors.add("Node '" + node.id() + "' onSuccess references unknown node: " + node.onSuccess());
            }
            if (node.onFailure() != null && !nodeIds.contains(node.onFailure())) {
                errors.add("Node '" + node.id() + "' onFailure references unknown node: " + node.onFailure());
            }
            if (node.onTimeout() != null && !nodeIds.contains(node.onTimeout())) {
                errors.add("Node '" + node.id() + "' onTimeout references unknown node: " + node.onTimeout());
            }
        }

        // Validate edge from/to references
        for (ScenarioEdge edge : scenario.edges()) {
            if (!nodeIds.contains(edge.from())) {
                errors.add("Edge from unknown node: " + edge.from());
            }
            if (!nodeIds.contains(edge.to())) {
                errors.add("Edge to unknown node: " + edge.to());
            }
        }

        return List.copyOf(errors);
    }
}
