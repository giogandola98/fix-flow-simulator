package com.fixflow.api.config;

import com.fixflow.api.exception.SessionConflictException;
import com.fixflow.api.rest.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ---- direct unit tests of each handler method ----

    @Test
    void handleNotFoundMaps404() {
        ResponseEntity<ErrorResponse> r = handler.handleNotFound(new NoSuchElementException("gone"));
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody().status()).isEqualTo(404);
        assertThat(r.getBody().message()).isEqualTo("gone");
    }

    @Test
    void handleBadRequestMaps400() {
        ResponseEntity<ErrorResponse> r = handler.handleBadRequest(new IllegalArgumentException("nope"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().message()).isEqualTo("nope");
    }

    @Test
    void handleBadRequestSpringMaps400() {
        ResponseEntity<ErrorResponse> r = handler.handleBadRequestSpring(
            new MissingServletRequestParameterException("file", "MultipartFile"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void handleMethodNotAllowedMaps405() {
        ResponseEntity<ErrorResponse> r = handler.handleMethodNotAllowed(
            new HttpRequestMethodNotSupportedException("DELETE"));
        assertThat(r.getStatusCode().value()).isEqualTo(405);
    }

    @Test
    void handleConflictMaps409() {
        ResponseEntity<ErrorResponse> r = handler.handleConflict(new SessionConflictException("busy"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().message()).isEqualTo("busy");
    }

    @Test
    void handleGenericMaps500WithFixedMessageNoLeak() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(
            new RuntimeException("secret internal detail with stack info"));
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        // Must not leak the exception message.
        assertThat(r.getBody().message()).isEqualTo("Internal server error");
        assertThat(r.getBody().message()).doesNotContain("secret");
    }

    // ---- HTTP dispatch tests: exercise framework-thrown exceptions through the advice ----

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void noSuchElementReturns404() throws Exception {
        mvc.perform(get("/t/notfound"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void illegalArgumentReturns400() throws Exception {
        mvc.perform(get("/t/badarg"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void sessionConflictReturns409() throws Exception {
        mvc.perform(get("/t/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void genericReturns500WithoutLeakingMessage() throws Exception {
        mvc.perform(get("/t/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/t/body")
                .contentType("application/json")
                .content("{ this is not valid json "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingRequiredParamReturns400() throws Exception {
        mvc.perform(get("/t/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void typeMismatchPathVariableReturns400() throws Exception {
        mvc.perform(get("/t/uuid/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        // /t/getonly only supports GET
        mvc.perform(post("/t/getonly"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
    }

    @RestController
    static class TestController {
        @GetMapping("/t/notfound")
        String notFound() { throw new NoSuchElementException("missing"); }

        @GetMapping("/t/badarg")
        String bad() { throw new IllegalArgumentException("bad"); }

        @GetMapping("/t/conflict")
        String conflict() { throw new SessionConflictException("busy"); }

        @GetMapping("/t/boom")
        String boom() { throw new RuntimeException("kaboom-secret"); }

        @PostMapping("/t/body")
        String body(@RequestBody java.util.Map<String, Object> b) { return "ok"; }

        @GetMapping("/t/param")
        String param(@RequestParam("q") String q) { return q; }

        @GetMapping("/t/uuid/{id}")
        String uuid(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) { return id.toString(); }

        @GetMapping("/t/getonly")
        String getOnly() { return "ok"; }
    }
}
