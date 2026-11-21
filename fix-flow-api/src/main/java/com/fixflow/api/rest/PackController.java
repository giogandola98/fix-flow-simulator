package com.fixflow.api.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.rest.dto.PackDto;
import com.fixflow.core.domain.pack.InstalledPack;
import com.fixflow.core.domain.pack.PackImportResult;
import com.fixflow.core.domain.pack.PackManifest;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import com.fixflow.core.ports.outbound.DictionaryStorePort;
import com.fixflow.core.ports.outbound.FIXSessionRepositoryPort;
import com.fixflow.core.ports.outbound.PackRepositoryPort;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.pack.PackContents;
import com.fixflow.engine.pack.PackExporter;
import com.fixflow.engine.pack.PackImporter;
import com.fixflow.engine.pack.PackManifestCodec;
import com.fixflow.engine.pack.PackReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider packs: list, inspect a zip, import (install or update), export, delete. The import
 * and delete rules live in {@link PackImporter}; see {@code docs/packs.md} for the format.
 */
@RestController
@RequestMapping("/api/v1/packs")
public class PackController {

    private final PackImporter importer;
    private final PackExporter exporter;
    private final PackRepositoryPort packs;
    private final ScenarioRepositoryPort scenarios;
    private final DataSetRepositoryPort dataSets;
    private final FIXSessionRepositoryPort sessions;
    private final DictionaryStorePort dictionaries;
    private final ObjectMapper json = new ObjectMapper();

    public PackController(PackImporter importer, PackExporter exporter, PackRepositoryPort packs,
                          ScenarioRepositoryPort scenarios, DataSetRepositoryPort dataSets,
                          FIXSessionRepositoryPort sessions, DictionaryStorePort dictionaries) {
        this.importer = importer;
        this.exporter = exporter;
        this.packs = packs;
        this.scenarios = scenarios;
        this.dataSets = dataSets;
        this.sessions = sessions;
        this.dictionaries = dictionaries;
    }

    @GetMapping
    public List<PackDto.Summary> list() {
        return packs.findAll().stream()
            .sorted(Comparator.comparing(InstalledPack::id))
            .map(p -> new PackDto.Summary(p.id(), p.manifest().name(), p.manifest().version(),
                p.manifest().provider(), p.manifest().description(), p.installedAt(), p.updatedAt(),
                counts(p.id())))
            .toList();
    }

    @PostMapping(value = "/inspect", consumes = "multipart/form-data")
    public PackDto.Inspection inspect(@RequestParam("file") MultipartFile file) throws IOException {
        PackContents contents;
        try (InputStream in = file.getInputStream()) {
            contents = PackReader.readZip(in);
        }
        PackManifest m = PackManifestCodec.parse(contents.manifestYaml());
        Optional<InstalledPack> installed = packs.findById(m.id());
        return new PackDto.Inspection(m.id(), m.name(), m.version(), m.provider(), m.engineMin(),
            m.description(), m.values().stream().map(PackDto.Value::from).toList(),
            installed.map(p -> p.manifest().version()).orElse(null),
            installed.map(InstalledPack::values).orElse(null));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PackImportResult> importPack(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "values", required = false) String values)
        throws IOException {
        Map<String, String> parsed = parseValues(values);
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.status(201).body(importer.importPack(in, parsed));
        }
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id) {
        byte[] zip = exporter.export(id);
        String version = packs.findById(id).map(p -> p.manifest().version()).orElse("");
        String file = (id + "-" + version).replaceAll("[^A-Za-z0-9._-]", "_") + ".zip";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
            .body(zip);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        importer.uninstall(id);
        return ResponseEntity.noContent().build();
    }

    private PackDto.Counts counts(String packId) {
        String prefix = PackImporter.dictionaryPrefix(packId);
        return new PackDto.Counts(
            (int) scenarios.findAll().stream().filter(s -> packId.equals(s.packId())).count(),
            (int) dataSets.findAll().stream().filter(d -> packId.equals(d.packId())).count(),
            (int) sessions.findAll().stream().filter(c -> packId.equals(c.packId())).count(),
            (int) dictionaries.list().stream().filter(n -> n.startsWith(prefix)).count());
    }

    private Map<String, String> parseValues(String values) {
        if (values == null || values.isBlank()) return Map.of();
        try {
            Map<String, String> m = json.readValue(values, new TypeReference<Map<String, String>>() {});
            return m == null ? Map.of() : m;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("values must be a JSON object of strings, e.g. {\"SENDER\":\"SIM\"}: "
                + e.getOriginalMessage());
        }
    }
}
