package com.fixflow.core.domain.pack;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackDomainTest {

    private static PackManifest manifest(String id, List<PackManifest.Value> values) {
        return new PackManifest(id, "Demo", "1.0.0", "Acme", "0.8.0", "d", values);
    }

    @Test
    void manifestRequiresIdNameVersion() {
        assertThatThrownBy(() -> manifest("Bad_Id", List.of())).hasMessageContaining("[a-z0-9-]+");
        assertThatThrownBy(() -> manifest(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PackManifest("demo", " ", "1", null, null, null, null))
            .hasMessageContaining("name");
        assertThatThrownBy(() -> new PackManifest("demo", "Demo", null, null, null, null, null))
            .hasMessageContaining("version");
        assertThat(new PackManifest("demo", "Demo", "1", null, null, null, null).values()).isEmpty();
    }

    @Test
    void valuesAreCheckedAndCopied() {
        assertThatThrownBy(() -> new PackManifest.Value("1X", null, null)).hasMessageContaining("value name");
        assertThatThrownBy(() -> manifest("demo",
            List.of(new PackManifest.Value("A", null, null), new PackManifest.Value("A", null, "1"))))
            .hasMessageContaining("twice");
        List<PackManifest.Value> values = new ArrayList<>(List.of(new PackManifest.Value("A", "a", null),
            new PackManifest.Value("B", "b", "2")));
        PackManifest m = manifest("demo", values);
        values.clear();
        assertThat(m.values()).hasSize(2);
        assertThat(m.values().get(0).required()).isTrue();
        assertThat(m.values().get(1).required()).isFalse();
    }

    @Test
    void installedPackCopiesValuesAndExposesId() {
        Map<String, String> values = new HashMap<>(Map.of("A", "1"));
        Instant now = Instant.now();
        InstalledPack p = new InstalledPack(manifest("demo", List.of()), values, now, now);
        values.put("B", "2");
        assertThat(p.id()).isEqualTo("demo");
        assertThat(p.values()).containsOnlyKeys("A");
        assertThat(new InstalledPack(manifest("demo", List.of()), null, now, now).values()).isEmpty();
        assertThatThrownBy(() -> new InstalledPack(null, Map.of(), now, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importResultCopiesWarnings() {
        assertThat(new PackImportResult("demo", "1", 1, 2, 3, 4, null).warnings()).isEmpty();
        assertThat(new PackImportResult("demo", "1", 1, 2, 3, 4, List.of("w")).warnings()).containsExactly("w");
    }
}
