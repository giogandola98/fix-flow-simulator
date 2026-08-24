package com.fixflow.api.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemController.class)
@Import(TestWebConfig.class)
class SystemControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void shutdownReturnsAcceptedImmediately() throws Exception {
        mvc.perform(post("/api/v1/system/shutdown")).andExpect(status().isAccepted());
    }

    @Test
    void shutdownHookIsInvokedExactlyOnce() throws Exception {
        // Capture into a future rather than a pre-zeroed counter: an AtomicInteger
        // starting at 0 would make "assertEquals(0, ...)" pass whether or not the
        // exit hook ever ran. Completing the future is proof the hook actually fired.
        AtomicInteger invocations = new AtomicInteger();
        CompletableFuture<Integer> firstExitCode = new CompletableFuture<>();
        SystemController controller = new SystemController(null, code -> {
            invocations.incrementAndGet();
            firstExitCode.complete(code);
        });

        controller.shutdown();
        controller.shutdown(); // second call on the same instance must be a no-op

        // Await the future instead of racing a longer sleep against the controller's
        // internal delay: this blocks exactly as long as needed, no more, no less.
        Integer code = firstExitCode.get(5, TimeUnit.SECONDS);
        assertEquals(0, code, "exit code 0");

        // The first call's internal delay has now definitely elapsed (the future
        // would not have completed otherwise). Both shutdown() calls were issued
        // back-to-back, so if the second one had wrongly started its own exit
        // thread, it would be on (or past) the same schedule as the first — this
        // short buffer gives a straggler invocation a chance to land before the
        // count is checked, without making the primary assertion depend on timing.
        Thread.sleep(100);
        assertEquals(1, invocations.get(), "a second shutdown() call must not start a second exit");
    }
}
