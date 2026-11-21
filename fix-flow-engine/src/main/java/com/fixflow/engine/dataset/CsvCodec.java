package com.fixflow.engine.dataset;

import com.fixflow.core.domain.dataset.DataSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RFC 4180 CSV to {@link DataSet} and back. The first record is the header. A UTF-8 BOM is
 * stripped; CRLF and LF both end a record; fields may be quoted (commas, quotes, newlines).
 * Every error is an {@link IllegalArgumentException} starting {@code line N:}, where N is the
 * physical line (1-based) on which the offending record starts.
 */
public final class CsvCodec {

    private CsvCodec() {}

    public static DataSet parse(String name, String csv) {
        List<List<String>> records = new ArrayList<>();
        List<Integer> startLines = new ArrayList<>();
        String s = csv == null ? "" : csv;
        if (s.startsWith("﻿")) s = s.substring(1);

        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;      // inside a quoted field
        boolean wasQuoted = false;   // current field started with a quote
        int line = 1;
        int recordLine = 1;
        boolean recordHasContent = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else quoted = false;
                } else {
                    if (c == '\n') line++;
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && field.length() == 0 && !wasQuoted) {
                quoted = true; wasQuoted = true; recordHasContent = true;
            } else if (c == ',') {
                record.add(field.toString()); field.setLength(0); wasQuoted = false; recordHasContent = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                if (recordHasContent) {
                    record.add(field.toString());
                    records.add(record);
                    startLines.add(recordLine);
                }
                record = new ArrayList<>(); field.setLength(0); wasQuoted = false; recordHasContent = false;
                line++;
                recordLine = line;
            } else {
                field.append(c); recordHasContent = true;
            }
        }
        if (quoted) throw new IllegalArgumentException("line " + recordLine + ": unterminated quoted field");
        if (recordHasContent) {
            record.add(field.toString());
            records.add(record);
            startLines.add(recordLine);
        }

        if (records.isEmpty()) throw new IllegalArgumentException("line 1: empty CSV, header row required");
        List<String> header = records.get(0);
        Set<String> seen = new HashSet<>();
        for (String h : header) {
            if (h.isBlank()) throw new IllegalArgumentException("line 1: blank column name");
            if (!seen.add(h)) throw new IllegalArgumentException("line 1: duplicate column " + h);
        }
        List<List<String>> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> row = records.get(r);
            if (row.size() != header.size()) {
                throw new IllegalArgumentException(
                    "line " + startLines.get(r) + ": " + row.size() + " values, header has " + header.size());
            }
            rows.add(row);
        }
        return new DataSet(null, name, header, rows, null);
    }

    public static String write(DataSet ds) {
        StringBuilder sb = new StringBuilder();
        appendRecord(sb, ds.columns());
        for (List<String> row : ds.rows()) appendRecord(sb, row);
        return sb.toString();
    }

    private static void appendRecord(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            String v = values.get(i);
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append('\n');
    }
}
