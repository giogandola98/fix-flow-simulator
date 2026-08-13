package com.fixflow.api.rest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration for web-layer slice tests.
 * Lives in the same package as the controller tests so @WebMvcTest prefers it
 * over FixFlowApplication (which enables JPA / datasource auto-config).
 * Only scans com.fixflow.api.rest, so config beans (e.g. GlobalExceptionHandler)
 * must be pulled in explicitly with @Import in each slice test.
 */
@SpringBootApplication(scanBasePackageClasses = TestWebConfig.class)
class TestWebConfig {
}
