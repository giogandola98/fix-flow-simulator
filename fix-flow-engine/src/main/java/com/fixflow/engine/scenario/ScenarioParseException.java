// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.engine.scenario;

/**
 * The YAML handed to {@link ScenarioDslParser#parseYaml(String)} could not be read as a
 * scenario — malformed document, or a value that does not fit the DSL.
 *
 * <p>Distinct from {@code UncheckedIOException} on purpose (issue #105): parsing a
 * {@code String} never fails for I/O reasons, so an unreadable document is bad input, not a
 * server fault, and the API answers 400 rather than 500. The message names the position
 * reported by the parser so the caller can find the offending line.
 */
public class ScenarioParseException extends RuntimeException {

    public ScenarioParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
