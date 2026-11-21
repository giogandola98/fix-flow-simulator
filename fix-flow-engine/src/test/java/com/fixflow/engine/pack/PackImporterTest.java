package com.fixflow.engine.pack;

import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.pack.PackImportResult;
import com.fixflow.core.domain.scenario.NodeType;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.scenario.ScenarioNode;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.domain.session.FIXVersion;
import com.fixflow.engine.handlers.EndHandler;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.handlers.NodeHandlerResult;
import com.fixflow.engine.support.ProgrammableHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PackImporterTest {

    private final PackFakes f = new PackFakes();

    private PackImportResult importDemo(PackFakes fakes, Map<String, String> override, List<String> drop) {
        return fakes.importer().importPack(
            new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO, override, drop)), PackFakes.demoValues());
    }

    private PackImportResult importDemo() { return importDemo(f, Map.of(), List.of()); }

    private Scenario scenario(String name) {
        return f.scenarios.findAll().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void import_creates_all_objects_tagged() {
        PackImportResult r = importDemo();

        assertThat(r.packId()).isEqualTo("demo");
        assertThat(r.version()).isEqualTo("1.0.0");
        assertThat(r.scenarios()).isEqualTo(2);
        assertThat(r.datasets()).isEqualTo(1);
        assertThat(r.sessions()).isEqualTo(1);
        assertThat(r.dictionaries()).isEqualTo(1);

        assertThat(f.scenarios.findAll()).hasSize(2).allMatch(s -> "demo".equals(s.packId()));
        assertThat(f.dataSets.findAll()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("instruments");
            assertThat(d.packId()).isEqualTo("demo");
            assertThat(d.columns()).containsExactly("isin", "qty", "side");
            assertThat(d.rows()).hasSize(2);
            assertThat(d.rows().get(1)).containsExactly("XS0000000002", "2,500", "2");
        });
        FIXSessionConfig s = f.sessions.byName("demo-acceptor");
        assertThat(s.packId()).isEqualTo("demo");
        assertThat(s.mode()).isEqualTo(FIXMode.ACCEPTOR);
        assertThat(s.fixVersion()).isEqualTo(FIXVersion.FIXT_11);
        assertThat(s.senderCompID()).isEqualTo("SIM");
        assertThat(s.targetCompID()).isEqualTo("SUT");
        assertThat(s.port()).isEqualTo(9876);           // from the value's default
        assertThat(s.appDataDictionary()).isEqualTo("demo__FIX50SP2-DEMO.xml");
        assertThat(f.dictionaries.list()).containsExactly("demo__FIX50SP2-DEMO.xml");
        assertThat(f.packs.findById("demo")).isPresent()
            .get().satisfies(p -> assertThat(p.values()).containsEntry("PORT", "9876").containsEntry("SENDER", "SIM"));
        // registered for execution straight away
        assertThat(f.registry.findAll()).hasSize(2);
    }

    @Test
    void references_by_name_resolved() {
        importDemo();
        Scenario main = scenario("demo-main");
        Scenario send = scenario("demo-send-quote-request");
        FIXSessionConfig session = f.sessions.byName("demo-acceptor");
        DataSet ds = f.dataSets.findByName("instruments").orElseThrow();

        ScenarioNode call = main.findNode("call").orElseThrow();
        assertThat(call.type()).isEqualTo(NodeType.CALL_SCENARIO);
        assertThat(call.config()).containsEntry("targetScenarioId", send.id().toString())
            .doesNotContainKey("targetScenario");
        assertThat(main.sessionRef()).isEqualTo(session.id().toString());
        assertThat(main.datasetRef()).isEqualTo(ds.id().toString());
        // the registry holds the same resolved content
        assertThat(f.registry.getById(main.id()).orElseThrow().findNode("call").orElseThrow().config())
            .containsEntry("targetScenarioId", send.id().toString());
    }

    @Test
    void parallel_target_scenario_resolved_too() {
        String parallel = PackFakes.read("scenarios/demo-main.yaml")
            .replace("type: CALL_SCENARIO", "type: PARALLEL");
        importDemo(f, Map.of("scenarios/demo-main.yaml", parallel), List.of());
        assertThat(scenario("demo-main").findNode("call").orElseThrow().config())
            .containsEntry("targetScenarioId", scenario("demo-send-quote-request").id().toString());
    }

    @Test
    void reimport_updates_in_place_and_removes_dropped() {
        importDemo();
        UUID mainId = scenario("demo-main").id();
        UUID sendId = scenario("demo-send-quote-request").id();
        UUID dsId = f.dataSets.findByName("instruments").orElseThrow().id();
        UUID sessionId = f.sessions.byName("demo-acceptor").id();
        // objects outside the pack stay untouched
        f.dataSets.save(new DataSet(null, "mine", List.of("a"), List.of(List.of("1")), null));

        String main = PackFakes.read("scenarios/demo-main.yaml")
            .replace("description: Calls the send sub-flow by name.", "description: changed")
            .replace("    config:\n      targetScenario: demo-send-quote-request\n", "")
            .replace("type: CALL_SCENARIO", "type: DELAY");
        String pack = PackFakes.read("pack.yaml").replace("version: 1.0.0", "version: 1.1.0");
        PackImportResult r = importDemo(f,
            Map.of("scenarios/demo-main.yaml", main, "pack.yaml", pack,
                "datasets/instruments.csv", "isin,qty,side\nXS9,1,1\n"),
            List.of("scenarios/demo-send-quote-request.yaml"));

        assertThat(r.version()).isEqualTo("1.1.0");
        assertThat(r.scenarios()).isEqualTo(1);
        assertThat(scenario("demo-main").id()).isEqualTo(mainId);
        assertThat(scenario("demo-main").description()).isEqualTo("changed");
        assertThat(f.scenarios.findById(sendId)).isEmpty();
        assertThat(f.registry.getById(sendId)).isEmpty();
        assertThat(f.registry.getById(mainId).orElseThrow().description()).isEqualTo("changed");
        DataSet ds = f.dataSets.findByName("instruments").orElseThrow();
        assertThat(ds.id()).isEqualTo(dsId);
        assertThat(ds.rows()).containsExactly(List.of("XS9", "1", "1"));
        assertThat(f.sessions.byName("demo-acceptor").id()).isEqualTo(sessionId);
        assertThat(f.dataSets.findByName("mine")).isPresent();
        assertThat(f.packs.findById("demo").orElseThrow().manifest().version()).isEqualTo("1.1.0");
    }

    @Test
    void reimport_drops_dictionaries_and_sessions_no_longer_in_zip() {
        importDemo();
        String session = PackFakes.read("sessions/demo-acceptor.yaml")
            .replace("appDataDictionary: FIX50SP2-DEMO.xml\n", "");
        String main = PackFakes.read("scenarios/demo-main.yaml").replace("sessionRef: demo-acceptor\n", "");
        String send = PackFakes.read("scenarios/demo-send-quote-request.yaml").replace("sessionRef: demo-acceptor\n", "");
        importDemo(f, Map.of("sessions/other.yaml", session.replace("name: demo-acceptor", "name: other"),
                "scenarios/demo-main.yaml", main, "scenarios/demo-send-quote-request.yaml", send),
            List.of("sessions/demo-acceptor.yaml", "dictionaries/FIX50SP2-DEMO.xml"));
        assertThat(f.sessions.findAll()).extracting(FIXSessionConfig::name).containsExactly("other");
        assertThat(f.dictionaries.list()).isEmpty();
    }

    @Test
    void missing_values_listed() {
        assertThatThrownBy(() -> f.importer().importPack(
                new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)), Map.of("PORT", "1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SENDER")
            .hasMessageContaining("TARGET")
            .hasMessageNotContaining("PORT");
        assertThat(f.scenarios.findAll()).isEmpty();
        assertThat(f.packs.findAll()).isEmpty();
    }

    @Test
    void blank_value_counts_as_missing() {
        Map<String, String> values = new HashMap<>(PackFakes.demoValues());
        values.put("SENDER", " ");
        assertThatThrownBy(() -> f.importer().importPack(
                new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)), values))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SENDER");
    }

    @Test
    void unresolved_reference_names_file() {
        String main = PackFakes.read("scenarios/demo-main.yaml")
            .replace("targetScenario: demo-send-quote-request", "targetScenario: nope");
        assertThatThrownBy(() -> importDemo(f, Map.of("scenarios/demo-main.yaml", main), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scenarios/demo-main.yaml")
            .hasMessageContaining("nope");
        assertThat(f.scenarios.findAll()).isEmpty();
        assertThat(f.dictionaries.list()).isEmpty();

        String ds = PackFakes.read("scenarios/demo-main.yaml").replace("datasetRef: instruments", "datasetRef: other");
        assertThatThrownBy(() -> importDemo(f, Map.of("scenarios/demo-main.yaml", ds), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scenarios/demo-main.yaml").hasMessageContaining("other");

        String sess = PackFakes.read("scenarios/demo-main.yaml").replace("sessionRef: demo-acceptor", "sessionRef: x");
        assertThatThrownBy(() -> importDemo(f, Map.of("scenarios/demo-main.yaml", sess), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scenarios/demo-main.yaml").hasMessageContaining("sessionRef");

        String dict = PackFakes.read("sessions/demo-acceptor.yaml").replace("FIX50SP2-DEMO.xml", "MISSING.xml");
        assertThatThrownBy(() -> importDemo(f, Map.of("sessions/demo-acceptor.yaml", dict), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sessions/demo-acceptor.yaml").hasMessageContaining("MISSING.xml");
    }

    @Test
    void unknown_placeholder_names_file() {
        String s = PackFakes.read("sessions/demo-acceptor.yaml").replace("host: 0.0.0.0", "host: ${HOST}");
        assertThatThrownBy(() -> importDemo(f, Map.of("sessions/demo-acceptor.yaml", s), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sessions/demo-acceptor.yaml").hasMessageContaining("HOST");
    }

    @Test
    void data_set_name_taken_outside_the_pack_is_a_conflict() {
        f.dataSets.save(new DataSet(null, "instruments", List.of("a"), List.of(), null));
        assertThatThrownBy(this::importDemo)
            .isInstanceOf(PackConflictException.class).hasMessageContaining("instruments");
    }

    @Test
    void running_execution_blocks_with_409() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ProgrammableHandler blockingStart = new ProgrammableHandler(NodeType.START, (n, c) -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return NodeHandlerResult.success("end");
        });
        PackFakes fakes = new PackFakes(new NodeDispatcher(List.of(blockingStart, new EndHandler())));
        importDemo(fakes, Map.of(), List.of());
        UUID sendId = fakes.scenarios.findAll().stream()
            .filter(s -> s.name().equals("demo-send-quote-request")).findFirst().orElseThrow().id();
        UUID exec = fakes.executions.start(sendId, null);
        try {
            assertThatThrownBy(() -> importDemo(fakes, Map.of(), List.of()))
                .isInstanceOf(PackConflictException.class)
                .hasMessageContaining(exec.toString());
            assertThatThrownBy(() -> fakes.importer().uninstall("demo"))
                .isInstanceOf(PackConflictException.class)
                .hasMessageContaining(exec.toString());
        } finally {
            release.countDown();
        }
        await().atMost(Duration.ofSeconds(5)).until(() -> fakes.guard.running(
            fakes.scenarios.findAll().stream().map(Scenario::id).collect(java.util.stream.Collectors.toSet()),
            java.util.Set.of()).isEmpty());
        importDemo(fakes, Map.of(), List.of());
    }

    @Test
    void execution_on_a_pack_session_blocks_with_409() {
        importDemo();
        UUID sessionId = f.sessions.byName("demo-acceptor").id();
        UUID exec = UUID.randomUUID();
        f.sessionExecutions.register(sessionId.toString(), exec);
        assertThatThrownBy(this::importDemo)
            .isInstanceOf(PackConflictException.class).hasMessageContaining(exec.toString());
    }

    @Test
    void connected_session_reconnected_only_if_changed() {
        importDemo();
        FIXSessionConfig s = f.sessions.byName("demo-acceptor");
        f.sessionManager.connect(s);
        f.fixPort.connects.set(0);
        f.fixPort.connectedWith.clear();

        importDemo();   // same values: unchanged
        assertThat(f.fixPort.disconnects.get()).isZero();
        assertThat(f.fixPort.connects.get()).isZero();

        f.importer().importPack(new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)),
            Map.of("SENDER", "SIM2", "TARGET", "SUT"));
        assertThat(f.fixPort.disconnects.get()).isEqualTo(1);
        assertThat(f.fixPort.connects.get()).isEqualTo(1);
        assertThat(f.fixPort.connectedWith.get(0).senderCompID()).isEqualTo("SIM2");
        assertThat(f.sessionManager.isConnected(s.id())).isTrue();
        assertThat(f.sessions.byName("demo-acceptor").senderCompID()).isEqualTo("SIM2");
    }

    @Test
    void disconnected_session_is_not_connected_by_import() {
        importDemo();
        f.importer().importPack(new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)),
            Map.of("SENDER", "SIM2", "TARGET", "SUT"));
        assertThat(f.fixPort.connects.get()).isZero();
    }

    @Test
    void engine_min_checked() {
        assertThatThrownBy(() -> f.importer("0.7.4-beta").importPack(
                new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)), PackFakes.demoValues()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("0.8.0")
            .hasMessageContaining("0.7.4-beta");
        // a pre-release of the required version is enough: 0.8.0-beta runs packs that need 0.8.0
        assertThat(f.importer("0.8.0-beta").importPack(
                new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)), PackFakes.demoValues()).packId())
            .isEqualTo("demo");
        assertThat(PackImporter.compareVersions("0.10.0", "0.9.9")).isPositive();
        assertThat(PackImporter.compareVersions("1.0", "1.0.0")).isZero();
    }

    @Test
    void uninstall_removes_everything_and_disconnects() {
        importDemo();
        FIXSessionConfig s = f.sessions.byName("demo-acceptor");
        f.sessionManager.connect(s);
        f.dataSets.save(new DataSet(null, "mine", List.of("a"), List.of(), null));

        f.importer().uninstall("demo");

        assertThat(f.scenarios.findAll()).isEmpty();
        assertThat(f.registry.findAll()).isEmpty();
        assertThat(f.sessions.findAll()).isEmpty();
        assertThat(f.dictionaries.list()).isEmpty();
        assertThat(f.dataSets.findAll()).extracting(DataSet::name).containsExactly("mine");
        assertThat(f.packs.findAll()).isEmpty();
        assertThat(f.sessionManager.isConnected(s.id())).isFalse();
    }

    @Test
    void uninstall_unknown_pack_is_not_found() {
        assertThatThrownBy(() -> f.importer().uninstall("nope"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void invalid_csv_names_file() {
        assertThatThrownBy(() -> importDemo(f, Map.of("datasets/instruments.csv", "a,b\n1\n"), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("datasets/instruments.csv").hasMessageContaining("line 2");
    }

    @Test
    void invalid_dictionary_names_file() {
        assertThatThrownBy(() -> importDemo(f, Map.of("dictionaries/FIX50SP2-DEMO.xml", "<nope/>"), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dictionaries/FIX50SP2-DEMO.xml");
    }
}
