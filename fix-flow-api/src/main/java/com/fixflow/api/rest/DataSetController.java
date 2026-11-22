package com.fixflow.api.rest;

import com.fixflow.api.exception.SessionConflictException;
import com.fixflow.api.rest.dto.DataSetDto;
import com.fixflow.core.domain.dataset.DataSet;
import com.fixflow.core.ports.outbound.DataSetRepositoryPort;
import com.fixflow.engine.dataset.CsvCodec;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/datasets")
public class DataSetController {

    private final DataSetRepositoryPort repo;

    public DataSetController(DataSetRepositoryPort repo) { this.repo = repo; }

    @GetMapping
    public List<DataSetDto> list() {
        return repo.findAll().stream().map(DataSetDto::summary).toList();
    }

    @GetMapping("/{id}")
    public DataSetDto get(@PathVariable UUID id) { return DataSetDto.full(load(id)); }

    @PostMapping
    public ResponseEntity<DataSetDto> create(@RequestBody DataSetDto body) {
        DataSet ds = fromDto(null, body, null); // packId from the body is ignored: only the pack importer sets it
        assertNameFree(ds.name(), null);
        return ResponseEntity.status(201).body(DataSetDto.full(save(ds)));
    }

    @PutMapping("/{id}")
    public DataSetDto update(@PathVariable UUID id, @RequestBody DataSetDto body) {
        DataSet existing = load(id);
        DataSet ds = fromDto(id, body, existing.packId());
        assertNameFree(ds.name(), id);
        return DataSetDto.full(save(ds));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        load(id);
        repo.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<DataSetDto> importCsv(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "name", required = false) String name)
        throws IOException {
        String n = name != null && !name.isBlank() ? name : stripCsv(file.getOriginalFilename());
        assertNameFree(n, null);
        DataSet ds = CsvCodec.parse(n, new String(file.getBytes(), StandardCharsets.UTF_8));
        return ResponseEntity.status(201).body(DataSetDto.full(save(ds)));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        DataSet ds = load(id);
        String file = ds.name().replaceAll("[^A-Za-z0-9._-]", "_") + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
            .body(CsvCodec.write(ds));
    }

    private DataSet load(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("data set not found: " + id));
    }

    /**
     * The name check above is not atomic: two concurrent creates with one name both pass it, and
     * the unique index on {@code data_sets(name)} rejects the second insert. That is a name clash,
     * so it answers 409 like the check does, not 500.
     */
    private DataSet save(DataSet ds) {
        try {
            return repo.save(ds);
        } catch (DataIntegrityViolationException e) {
            throw new SessionConflictException("data set name already used: " + ds.name());
        }
    }

    private void assertNameFree(String name, UUID selfId) {
        repo.findByName(name).ifPresent(other -> {
            if (!other.id().equals(selfId)) {
                throw new SessionConflictException("data set name already used: " + name);
            }
        });
    }

    private static DataSet fromDto(UUID id, DataSetDto b, String packId) {
        List<List<String>> rows = b.rows() == null ? List.of()
            : b.rows().stream().map(r -> r.stream().map(v -> v == null ? "" : v).toList()).toList();
        return new DataSet(id, b.name(), b.columns(), rows, packId);
    }

    private static String stripCsv(String filename) {
        if (filename == null) return null;
        return filename.toLowerCase().endsWith(".csv") ? filename.substring(0, filename.length() - 4) : filename;
    }
}
