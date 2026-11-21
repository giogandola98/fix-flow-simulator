package com.fixflow.api.rest;

import com.fixflow.adapters.dictionary.DictionaryStore;
import com.fixflow.adapters.persistence.FIXSessionRepositoryAdapter;
import com.fixflow.api.exception.SessionConflictException;
import com.fixflow.api.rest.dto.DictionaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {

    private final DictionaryStore store;
    private final FIXSessionRepositoryAdapter sessionRepo;

    public DictionaryController(DictionaryStore store, FIXSessionRepositoryAdapter sessionRepo) {
        this.store = store;
        this.sessionRepo = sessionRepo;
    }

    @GetMapping
    public List<DictionaryDto> list() {
        return store.list().stream()
            .map(n -> store.resolve(n).map(p -> dto(n, p)).orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DictionaryDto> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        Path saved;
        try (InputStream in = file.getInputStream()) {
            saved = store.save(name, in);
        }
        return ResponseEntity.status(201).body(dto(name, saved));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        List<String> users = sessionRepo.findAll().stream()
            .filter(c -> name.equals(c.appDataDictionary()) || name.equals(c.transportDataDictionary()))
            .map(c -> c.name())
            .toList();
        if (!users.isEmpty()) {
            throw new SessionConflictException(
                "dictionary " + name + " is used by session(s): " + String.join(", ", users));
        }
        store.delete(name);
        return ResponseEntity.noContent().build();
    }

    private static DictionaryDto dto(String name, Path file) {
        try {
            return new DictionaryDto(name, Files.size(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
