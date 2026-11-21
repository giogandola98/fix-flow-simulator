package com.fixflow.engine.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a pack from a zip or a folder into {@link PackContents}.
 *
 * <p>A pack holds {@code pack.yaml} and files directly inside four folders:
 * {@code dictionaries/*.xml}, {@code datasets/*.csv}, {@code scenarios/*.yaml} and
 * {@code sessions/*.yaml}. Anything else (other folders, sub-folders, other extensions, absolute
 * paths or {@code ..} segments) is rejected with the entry's name, so a pack can never write
 * outside what the engine expects. A zip whose entries all sit under one top folder
 * ({@code demo/pack.yaml}, as a zipped folder usually comes) is read as if that folder were the root.
 */
public final class PackReader {

    /** Upper bound on the uncompressed size of a pack, against zip bombs. */
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;

    private PackReader() {}

    public static PackContents readZip(InputStream zip) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(zip, StandardCharsets.UTF_8)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = in.getNextEntry()) != null) {
                String name = e.getName();
                checkSafe(name);
                if (e.isDirectory()) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IllegalArgumentException("pack is larger than " + MAX_TOTAL_BYTES / (1024 * 1024) + " MB");
                    }
                    out.write(buf, 0, n);
                }
                if (entries.put(name, out.toByteArray()) != null) {
                    throw new IllegalArgumentException("pack has the entry " + name + " twice");
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("not a readable zip: " + ex.getMessage(), ex);
        }
        return build(stripTopFolder(entries));
    }

    public static PackContents readFolder(Path dir) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                entries.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return build(entries);
    }

    private static void checkSafe(String name) {
        if (name.startsWith("/") || name.contains("\\") || name.contains(":")
                || List.of(name.split("/")).contains("..")) {
            throw new IllegalArgumentException("pack entry " + name + " is not a plain relative path");
        }
    }

    private static Map<String, byte[]> stripTopFolder(Map<String, byte[]> entries) {
        if (entries.containsKey("pack.yaml") || entries.isEmpty()) return entries;
        String first = entries.keySet().iterator().next();
        int slash = first.indexOf('/');
        if (slash < 0) return entries;
        String top = first.substring(0, slash + 1);
        if (!entries.containsKey(top + "pack.yaml")) return entries;
        if (!entries.keySet().stream().allMatch(k -> k.startsWith(top))) return entries;
        Map<String, byte[]> stripped = new LinkedHashMap<>();
        entries.forEach((k, v) -> stripped.put(k.substring(top.length()), v));
        return stripped;
    }

    private static PackContents build(Map<String, byte[]> entries) {
        String manifest = null;
        Map<String, byte[]> dictionaries = new TreeMap<>();
        Map<String, String> datasets = new TreeMap<>();
        Map<String, String> scenarios = new TreeMap<>();
        Map<String, String> sessions = new TreeMap<>();
        List<String> rejected = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String path = e.getKey();
            checkSafe(path);
            if (path.equals("pack.yaml")) {
                manifest = text(e.getValue());
                continue;
            }
            String[] parts = path.split("/");
            if (parts.length != 2 || parts[1].isBlank()) {
                rejected.add(path);
                continue;
            }
            String folder = parts[0];
            String file = parts[1];
            String lower = file.toLowerCase();
            boolean yaml = lower.endsWith(".yaml") || lower.endsWith(".yml");
            switch (folder) {
                case "dictionaries" -> { if (lower.endsWith(".xml")) dictionaries.put(file, e.getValue()); else rejected.add(path); }
                case "datasets" -> { if (lower.endsWith(".csv")) datasets.put(file, text(e.getValue())); else rejected.add(path); }
                case "scenarios" -> { if (yaml) scenarios.put(file, text(e.getValue())); else rejected.add(path); }
                case "sessions" -> { if (yaml) sessions.put(file, text(e.getValue())); else rejected.add(path); }
                default -> rejected.add(path);
            }
        }
        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException("pack files outside dictionaries/*.xml, datasets/*.csv, "
                    + "scenarios/*.yaml and sessions/*.yaml: " + String.join(", ", rejected));
        }
        if (manifest == null) throw new IllegalArgumentException("pack has no pack.yaml at its root");
        return new PackContents(manifest, dictionaries, datasets, scenarios, sessions);
    }

    private static String text(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.startsWith("﻿") ? s.substring(1) : s;
    }
}
