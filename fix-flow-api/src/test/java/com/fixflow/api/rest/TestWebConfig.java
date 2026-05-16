package com.fixflow.api.rest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration for web-layer slice tests.
 * Lives in the same package as the controller tests so @WebMvcTest prefers it
 * over FixFlowApplication (which enables JPA/datasource).
 */
@SpringBootApplication(
    scanBasePackageClasses = TestWebConfig.class
)
class TestWebConfig {
}
