package com.fixflow.api.config;

import com.fixflow.api.exception.SessionConflictException;
import com.fixflow.api.rest.dto.ErrorResponse;
import com.fixflow.engine.scenario.ScenarioParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final DatabaseAvailability database;

    /**
     * Single constructor on purpose: with two, Spring would silently pick the no-arg one and the
     * advice would end up with its own {@link DatabaseAvailability} instead of the shared bean.
     */
    public GlobalExceptionHandler(DatabaseAvailability database) {
        this.database = database;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(
            ErrorResponse.of(404, "Not Found", ex.getMessage())
        );
    }

    /**
     * A request for a path no controller and no static resource matches. Spring signals this
     * with {@code NoResourceFoundException} (or {@code NoHandlerFoundException} when configured
     * to throw); without these handlers it fell through to {@link #handleGeneric} and a typo in
     * a URL was reported as a server fault — see issue #103.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(
            ErrorResponse.of(404, "Not Found",
                "No endpoint for " + ex.getHttpMethod() + " /" + ex.getResourcePath())
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
        return ResponseEntity.status(404).body(
            ErrorResponse.of(404, "Not Found",
                "No endpoint for " + ex.getHttpMethod() + " " + ex.getRequestURL())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400).body(
            ErrorResponse.of(400, "Bad Request", ex.getMessage())
        );
    }

    /**
     * YAML the caller supplied that the DSL parser cannot read. The message carries the line and
     * column the parser reported; without this it fell through to {@link #handleGeneric} and a
     * bad request body was reported as a server fault — see issue #105.
     */
    @ExceptionHandler(ScenarioParseException.class)
    public ResponseEntity<ErrorResponse> handleScenarioParse(ScenarioParseException ex) {
        return ResponseEntity.status(400).body(
            ErrorResponse.of(400, "Bad Request", ex.getMessage())
        );
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequestSpring(Exception ex) {
        return ResponseEntity.status(400).body(
            ErrorResponse.of(400, "Bad Request", ex.getMessage())
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405).body(
            ErrorResponse.of(405, "Method Not Allowed", ex.getMessage())
        );
    }

    @ExceptionHandler(SessionConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(SessionConflictException ex) {
        return ResponseEntity.status(409).body(
            ErrorResponse.of(409, "Conflict", ex.getMessage())
        );
    }

    /**
     * Anything unhandled. A dead H2 store is separated out here and answered {@code 503} with
     * the real cause: it is not a per-request fault, it is permanent until restart, and a
     * caller — a human or a test harness — needs to be able to tell the two apart.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        if (database.recordIfFatal(ex)) {
            return ResponseEntity.status(503).body(
                ErrorResponse.of(503, "Service Unavailable",
                    "Database is unavailable and cannot recover: " + database.failureReason()
                        + ". Restart the simulator.")
            );
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(
            ErrorResponse.of(500, "Internal Server Error", "Internal server error")
        );
    }
}
