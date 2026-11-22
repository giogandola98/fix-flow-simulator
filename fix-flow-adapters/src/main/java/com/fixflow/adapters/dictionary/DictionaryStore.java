package com.fixflow.adapters.dictionary;

import com.fixflow.core.ports.outbound.DictionaryStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import quickfix.ConfigError;
import quickfix.DataDictionary;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Folder of QuickFIX/J data dictionaries (XML) that sessions can select by file name.
 * Names are restricted to a flat {@code [A-Za-z0-9._-]+.xml} form so a name can never leave the folder.
 */
@Component
public class DictionaryStore implements DictionaryStorePort {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+\\.xml");

    private final Path dir;

    public DictionaryStore(@Value("${fixflow.dictionaries.dir:./dictionaries}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public List<String> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(n -> NAME.matcher(n).matches())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Validates the XML as a QuickFIX/J dictionary, then stores it, replacing a file of the same name. */
    public Path save(String name, InputStream xml) {
        checkName(name);
        byte[] bytes;
        try {
            bytes = xml.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        validate(bytes);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            Path tmp = Files.createTempFile(dir, "upload-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void validate(byte[] xml) {
        try {
            new DataDictionary(new ByteArrayInputStream(xml));
        } catch (ConfigError | RuntimeException e) {
            throw new IllegalArgumentException("not a valid QuickFIX/J data dictionary: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveXml(String name, byte[] xml) {
        save(name, new ByteArrayInputStream(xml));
    }

    @Override
    public Optional<byte[]> read(String name) {
        return resolve(name).map(p -> {
            try {
                return Files.readAllBytes(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public Optional<Path> resolve(String name) {
        checkName(name);
        Path p = dir.resolve(name);
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    @Override
    public void delete(String name) {
        checkName(name);
        try {
            Files.deleteIfExists(dir.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void checkName(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.contains("..")) {
            throw new IllegalArgumentException(
                "invalid dictionary name '" + name + "': use letters, digits, '.', '_', '-' and end with .xml");
        }
    }
}
