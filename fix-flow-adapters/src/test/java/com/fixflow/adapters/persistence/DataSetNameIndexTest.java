package com.fixflow.adapters.persistence;

import com.fixflow.core.domain.dataset.DataSet;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production profile runs {@code ddl-auto: update} with {@code unique_constraint_strategy:
 * SKIP}, so the {@code @UniqueConstraint} on {@code DataSetEntity} is never created there. These
 * tests run with the same two settings.
 */
class DataSetNameIndexTest {

    static boolean indexExists(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             ResultSet rs = c.getMetaData().getIndexInfo(null, null, "DATA_SETS", true, false)) {
            while (rs.next()) {
                if ("UK_DATA_SETS_NAME".equalsIgnoreCase(rs.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }

    private static DataSet ds(String name) {
        return new DataSet(UUID.randomUUID(), name, List.of("a"), List.of(List.of("1")), null);
    }

    @Nested
    @DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.schema_update.unique_constraint_strategy=SKIP"
    })
    @Import(DataSetNameIndex.class)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class WithStartupIndex {
        @Autowired DataSource dataSource;
        @Autowired DataSetRepositoryAdapter adapter;

        @Test
        void indexExistsAfterStartupOnUpdateModeDb() throws Exception {
            assertThat(indexExists(dataSource)).isTrue();
        }

        @Test
        void duplicateNameIsRejectedByTheIndex() {
            adapter.save(ds("idx-dup"));
            assertThatThrownBy(() -> adapter.save(ds("idx-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.schema_update.unique_constraint_strategy=SKIP"
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class DatabaseAlreadyHoldingDuplicates {
        @Autowired DataSource dataSource;
        @Autowired EntityManagerFactory emf;
        @Autowired DataSetRepositoryAdapter adapter;

        @Test
        void duplicatesSkipTheIndexAndLookupAnswersTheOldestRow() throws Exception {
            DataSet first = adapter.save(ds("old-dup"));
            Thread.sleep(5);
            adapter.save(ds("old-dup"));

            boolean created = new DataSetNameIndex(dataSource, emf).ensure();

            assertThat(created).isFalse();
            assertThat(indexExists(dataSource)).isFalse();
            assertThat(adapter.findByName("old-dup")).map(DataSet::id).contains(first.id());
        }
    }
}
