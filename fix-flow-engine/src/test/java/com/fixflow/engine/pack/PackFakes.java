package com.fixflow.engine.pack;

import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.pack.InstalledPack;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import com.fixflow.core.ports.outbound.DictionaryStorePort;
import com.fixflow.core.ports.outbound.FIXSessionRepositoryPort;
import com.fixflow.core.ports.outbound.PackRepositoryPort;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.SessionExecutionRegistry;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.handlers.NodeDispatcher;
import com.fixflow.engine.scenario.ScenarioDslParser;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.support.FakeFixAdapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** In-memory engine wiring for the pack tests: every port the importer and exporter use. */
final class PackFakes {

    static final Path DEMO = Path.of("src/test/resources/packs/demo");
    static final String ENGINE_VERSION = "0.8.0-beta";

    final ScenarioDslParser parser = new ScenarioDslParser();
    final ScenarioRegistry registry = new ScenarioRegistry();
    final Scenarios scenarios = new Scenarios(parser);
    final DataSets dataSets = new DataSets();
    final Sessions sessions = new Sessions();
    final Dictionaries dictionaries = new Dictionaries();
    final Packs packs = new Packs();
    final CountingFixPort fixPort = new CountingFixPort();
    final FIXSessionManager sessionManager = new FIXSessionManager(fixPort);
    final SessionExecutionRegistry sessionExecutions = new SessionExecutionRegistry();
    final ExecutionManager executions;
    final PackExecutionGuard guard;

    PackFakes() { this(new NodeDispatcher(List.of())); }

    PackFakes(NodeDispatcher dispatcher) {
        executions = new ExecutionManager(registry, new com.fixflow.engine.execution.NodeWalker(dispatcher),
                null, null, sessionExecutions);
        guard = new PackExecutionGuard(executions, sessionExecutions);
    }

    PackImporter importer() { return importer(ENGINE_VERSION); }

    PackImporter importer(String engineVersion) {
        return new PackImporter(scenarios, registry, parser, dataSets, sessions, dictionaries, packs,
                sessionManager, guard, engineVersion);
    }

    PackExporter exporter() {
        return new PackExporter(scenarios, dataSets, sessions, dictionaries, packs);
    }

    static Map<String, String> demoValues() {
        return Map.of("SENDER", "SIM", "TARGET", "SUT");
    }

    static byte[] zip(Path folder) {
        return zip(folder, Map.of(), List.of());
    }

    /** Zips {@code folder}, replacing the files in {@code override} (path → content) and leaving out {@code drop}. */
    static byte[] zip(Path folder, Map<String, String> override, List<String> drop) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                String rel = folder.relativize(p).toString().replace('\\', '/');
                entries.put(rel, Files.readAllBytes(p));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        drop.forEach(entries::remove);
        override.forEach((k, v) -> entries.put(k, v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return zip(entries);
    }

    static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static String read(String rel) {
        try {
            return Files.readString(DEMO.resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------- fakes ----------

    static final class Scenarios implements ScenarioRepositoryPort {
        final Map<UUID, Scenario> rows = new LinkedHashMap<>();
        private final ScenarioDslParser parser;
        Scenarios(ScenarioDslParser parser) { this.parser = parser; }

        @Override public Scenario save(Scenario s) {
            Scenario old = rows.get(s.id());
            String pack = s.packId() != null ? s.packId() : old == null ? null : old.packId();
            Scenario stored = parser.parseYaml(s.rawYaml() != null ? s.rawYaml() : parser.toYaml(s))
                    .withIdAndPack(s.id(), pack);
            rows.put(s.id(), stored);
            return stored;
        }
        @Override public Optional<Scenario> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public List<Scenario> findAll() { return List.copyOf(rows.values()); }
        @Override public void delete(UUID id) { rows.remove(id); }
        @Override public void saveVersion(Scenario scenario) { }
        @Override public List<String> findVersions(UUID id) { return List.of(); }
    }

    static final class DataSets implements DataSetRepositoryPort {
        final Map<UUID, DataSet> rows = new LinkedHashMap<>();
        @Override public DataSet save(DataSet ds) {
            UUID id = ds.id() != null ? ds.id() : UUID.randomUUID();
            DataSet stored = new DataSet(id, ds.name(), ds.columns(), ds.rows(), ds.packId());
            rows.put(id, stored);
            return stored;
        }
        @Override public Optional<DataSet> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public Optional<DataSet> findByName(String name) {
            return rows.values().stream().filter(d -> d.name().equals(name)).findFirst();
        }
        @Override public List<DataSet> findAll() { return List.copyOf(rows.values()); }
        @Override public void delete(UUID id) { rows.remove(id); }
    }

    static final class Sessions implements FIXSessionRepositoryPort {
        final Map<UUID, FIXSessionConfig> rows = new LinkedHashMap<>();
        @Override public FIXSessionConfig save(FIXSessionConfig c) { rows.put(c.id(), c); return c; }
        @Override public Optional<FIXSessionConfig> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public List<FIXSessionConfig> findAll() { return List.copyOf(rows.values()); }
        @Override public void delete(UUID id) { rows.remove(id); }
        FIXSessionConfig byName(String name) {
            return rows.values().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        }
    }

    static final class Dictionaries implements DictionaryStorePort {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        @Override public List<String> list() { return files.keySet().stream().sorted().toList(); }
        @Override public void validate(byte[] xml) {
            String s = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
            if (!s.contains("<fix")) throw new IllegalArgumentException("not a valid QuickFIX/J data dictionary");
        }
        @Override public void saveXml(String name, byte[] xml) { validate(xml); files.put(name, xml); }
        @Override public Optional<byte[]> read(String name) { return Optional.ofNullable(files.get(name)); }
        @Override public void delete(String name) { files.remove(name); }
    }

    static final class Packs implements PackRepositoryPort {
        final Map<String, InstalledPack> rows = new LinkedHashMap<>();
        @Override public InstalledPack save(InstalledPack p) { rows.put(p.id(), p); return p; }
        @Override public Optional<InstalledPack> findById(String id) { return Optional.ofNullable(rows.get(id)); }
        @Override public List<InstalledPack> findAll() { return new ArrayList<>(rows.values()); }
        @Override public void delete(String id) { rows.remove(id); }
    }

    static final class CountingFixPort extends FakeFixAdapter {
        final AtomicInteger connects = new AtomicInteger();
        final AtomicInteger disconnects = new AtomicInteger();
        final List<FIXSessionConfig> connectedWith = new ArrayList<>();
        @Override public void connect(FIXSessionConfig config) {
            connects.incrementAndGet();
            connectedWith.add(config);
            super.connect(config);
        }
        @Override public void disconnect(UUID sessionId) {
            disconnects.incrementAndGet();
            super.disconnect(sessionId);
        }
    }
}
