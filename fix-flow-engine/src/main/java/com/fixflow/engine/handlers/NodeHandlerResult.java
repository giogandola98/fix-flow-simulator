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
