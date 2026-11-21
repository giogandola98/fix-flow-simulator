package com.fixflow.engine.pack;

import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.session.FIXSessionConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackExporterTest {

    private static PackContents read(byte[] zip) {
        return PackReader.readZip(new ByteArrayInputStream(zip));
    }

    private static Map<String, String> text(PackContents c) {
        Map<String, String> m = new TreeMap<>();
        m.put("pack.yaml", c.manifestYaml());
        c.datasets().forEach((k, v) -> m.put("datasets/" + k, v));
        c.scenarios().forEach((k, v) -> m.put("scenarios/" + k, v));
        c.sessions().forEach((k, v) -> m.put("sessions/" + k, v));
        c.dictionaries().forEach((k, v) -> m.put("dictionaries/" + k, new String(v, StandardCharsets.UTF_8)));
        return m;
    }

    @Test
    void export_then_import_is_identity() {
        PackFakes original = new PackFakes();
        original.importer().importPack(new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)), PackFakes.demoValues());
        Map<UUID, String> yamlBefore = original.scenarios.findAll().stream()
            .collect(Collectors.toMap(Scenario::id, Scenario::rawYaml));
        List<FIXSessionConfig> sessionsBefore = original.sessions.findAll();
        List<DataSet> dataSetsBefore = original.dataSets.findAll();

        byte[] exported = original.exporter().export("demo");
        PackContents c = read(exported);

        // names, not ids, inside the pack
        assertThat(c.dictionaries()).containsOnlyKeys("FIX50SP2-DEMO.xml");
        assertThat(c.dictionaries().get("FIX50SP2-DEMO.xml"))
            .isEqualTo(PackReader.readFolder(PackFakes.DEMO).dictionaries().get("FIX50SP2-DEMO.xml"));
        assertThat(c.datasets()).containsOnlyKeys("instruments.csv");
        assertThat(c.scenarios()).containsOnlyKeys("demo-main.yaml", "demo-send-quote-request.yaml");
        String main = c.scenarios().get("demo-main.yaml");
        assertThat(main).contains("targetScenario: demo-send-quote-request")
            .contains("sessionRef: demo-acceptor").contains("datasetRef: instruments")
            .doesNotContain("targetScenarioId").doesNotContain("id: " + yamlBefore.keySet().iterator().next());
        assertThat(c.sessions().get("demo-acceptor.yaml"))
            .contains("appDataDictionary: FIX50SP2-DEMO.xml").contains("senderCompID: SIM")
            .doesNotContain("packId").doesNotContain("demo__");
        assertThat(c.manifestYaml()).contains("id: demo").contains("version: 1.0.0");

        // re-import into the same engine: every object unchanged, same ids
        original.importer().importPack(new ByteArrayInputStream(exported), Map.of());
        assertThat(original.scenarios.findAll().stream().collect(Collectors.toMap(Scenario::id, Scenario::rawYaml)))
            .isEqualTo(yamlBefore);
        assertThat(original.sessions.findAll()).isEqualTo(sessionsBefore);
        assertThat(original.dataSets.findAll()).isEqualTo(dataSetsBefore);

        // import into an empty engine, export again: the same pack
        PackFakes fresh = new PackFakes();
        fresh.importer().importPack(new ByteArrayInputStream(exported), Map.of());
        assertThat(text(read(fresh.exporter().export("demo")))).isEqualTo(text(c));
        Map<String, FIXSessionConfig> byName = fresh.sessions.findAll().stream()
            .collect(Collectors.toMap(FIXSessionConfig::name, Function.identity()));
        FIXSessionConfig before = sessionsBefore.get(0);
        FIXSessionConfig after = byName.get(before.name());
        assertThat(after).usingRecursiveComparison().ignoringFields("id").isEqualTo(before);
    }

    @Test
    void export_unknown_pack_is_not_found() {
        assertThatThrownBy(() -> new PackFakes().exporter().export("nope"))
            .isInstanceOf(NoSuchElementException.class);
    }
}
