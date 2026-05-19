package com.fixflow.engine.execution;

import com.fixflow.core.domain.execution.ExecutionStatus;
import com.fixflow.core.domain.scenario.Scenario;

public interface ScenarioExecutorPort {
    ExecutionStatus execute(Scenario scenario, ExecutionContext ctx) throws InterruptedException;
}
