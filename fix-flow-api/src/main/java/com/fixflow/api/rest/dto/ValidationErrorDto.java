package com.fixflow.api.rest.dto;

import java.util.List;

public record ValidationErrorDto(boolean valid, List<String> errors) {}
