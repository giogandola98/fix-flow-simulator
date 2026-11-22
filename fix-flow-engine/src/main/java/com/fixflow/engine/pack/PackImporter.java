package com.fixflow.engine.pack;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.domain.pack.InstalledPack;
import com.fixflow.core.domain.pack.PackImportResult;
import com.fixflow.core.domain.pack.PackManifest;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import com.fixflow.core.ports.outbound.DictionaryStorePort;
import com.fixflow.core.ports.outbound.FIXSessionRepositoryPort;
import com.fixflow.core.ports.outbound.PackRepositoryPort;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.dataset.CsvCodec;
import com.fixflow.engine.fix.FIXSessionManager;
import com.fixflow.engine.scenario.ScenarioDslParser;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Installs, updates and removes provider packs.
 *
 * <p>An import first reads and checks everything — manifest, engine version, values, every file
 * and every name reference — and only then writes, so a bad pack leaves the engine as it was.
 * Re-importing a pack that is installed (same {@code id}) updates each object in place, matched
 * by {@code (packId, name)}, so ids, and with them executions history and references from outside
 * the pack, survive; objects of the pack no longer in the zip are removed.
 *
 * <p>Dictionaries are stored as {@code <packId>__<file>} so two packs can ship a file of the
 * same name.
 */
@Service
public class PackImporter {

    private static final Logger log = LoggerFactory.getLogger(PackImporter.class);
    /** Same rule as the dictionary store: a flat name ending in {@code .xml}. */
    private static final Pattern DICTIONARY_FILE = Pattern.compile("[A-Za-z0-9._-]+\\.xml");

    private final ScenarioRepositoryPort scenarios;
    private final ScenarioRegistry registry;
    private final ScenarioDslParser parser;
    private final DataSetRepositoryPort dataSets;
    private final FIXSessionRepositoryPort sessions;
    private final DictionaryStorePort dictionaries;
    private final PackRepositoryPort packs;
    private final FIXSessionManager sessionManager;
    private final PackExecutionGuard guard;
    private final String engineVersion;

    public PackImporter(ScenarioRepositoryPort scenarios, ScenarioRegistry registry, ScenarioDslParser parser,
                        DataSetRepositoryPort dataSets, FIXSessionRepositoryPort sessions,
                        DictionaryStorePort dictionaries, PackRepositoryPort packs,
                        FIXSessionManager sessionManager, PackExecutionGuard guard,
                        @Value("${fixflow.version:0.0.0}") String engineVersion) {
        this.scenarios = scenarios;
        this.registry = registry;
        this.parser = parser;
        this.dataSets = dataSets;
        this.sessions = sessions;
        this.dictionaries = dictionaries;
        this.packs = packs;
        this.sessionManager = sessionManager;
        this.guard = guard;
        this.engineVersion = engineVersion;
    }

    /** The stored name of a pack dictionary. */
    public static String storedDictionaryName(String packId, String file) { return packId + "__" + file; }

    public static String dictionaryPrefix(String packId) { return packId + "__"; }

