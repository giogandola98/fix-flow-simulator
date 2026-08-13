package com.fixflow.engine.handlers;

import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.TimeoutAction;
import com.fixflow.engine.execution.ExecutionContext;
import com.fixflow.engine.support.Fixtures;
import org.junit.jupiter.api.Test;

import static com.fixflow.engine.support.Fixtures.node;
import static com.fixflow.engine.support.Fixtures.scenario;
import static com.fixflow.engine.support.Fixtures.start;
import static com.fixflow.engine.support.Fixtures.timeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelayWaitHandlerTest {

    private ExecutionContext ctx(Scenario s) { return Fixtures.ctx(s); }

    @Test
    void delaySupportsDelayType() {
        assertThat(new DelayHandler().getSupportedType()).isEqualTo(NodeType.DELAY);
    }

    @Test
    void delayZeroReturnsImmediatelySuccess() throws Exception {
        Scenario s = scenario("s", start("d"), node("d", NodeType.DELAY).cfg("delayMs", 0L).onSuccess("n").build());
        NodeHandlerResult r = new DelayHandler().handle(s.findNode("d").get(), ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("n");
    }

    @Test
    void delayNullConfigTreatedAsZero() throws Exception {
        Scenario s = scenario("s", start("d"), node("d", NodeType.DELAY).onSuccess("n").build());
        assertThat(new DelayHandler().handle(s.findNode("d").get(), ctx(s)).success()).isTrue();
    }

    @Test
    void delayShortSleepStillSucceeds() throws Exception {
        Scenario s = scenario("s", start("d"), node("d", NodeType.DELAY).cfg("delayMs", 5L).onSuccess("n").build());
        assertThat(new DelayHandler().handle(s.findNode("d").get(), ctx(s)).success()).isTrue();
    }

    @Test
    void delayInterruptPropagates() {
        Scenario s = scenario("s", start("d"), node("d", NodeType.DELAY).cfg("delayMs", 60_000L).onSuccess("n").build());
        Thread.currentThread().interrupt(); // pre-set flag -> Thread.sleep throws immediately
        assertThatThrownBy(() -> new DelayHandler().handle(s.findNode("d").get(), ctx(s)))
                .isInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isFalse(); // clear for subsequent tests
    }

    @Test
    void waitSupportsWaitType() {
        assertThat(new WaitHandler().getSupportedType()).isEqualTo(NodeType.WAIT);
    }

    @Test
    void waitWithNoTimeoutReturnsImmediately() throws Exception {
        Scenario s = scenario("s", start("w"), node("w", NodeType.WAIT).onSuccess("n").build());
        NodeHandlerResult r = new WaitHandler().handle(s.findNode("w").get(), ctx(s));
        assertThat(r.success()).isTrue();
        assertThat(r.nextNodeId()).isEqualTo("n");
    }

    @Test
    void waitWithTimeoutSleepsThenSucceeds() throws Exception {
        Scenario s = scenario("s", start("w"),
                node("w", NodeType.WAIT).timeout(timeout(5, TimeoutAction.CONTINUE, null)).onSuccess("n").build());
        assertThat(new WaitHandler().handle(s.findNode("w").get(), ctx(s)).success()).isTrue();
    }

    @Test
    void waitInterruptPropagates() {
        Scenario s = scenario("s", start("w"),
                node("w", NodeType.WAIT).timeout(timeout(60_000, TimeoutAction.CONTINUE, null)).onSuccess("n").build());
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> new WaitHandler().handle(s.findNode("w").get(), ctx(s)))
                .isInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isFalse();
    }
}
