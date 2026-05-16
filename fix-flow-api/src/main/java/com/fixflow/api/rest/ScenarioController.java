package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ScenarioDto;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import com.fixflow.engine.scenario.ScenarioRegistry;
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
    private final ScenarioRegistry registry;

    public ScenarioController(ScenarioRepositoryPort repo, ScenarioDslParser parser, ScenarioRegistry registry) {
        this.repo = repo;
        this.parser = parser;
        this.registry = registry;
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@RequestBody ScenarioRequest req) {
        Scenario parsed = req.yamlDsl() != null && !req.yamlDsl().isBlank()
            ? parser.parseYaml(req.yamlDsl())
            : new Scenario(UUID.randomUUID(), req.name(), req.description(), "1",
                           req.sessionRef(), null, null, null, null, null, null);
        Scenario saved = repo.save(parsed);
        registry.register(saved);
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

    @PutMapping("/{id}")
    public ScenarioDto update(@PathVariable UUID id, @RequestBody ScenarioRequest req) {
        Scenario existing = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found: " + id));
        Scenario updated = req.yamlDsl() != null && !req.yamlDsl().isBlank()
            ? parser.parseYaml(req.yamlDsl())
            : new Scenario(existing.id(),
                           req.name() != null ? req.name() : existing.name(),
                           req.description() != null ? req.description() : existing.description(),
                           existing.version(), existing.sessionRef(),
                           existing.runtimePolicy(), existing.routingRules(),
                           existing.correlationRules(), existing.nodes(),
                           existing.edges(), existing.variables());
        Scenario saved = repo.save(updated);
        registry.register(saved);
        return ScenarioDto.from(saved);
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
        Scenario saved = repo.save(s);
        registry.register(saved);
        return ResponseEntity.status(201).body(ScenarioDto.from(saved));
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
