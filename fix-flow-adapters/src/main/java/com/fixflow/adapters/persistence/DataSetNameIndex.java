package com.fixflow.adapters.persistence;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the unique index on {@code data_sets(name)} at startup.
 *
 * <p>{@code DataSetEntity} declares {@code uk_data_sets_name}, but the application runs with
 * {@code ddl-auto: update} and {@code unique_constraint_strategy: SKIP} (needed so restarts do not
 * re-issue existing constraints), so Hibernate never creates it. Without the index two concurrent
 * creates with the same name both succeed and every lookup by that name becomes ambiguous.
 *
 * <p>A database that already holds duplicate names cannot take the index: the duplicates are
 * logged at WARN and the index is skipped, so startup never fails on old data. Lookups by name
 * stay deterministic in that case (oldest row wins, see {@code JpaDataSetRepository}).
 *
 * <p>The {@link EntityManagerFactory} parameter is there only to order this after Hibernate's
 * schema update, so the table exists when the index is created.
 */
@Component
public class DataSetNameIndex {

    static final String INDEX_NAME = "uk_data_sets_name";
    private static final Logger log = LoggerFactory.getLogger(DataSetNameIndex.class);

    private final DataSource dataSource;

    public DataSetNameIndex(DataSource dataSource, EntityManagerFactory schemaReady) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void init() { ensure(); }

    /** Returns true when the index exists (created now or already there). */
    public boolean ensure() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            List<String> duplicates = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                "SELECT name, COUNT(*) FROM data_sets GROUP BY name HAVING COUNT(*) > 1 ORDER BY name")) {
                while (rs.next()) duplicates.add("'" + rs.getString(1) + "' x" + rs.getLong(2));
            }
            if (!duplicates.isEmpty()) {
                log.warn("data_sets holds duplicate names {}; unique index {} not created. "
                    + "Rename or delete the duplicates and restart to enforce unique names.",
                    duplicates, INDEX_NAME);
                return false;
            }
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + INDEX_NAME + " ON data_sets(name)");
            return true;
        } catch (SQLException e) {
            log.warn("cannot create unique index {} on data_sets(name): {}", INDEX_NAME, e.getMessage());
            return false;
        }
    }
}
