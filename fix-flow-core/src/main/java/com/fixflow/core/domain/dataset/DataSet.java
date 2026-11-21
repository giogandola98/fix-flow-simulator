package com.fixflow.core.domain.dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A named table of string values: a header of unique column names and rows of the same width.
 * {@code packId} is set when the data set belongs to a provider pack.
 */
public record DataSet(UUID id, String name, List<String> columns, List<List<String>> rows, String packId) {

    public DataSet {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("data set name must not be blank");
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("data set needs at least one column");
        if (new LinkedHashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("duplicate column in " + columns);
        }
        columns = List.copyOf(columns);
        List<List<String>> copy = new ArrayList<>();
        int i = 0;
        for (List<String> row : rows == null ? List.<List<String>>of() : rows) {
            i++;
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException(
                    "row " + i + " has " + row.size() + " values, header has " + columns.size());
            }
            copy.add(List.copyOf(row));
        }
        rows = List.copyOf(copy);
    }

    /** Column to value for the row at {@code index} (0-based, header excluded). */
    public Map<String, String> row(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IndexOutOfBoundsException("row index " + index + " out of range, data set has " + rows.size() + " rows");
        }
        Map<String, String> m = new LinkedHashMap<>();
        List<String> r = rows.get(index);
        for (int c = 0; c < columns.size(); c++) m.put(columns.get(c), r.get(c));
        return m;
    }
}
