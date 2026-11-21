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
import com.fixflow.engine.dataset.CsvCodec;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes an installed pack back as a zip in the pack layout, with names instead of ids.
 *
 * <p>The objects are written as they are now, edits made in the engine included. Session files
 * carry the values the pack was installed with as literal fields, and {@code pack.yaml} gets
 * those values as the {@code default}s, so the zip imports again without a form.
 */
@Service
public class PackExporter {

    /** Fixed entry time so exporting the same pack twice gives the same bytes. */
    private static final long ENTRY_TIME = 315_532_800_000L; // 1980-01-01

    private final ScenarioRepositoryPort scenarios;
    private final DataSetRepositoryPort dataSets;
    private final FIXSessionRepositoryPort sessions;
    private final DictionaryStorePort dictionaries;
    private final PackRepositoryPort packs;
    private final ScenarioDslParser parser = new ScenarioDslParser();

    public PackExporter(ScenarioRepositoryPort scenarios, DataSetRepositoryPort dataSets,
                        FIXSessionRepositoryPort sessions, DictionaryStorePort dictionaries,
                        PackRepositoryPort packs) {
        this.scenarios = scenarios;
        this.dataSets = dataSets;
        this.sessions = sessions;
        this.dictionaries = dictionaries;
        this.packs = packs;
    }

    public byte[] export(String packId) {
        InstalledPack pack = packs.findById(packId)
                .orElseThrow(() -> new NoSuchElementException("pack not found: " + packId));
        List<Scenario> packScenarios = scenarios.findAll().stream()
                .filter(s -> packId.equals(s.packId())).sorted(Comparator.comparing(Scenario::name)).toList();
        List<DataSet> packDataSets = dataSets.findAll().stream()
                .filter(d -> packId.equals(d.packId())).sorted(Comparator.comparing(DataSet::name)).toList();
        List<FIXSessionConfig> packSessions = sessions.findAll().stream()
                .filter(c -> packId.equals(c.packId())).sorted(Comparator.comparing(FIXSessionConfig::name)).toList();
        String prefix = PackImporter.dictionaryPrefix(packId);

        PackReferenceResolver.Ids ids = new PackReferenceResolver.Ids(
                toMap(packScenarios, Scenario::id, Scenario::name),
                toMap(packDataSets, DataSet::id, DataSet::name),
                toMap(packSessions, FIXSessionConfig::id, FIXSessionConfig::name));

        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("pack.yaml", utf8(PackManifestCodec.write(pack.manifest(), pack.values())));
        for (String stored : dictionaries.list()) {
            if (!stored.startsWith(prefix)) continue;
            dictionaries.read(stored).ifPresent(xml ->
                    entries.put("dictionaries/" + stored.substring(prefix.length()), xml));
        }
        Set<String> used = new HashSet<>();
        for (DataSet d : packDataSets) {
            entries.put("datasets/" + fileName("datasets", d.name(), ".csv", used), utf8(CsvCodec.write(d)));
        }
        for (FIXSessionConfig c : packSessions) {
            PackSessionTemplate t = PackSessionTemplate.fromConfig(c,
                    unprefix(c.appDataDictionary(), prefix), unprefix(c.transportDataDictionary(), prefix));
            entries.put("sessions/" + fileName("sessions", c.name(), ".yaml", used), utf8(PackReferenceResolver.write(t)));
        }
        for (Scenario s : packScenarios) {
            String yaml = s.rawYaml() != null ? s.rawYaml() : parser.toYaml(s);
            entries.put("scenarios/" + fileName("scenarios", s.name(), ".yaml", used),
                    utf8(PackReferenceResolver.toPack(yaml, ids)));
        }
        return zip(entries);
    }

    /** {@code <packId>__name.xml} back to {@code name.xml}; a dictionary outside the pack keeps its name. */
    private static String unprefix(String name, String prefix) {
        return name != null && name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    /** A file-system-safe, unique (per folder) file name for an object name. */
    private static String fileName(String folder, String name, String ext, Set<String> used) {
        String base = name.replaceAll("[^A-Za-z0-9._-]", "_");
        String candidate = base + ext;
        for (int i = 2; !used.add(folder + "/" + candidate); i++) candidate = base + "-" + i + ext;
        return candidate;
    }

    private static <T> Map<UUID, String> toMap(List<T> items, Function<T, UUID> id, Function<T, String> name) {
        return items.stream().collect(Collectors.toMap(id, name, (a, b) -> a));
    }

    private static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(ENTRY_TIME);
                z.putNextEntry(entry);
                z.write(e.getValue());
                z.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
