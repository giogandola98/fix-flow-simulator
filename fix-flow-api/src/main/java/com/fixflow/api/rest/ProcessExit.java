package com.fixflow.api.rest;

import org.springframework.stereotype.Component;

import java.util.function.IntConsumer;

/**
 * The real process-exit action for {@link SystemController}, wired in as a bean rather than
 * hard-coded so that a test can supply a capturing replacement instead.
 *
 * <p>{@code @WebMvcTest} slices only auto-detect controller-related beans (controllers,
 * {@code @ControllerAdvice}, converters, filters, {@code WebMvcConfigurer}, ...) — a plain
 * {@code @Component} like this one is never pulled into such a slice unless a test explicitly
 * imports it. That means a {@code @WebMvcTest} for {@link SystemController} that does not
 * provide its own {@code IntConsumer} bean simply fails to start (no bean to satisfy the
 * constructor parameter) rather than silently falling back to this — the real — exit action.
 * There is no code path by which a test can reach this class without asking for it by name.
 */
@Component
class ProcessExit implements IntConsumer {

    @Override
    public void accept(int code) {
        System.exit(code);
    }
}
