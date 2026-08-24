package com.fixflow.api.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * Lets the UI stop the simulator without reaching for Task Manager.
 *
 * <p>QuickFIX/J acceptor threads are non-daemon, so closing the Spring context is not enough
 * to end the JVM — {@code System.exit} is. The response is sent first and the exit happens on
 * a separate thread after a short delay, so the browser sees 202 rather than a dropped socket.
 *
 * <p>The actual exit call is delegated to an injected {@link IntConsumer} ({@link ProcessExit}
 * in production) rather than hard-coded to {@code System::exit}, so a test can supply a
 * capturing replacement — see {@link ProcessExit} for why that makes it safe to drive this
 * controller through a real Spring context in tests.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final ApplicationContext context;
    private final IntConsumer exit;

    @Autowired
    SystemController(ApplicationContext context, IntConsumer exit) {
        this.context = context;
        this.exit = exit;
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Void> shutdown() {
        if (started.compareAndSet(false, true)) {
            Thread t = new Thread(this::closeAndExit, "fixflow-shutdown");
            t.setDaemon(false);
            t.start();
        }
        return ResponseEntity.accepted().build();
    }

    private void closeAndExit() {
        try {
            Thread.sleep(400);   // let the 202 flush to the browser
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int code = context == null ? 0 : SpringApplication.exit(context, () -> 0);
        exit.accept(code);
    }
}
