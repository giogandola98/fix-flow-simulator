package com.fixflow.engine.variable;

import com.fixflow.engine.execution.ExecutionContext;

public interface VariableResolverPlugin {
    boolean supports(String expression);
    String resolve(String expression, ExecutionContext ctx);
}
