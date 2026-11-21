package com.fixflow.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixflow.core.domain.dataset.DataSet;

import java.util.List;
import java.util.UUID;

/** Wire form of a data set. The list endpoint omits {@code rows}; requests may omit id, packId, rowCount. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataSetDto(UUID id, String name, String packId, List<String> columns,
                         List<List<String>> rows, Integer rowCount) {

    public static DataSetDto full(DataSet d) {
        return new DataSetDto(d.id(), d.name(), d.packId(), d.columns(), d.rows(), d.rows().size());
    }

    public static DataSetDto summary(DataSet d) {
        return new DataSetDto(d.id(), d.name(), d.packId(), d.columns(), null, d.rows().size());
    }
}
