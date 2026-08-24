package com.fixflow.api.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} builds {@link SystemController} through Spring's normal constructor
 * injection, so its {@code IntConsumer} exit seam has to be satisfied by some bean. That bean
 * is the {@code @MockBean} below, not the production {@link ProcessExit} — {@code @WebMvcTest}
 * only auto-detects controller-related beans (see {@link ProcessExit}'s javadoc), so the real,
 * process-killing bean is never a candidate here regardless of what this test does or doesn't
 * import; without an explicit {@code IntConsumer} bean the context would simply fail to start.
 * That is what makes it safe to drive a real POST through MockMvc: there is no path from this
 * test to a genuine {@code System.exit}, and the mock's default (no-op) {@code accept(int)}
 * means the background thread this endpoint starts has nothing dangerous left to do.
 *
 * <p>This test deliberately does NOT await that background thread. The controller's
 * {@code closeAndExit()} still, for real, calls {@code SpringApplication.exit(context, ...)}
 * on the injected {@code ApplicationContext} ~400ms after the POST — that part of the
 * production behaviour is unchanged and untouched by the exit-hook seam above. Against a
 * {@code @WebMvcTest} slice that context is this test's own, and blocking here for it to be
 * closed races Spring's own post-test listeners (which also touch the context) and reliably
 * fails with "ApplicationContext ... is not active" — a real failure this fix uncovered,
 * previously masked because the un-fixed endpoint called a genuine {@code System.exit} that
 * killed the whole JVM before anyone could observe the race. The exact-once/code-0 proof
 * belongs to {@link #shutdownHookIsInvokedExactlyOnce()} below, which uses a directly
 * constructed controller with a null context so {@code SpringApplication.exit} never runs and
 * there is no shared Spring context to race against.
 *
 * <p>{@code @DirtiesContext} per method: even without awaiting it here, that background thread
 * still asynchronously closes this test's context a few hundred milliseconds later. Without
 * this annotation Spring would cache and reuse that same context for this class's other
 * {@code @Test} method — and that method's own ~500ms internal wait is long enough to overlap
 * the window in which the stale context gets closed out from under it. Discarding the context
 * after every method gives each method its own, guaranteed-fresh context.
 */
@WebMvcTest(controllers = SystemController.class)
@Import(TestWebConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemControllerTest {

    @Autowired MockMvc mvc;
    @MockBean IntConsumer exit;

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
