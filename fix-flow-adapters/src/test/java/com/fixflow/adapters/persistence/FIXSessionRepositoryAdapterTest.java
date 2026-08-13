package com.fixflow.adapters.persistence;

import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FIXSessionRepositoryAdapterTest {

    @Autowired FIXSessionRepositoryAdapter adapter;

    private FIXSessionConfig config(UUID id, String name, FIXMode mode) {
        return new FIXSessionConfig(id, name, mode, FIXVersion.FIXT_11, "9",
                "SENDER", "TARGET", "localhost", 9001, 30, true, false);
    }

    @Test
    void saveAndFindByIdMapsAllFields() {
        UUID id = UUID.randomUUID();
        adapter.save(config(id, "acc", FIXMode.ACCEPTOR));

        FIXSessionConfig loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.name()).isEqualTo("acc");
        assertThat(loaded.mode()).isEqualTo(FIXMode.ACCEPTOR);
        assertThat(loaded.fixVersion()).isEqualTo(FIXVersion.FIXT_11);
        assertThat(loaded.defaultApplVerID()).isEqualTo("9");
        assertThat(loaded.senderCompID()).isEqualTo("SENDER");
        assertThat(loaded.targetCompID()).isEqualTo("TARGET");
        assertThat(loaded.host()).isEqualTo("localhost");
        assertThat(loaded.port()).isEqualTo(9001);
        assertThat(loaded.heartbeatInterval()).isEqualTo(30);
        assertThat(loaded.resetOnLogon()).isTrue();
        assertThat(loaded.resetOnLogout()).isFalse();
    }

    @Test
    void findByIdEmptyWhenAbsent() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void saveIsUpsert() {
        UUID id = UUID.randomUUID();
        adapter.save(config(id, "first", FIXMode.INITIATOR));
        adapter.save(config(id, "second", FIXMode.ACCEPTOR));

        assertThat(adapter.findAll()).hasSize(1);
        FIXSessionConfig loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("second");
        assertThat(loaded.mode()).isEqualTo(FIXMode.ACCEPTOR);
    }

    @Test
    void findAllReturnsAllSaved() {
        adapter.save(config(UUID.randomUUID(), "a", FIXMode.INITIATOR));
        adapter.save(config(UUID.randomUUID(), "b", FIXMode.ACCEPTOR));

        List<FIXSessionConfig> all = adapter.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(FIXSessionConfig::name).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void deleteRemovesSession() {
        UUID id = UUID.randomUUID();
        adapter.save(config(id, "acc", FIXMode.ACCEPTOR));
        adapter.delete(id);

        assertThat(adapter.findById(id)).isEmpty();
    }
}
