package com.fixflow.engine.pack;

/** A pack operation that clashes with the engine's current state (answered 409). */
public class PackConflictException extends RuntimeException {
    public PackConflictException(String message) { super(message); }
}
