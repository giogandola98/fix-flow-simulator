package com.fixflow.adapters.persistence;

import com.fixflow.core.domain.pack.InstalledPack;
import com.fixflow.core.domain.pack.PackManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PackRepositoryAdapterTest {

    @Autowired PackRepositoryAdapter adapter;

    private static InstalledPack pack(String version) {
        PackManifest m = new PackManifest("demo", "Demo", version, "Acme", "0.8.0", "d",
            List.of(new PackManifest.Value("SENDER", "s", null), new PackManifest.Value("PORT", "p", "9876")));
        Instant t = Instant.parse("2026-11-21T10:00:00Z");
        return new InstalledPack(m, Map.of("SENDER", "SIM", "PORT", "9876"), t, t.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void saveAndFindRoundTripsManifestAndValues() {
        adapter.save(pack("1.0.0"));
        assertThat(adapter.findById("demo")).contains(pack("1.0.0"));
        assertThat(adapter.findAll()).containsExactly(pack("1.0.0"));
    }

    @Test
    void saveIsUpsertAndDeleteRemoves() {
        adapter.save(pack("1.0.0"));
        adapter.save(pack("1.1.0"));
        assertThat(adapter.findAll()).singleElement()
            .extracting(p -> p.manifest().version()).isEqualTo("1.1.0");
        adapter.delete("demo");
        assertThat(adapter.findById("demo")).isEmpty();
    }
}