    public synchronized PackImportResult importPack(InputStream zip, Map<String, String> suppliedValues) {
        PackContents contents = PackReader.readZip(zip);
        PackManifest manifest = PackManifestCodec.parse(contents.manifestYaml());
        String packId = manifest.id();
        checkEngineVersion(manifest);
        Map<String, String> values = effectiveValues(manifest, suppliedValues == null ? Map.of() : suppliedValues);
        List<String> warnings = new ArrayList<>();
        Set<String> declared = manifest.values().stream().map(PackManifest.Value::name).collect(Collectors.toSet());
        (suppliedValues == null ? Map.<String, String>of() : suppliedValues).keySet().stream()
                .filter(k -> !declared.contains(k)).sorted()
                .forEach(k -> warnings.add("value " + k + " is not declared in pack.yaml; ignored"));

        // What the pack has installed now.
        Map<String, Scenario> oldScenarios = byName(scenarios.findAll().stream()
                .filter(s -> packId.equals(s.packId())).toList(), Scenario::name);
        Map<String, DataSet> oldDataSets = byName(dataSets.findAll().stream()
                .filter(d -> packId.equals(d.packId())).toList(), DataSet::name);
        Map<String, FIXSessionConfig> oldSessions = byName(sessions.findAll().stream()
                .filter(c -> packId.equals(c.packId())).toList(), FIXSessionConfig::name);
        guard.check(packId, oldScenarios.values().stream().map(Scenario::id).collect(Collectors.toSet()),
                oldSessions.values().stream().map(FIXSessionConfig::id).toList());

        // Dictionaries.
        Map<String, byte[]> newDictionaries = new LinkedHashMap<>();
        contents.dictionaries().forEach((file, xml) -> {
            String path = "dictionaries/" + file;
            if (!DICTIONARY_FILE.matcher(file).matches() || file.contains("..")) {
                throw new IllegalArgumentException(path + ": use letters, digits, '.', '_', '-' and end with .xml");
            }
            try {
                dictionaries.validate(xml);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
            }
            newDictionaries.put(storedDictionaryName(packId, file), xml);
        });
        Set<String> engineDictionaries = new HashSet<>(dictionaries.list());

        // Data sets.
        List<DataSet> newDataSets = new ArrayList<>();
        Map<String, UUID> dataSetIds = new LinkedHashMap<>();
        contents.datasets().forEach((file, csv) -> {
            String path = "datasets/" + file;
            String name = file.substring(0, file.length() - ".csv".length());
            DataSet parsed;
            try {
                parsed = CsvCodec.parse(name, csv);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
            }
            dataSets.findByName(name).ifPresent(other -> {
                if (!packId.equals(other.packId())) {
                    throw new PackConflictException(path + ": data set name '" + name + "' is already used "
                            + (other.packId() == null ? "outside any pack" : "by pack " + other.packId()));
                }
            });
            DataSet old = oldDataSets.get(name);
            UUID id = old != null ? old.id() : UUID.randomUUID();
            dataSetIds.put(name, id);
            newDataSets.add(new DataSet(id, name, parsed.columns(), parsed.rows(), packId));
        });

        // Sessions.
        List<FIXSessionConfig> newSessions = new ArrayList<>();
        Map<String, UUID> sessionIds = new LinkedHashMap<>();
        contents.sessions().forEach((file, yaml) -> {
            String path = "sessions/" + file;
            PackSessionTemplate t = PackReferenceResolver.session(path, yaml, values);
            String app = dictionary(path, "appDataDictionary", t.appDataDictionary(), packId,
                    contents.dictionaries().keySet(), engineDictionaries);
            String transport = dictionary(path, "transportDataDictionary", t.transportDataDictionary(), packId,
                    contents.dictionaries().keySet(), engineDictionaries);
            String name = t.name() == null ? null : t.name().trim();
            FIXSessionConfig old = name == null ? null : oldSessions.get(name);
            FIXSessionConfig cfg;
            try {
                cfg = t.toConfig(old != null ? old.id() : UUID.randomUUID(), packId, app, transport);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
            }
            if (sessionIds.put(cfg.name(), cfg.id()) != null) {
                throw new IllegalArgumentException(path + ": a second session is named '" + cfg.name() + "'");
            }
            newSessions.add(cfg);
        });

        // Scenarios: names first (a scenario may call one read after it), then references.
        Map<String, ObjectNode> trees = new LinkedHashMap<>();
        Map<String, UUID> scenarioIds = new LinkedHashMap<>();
        Map<String, String> fileOf = new HashMap<>();
        contents.scenarios().forEach((file, yaml) -> {
            String path = "scenarios/" + file;
            ObjectNode tree = PackReferenceResolver.readObject(path, yaml);
            String name = PackReferenceResolver.scenarioName(path, tree);
            if (fileOf.put(name, path) != null) {
                throw new IllegalArgumentException(path + ": scenario name '" + name + "' is also used by " + fileOf.get(name));
            }
            Scenario old = oldScenarios.get(name);
            scenarioIds.put(name, old != null ? old.id() : UUID.randomUUID());
            trees.put(path, tree);
        });
        PackReferenceResolver.Names names = new PackReferenceResolver.Names(scenarioIds, dataSetIds, sessionIds);
        List<Scenario> newScenarios = new ArrayList<>();
        trees.forEach((path, tree) -> {
            UUID id = scenarioIds.get(PackReferenceResolver.scenarioName(path, tree));
            String stored = PackReferenceResolver.toStored(path, tree, id, names, warnings);
            Scenario parsed;
            try {
                parsed = parser.parseYaml(stored);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
            }
            newScenarios.add(parsed.withIdAndPack(id, packId));
        });

        // Everything checked: write.
        newDictionaries.forEach(dictionaries::saveXml);
        newDataSets.forEach(dataSets::save);
        for (FIXSessionConfig cfg : newSessions) saveSession(cfg, oldSessions.get(cfg.name()), warnings);
        for (Scenario s : newScenarios) registry.register(scenarios.save(s));

        // Objects the pack no longer has.
        oldScenarios.forEach((name, s) -> {
            if (!scenarioIds.containsKey(name)) removeScenario(s.id());
        });
        oldDataSets.forEach((name, d) -> {
            if (!dataSetIds.containsKey(name)) dataSets.delete(d.id());
        });
        oldSessions.forEach((name, c) -> {
            if (!sessionIds.containsKey(name)) removeSession(c.id());
        });
        for (String stored : dictionaries.list()) {
            if (stored.startsWith(dictionaryPrefix(packId)) && !newDictionaries.containsKey(stored)) {
                removeDictionary(stored, warnings);
            }
        }

        Instant now = Instant.now();
        Instant installedAt = packs.findById(packId).map(InstalledPack::installedAt).orElse(now);
        packs.save(new InstalledPack(manifest, values, installedAt, now));
        warnings.forEach(w -> log.warn("pack {}: {}", packId, w));
        return new PackImportResult(packId, manifest.version(), newScenarios.size(), newDataSets.size(),
                newSessions.size(), newDictionaries.size(), warnings);
    }

