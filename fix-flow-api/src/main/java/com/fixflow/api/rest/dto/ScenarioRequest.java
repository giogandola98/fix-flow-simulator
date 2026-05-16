package com.fixflow.api.rest.dto;

public record ScenarioRequest(String name, String description, String sessionRef, String yamlDsl) {}
