package com.fixflow.api.rest;

import com.fixflow.api.rest.dto.ScenarioDto;
import com.fixflow.api.rest.dto.ScenarioRequest;
import com.fixflow.core.domain.scenario.Scenario;
import com.fixflow.core.ports.outbound.ScenarioRepositoryPort;
import com.fixflow.engine.scenario.ScenarioDslParser;
import com.fixflow.engine.scenario.ScenarioDuplicator;
import com.fixflow.engine.scenario.ScenarioRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioRepositoryPort repo;
    private final ScenarioDslParser parser;
    private final ScenarioRegistry registry;
    private final ScenarioDuplicator duplicator;

    public ScenarioController(ScenarioRepositoryPort repo, ScenarioDslParser parser,
                              ScenarioRegistry registry, ScenarioDuplicator duplicator) {
        this.repo = repo;
        this.parser = parser;
        this.registry = registry;
        this.duplicator = duplicator;
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@RequestBody ScenarioRequest req) {
        Scenario parsed = req.yamlDsl() != null && !req.yamlDsl().isBlank()
            ? parser.parseYaml(req.yamlDsl())
            : new Scenario(UUID.randomUUID(), req.name(), req.description(), "1",
                           req.sessionRef(), null, null, null, null, null, null, null);
        Scenario saved = repo.save(parsed);
        registry.register(saved);
        String yaml = saved.rawYaml() != null ? saved.rawYaml() : parser.toYaml(saved);
        return ResponseEntity.status(201).body(ScenarioDto.withYaml(saved, yaml));
    }

    /**
     * Copies an existing scenario. The copy gets a new scenario id and new node ids, with every
     * reference to those ids rewritten, so editing it cannot disturb the original. Body is
     * optional; without a name the copy is called "&lt;name&gt; (copy)".
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ScenarioDto> duplicate(@PathVariable UUID id,
                                                 @RequestBody(required = false) ScenarioRequest req) {
        Scenario source = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found: " + id));
        String sourceYaml = source.rawYaml() != null ? source.rawYaml() : parser.toYaml(source);
        String name = req != null && req.name() != null && !req.name().isBlank()
            ? req.name().trim()
            : (source.name() == null ? "scenario" : source.name()) + " (copy)";

        String copyYaml = duplicator.duplicate(sourceYaml, name);
        Scenario saved = repo.save(parser.parseYaml(copyYaml));
        registry.register(saved);
        String responseYaml = saved.rawYaml() != null ? saved.rawYaml() : copyYaml;
        return ResponseEntity.status(201).body(ScenarioDto.withYaml(saved, responseYaml));
    }

    @GetMapping
    public List<ScenarioDto> list() {
        return repo.findAll().stream().map(ScenarioDto::from).toList();
    }

    @GetMapping("/{id}")
    public ScenarioDto get(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found: " + id));
        String yaml = s.rawYaml() != null ? s.rawYaml() : parser.toYaml(s);
        return ScenarioDto.withYaml(s, yaml);
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
                           existing.edges(), existing.variables(), null);
        Scenario saved = repo.save(updated);
        registry.register(saved);
        String responseYaml = saved.rawYaml() != null ? saved.rawYaml() : parser.toYaml(saved);
        return ScenarioDto.withYaml(saved, responseYaml);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<ScenarioDto> importYaml(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("import file is empty");
        }
        String yaml = new String(file.getBytes(), StandardCharsets.UTF_8);
        Scenario s = parser.parseYaml(yaml);
        Scenario saved = repo.save(s);
        registry.register(saved);
        String responseYaml = saved.rawYaml() != null ? saved.rawYaml() : parser.toYaml(saved);
        return ResponseEntity.status(201).body(ScenarioDto.withYaml(saved, responseYaml));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        Scenario s = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("scenario not found: " + id));
        String yaml = parser.toYaml(s);
        String safeName = s.name() == null ? "scenario" : s.name().replaceAll("[\\r\\n\"\\\\/]+", "_");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/x-yaml"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + ".yaml\"");
        return new ResponseEntity<>(yaml, h, 200);
    }
}
