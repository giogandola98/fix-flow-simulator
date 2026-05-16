package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ScenarioDto;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioRepositoryPort repo;
    private final ScenarioDslParser parser;

    public ScenarioController(ScenarioRepositoryPort repo, ScenarioDslParser parser) {
        this.repo = repo;
        this.parser = parser;
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@RequestBody ScenarioRequest req) {
        Scenario parsed = req.yamlDsl() != null && !req.yamlDsl().isBlank()
            ? parser.parseYaml(req.yamlDsl())
            : new Scenario(UUID.randomUUID(), req.name(), req.description(), "1",
                           req.sessionRef(), null, null, null, null, null, null);
        Scenario saved = repo.save(parsed);
        return ResponseEntity.status(201).body(ScenarioDto.from(saved));
    }

    @GetMapping
    public List<ScenarioDto> list() {
        return repo.findAll().stream().map(ScenarioDto::from).toList();
    }

    @GetMapping("/{id}")
    public ScenarioDto get(@PathVariable UUID id) {
        return ScenarioDto.from(repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found: " + id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<ScenarioDto> importYaml(@RequestParam("file") MultipartFile file) throws Exception {
        String yaml = new String(file.getBytes());
        Scenario s = parser.parseYaml(yaml);
        return ResponseEntity.status(201).body(ScenarioDto.from(repo.save(s)));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found: " + id));
        String yaml = parser.toYaml(s);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/x-yaml"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + s.name() + ".yaml");
        return new ResponseEntity<>(yaml, h, 200);
    }
}
