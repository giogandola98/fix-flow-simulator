package com.fixflow.adapters.persistence;

import com.fixflow.core.domain.dataset.DataSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class DataSetRepositoryAdapterTest {

    @Autowired DataSetRepositoryAdapter adapter;

    private DataSet ds(UUID id, String name) {
        return new DataSet(id, name, List.of("a", "b"),
            List.of(List.of("1", "x,y"), List.of("", "line\nbreak \"q\"")), "pack1");
    }

    @Test
    void saveAndFindRoundTripsEverything() {
        UUID id = UUID.randomUUID();
        adapter.save(ds(id, "d1"));

        DataSet loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded).isEqualTo(ds(id, "d1"));
        assertThat(adapter.findByName("d1")).contains(loaded);
        assertThat(adapter.findByName("nope")).isEmpty();
    }

    @Test
    void saveWithoutIdAssignsOne() {
        DataSet saved = adapter.save(ds(null, "d2"));
        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).isPresent();
    }

    @Test
    void saveExistingIdUpdates() {
        UUID id = UUID.randomUUID();
        adapter.save(ds(id, "d3"));
        adapter.save(new DataSet(id, "d3b", List.of("z"), List.of(List.of("9")), null));

        DataSet loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("d3b");
        assertThat(loaded.columns()).containsExactly("z");
        assertThat(loaded.packId()).isNull();
        assertThat(adapter.findAll()).hasSize(1);
    }

    @Test
    void duplicateNameViolatesUnique() {
        adapter.save(ds(UUID.randomUUID(), "dup"));
        assertThatThrownBy(() -> adapter.save(ds(UUID.randomUUID(), "dup")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteRemoves() {
        UUID id = UUID.randomUUID();
        adapter.save(ds(id, "d4"));
        adapter.delete(id);
        assertThat(adapter.findById(id)).isEmpty();
    }
}
