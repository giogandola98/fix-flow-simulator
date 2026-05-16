package com.fixflow.engine.fix;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FakeFixAdapterTest {

    @Test
    void capturesSentMessagesAndInjectsInbound() {
        FakeFixAdapter fake = new FakeFixAdapter();
        UUID sid = UUID.randomUUID();

        AtomicReference<Map<Integer, String>> received = new AtomicReference<>();
        fake.setInboundListener((s, f) -> received.set(f));

        fake.sendMessage(sid, Map.of(35, "D", 11, "CL-1"));
        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.getSentMessages().get(0)).containsEntry(11, "CL-1");

        Map<Integer, String> inbound = new HashMap<>();
        inbound.put(35, "8");
        inbound.put(11, "CL-1");
        fake.injectInbound(sid, inbound);

        assertThat(received.get()).containsEntry(35, "8");
    }
}