    /** Removes a pack and every object it installed. */
    public synchronized void uninstall(String packId) {
        packs.findById(packId).orElseThrow(() -> new NoSuchElementException("pack not found: " + packId));
        List<Scenario> packScenarios = scenarios.findAll().stream().filter(s -> packId.equals(s.packId())).toList();
        List<FIXSessionConfig> packSessions = sessions.findAll().stream().filter(c -> packId.equals(c.packId())).toList();
        guard.check(packId, packScenarios.stream().map(Scenario::id).collect(Collectors.toSet()),
                packSessions.stream().map(FIXSessionConfig::id).toList());
        List<String> warnings = new ArrayList<>();
        packScenarios.forEach(s -> removeScenario(s.id()));
        packSessions.forEach(c -> removeSession(c.id()));
        dataSets.findAll().stream().filter(d -> packId.equals(d.packId())).forEach(d -> dataSets.delete(d.id()));
        for (String stored : dictionaries.list()) {
            if (stored.startsWith(dictionaryPrefix(packId))) removeDictionary(stored, warnings);
        }
        warnings.forEach(w -> log.warn("pack {}: {}", packId, w));
        packs.delete(packId);
    }

    /**
     * Saves a pack session. A connected session whose config changed is disconnected, saved and
     * connected again with the new config; an unchanged one is left alone.
     */
    private void saveSession(FIXSessionConfig cfg, FIXSessionConfig old, List<String> warnings) {
        boolean connected = old != null && sessionManager.isConnected(old.id());
        if (!connected || cfg.equals(old)) {
            sessions.save(cfg);
            return;
        }
        sessionManager.disconnect(old.id());
        sessions.save(cfg);
        try {
            sessionManager.connect(cfg);
        } catch (RuntimeException e) {
            warnings.add("session " + cfg.name() + " was updated but did not reconnect: " + e.getMessage());
        }
    }

    private void removeScenario(UUID id) {
        scenarios.delete(id);
        registry.unregister(id);
    }

    private void removeSession(UUID id) {
        if (sessionManager.isConnected(id)) sessionManager.disconnect(id);
        sessions.delete(id);
    }

    /** Deletes a pack dictionary unless a session outside the pack still uses it. */
    private void removeDictionary(String stored, List<String> warnings) {
        List<String> users = sessions.findAll().stream()
                .filter(c -> stored.equals(c.appDataDictionary()) || stored.equals(c.transportDataDictionary()))
                .map(FIXSessionConfig::name).toList();
        if (users.isEmpty()) {
            dictionaries.delete(stored);
        } else {
            warnings.add("dictionary " + stored + " kept: used by session(s) " + String.join(", ", users));
        }
    }

    /** A session's dictionary reference: a file of the pack, else a dictionary already in the engine. */
    private static String dictionary(String path, String field, String name, String packId,
                                     Set<String> packFiles, Set<String> engineDictionaries) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (packFiles.contains(n)) return storedDictionaryName(packId, n);
        if (engineDictionaries.contains(n)) return n;
        throw new IllegalArgumentException(path + ": " + field + " '" + n
                + "' is neither in the pack's dictionaries/ folder nor in the engine");
    }

    private void checkEngineVersion(PackManifest m) {
        if (m.engineMin() == null || m.engineMin().isBlank()) return;
        if (compareVersions(engineVersion, m.engineMin()) < 0) {
            throw new IllegalArgumentException("pack " + m.id() + " needs engine " + m.engineMin()
                    + " or later; this engine is " + engineVersion);
        }
    }

    /**
     * Compares the numeric {@code major.minor.patch} part of two versions; a pre-release suffix
     * ({@code -beta}) is ignored, so a 0.8.0-beta engine runs packs that need 0.8.0.
     */
    static int compareVersions(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < 3; i++) {
            if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        }
        return 0;
    }

    private static int[] numbers(String v) {
        int[] out = new int[3];
        String core = v == null ? "" : v.trim().split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** Defaults overlaid by the supplied values; every value without a default must be supplied. */
    private static Map<String, String> effectiveValues(PackManifest m, Map<String, String> supplied) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (PackManifest.Value v : m.values()) {
            String given = supplied.get(v.name());
            if (given != null && !given.isBlank()) out.put(v.name(), given);
            else if (v.defaultValue() != null) out.put(v.name(), v.defaultValue());
            else missing.add(v.name());
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("pack " + m.id() + " needs values for: " + String.join(", ", missing));
        }
        return out;
    }

    private static <T> Map<String, T> byName(List<T> items, java.util.function.Function<T, String> name) {
        Map<String, T> out = new LinkedHashMap<>();
        for (T t : items) out.putIfAbsent(name.apply(t), t);
        return out;
    }
}
