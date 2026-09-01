package com.fixflow.adapters.persistence;

import com.fixflow.adapters.persistence.entity.ScenarioVersionEntity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #103: the unique constraint on scenario_versions must carry an explicit name. Left
 * unnamed, Hibernate derives a hash name that its schema update then re-issued on every start
 * against an existing database ("object already exists: UKa0ww2nwufmhx27cric1ols0gb").
 */
class ScenarioVersionConstraintTest {

    @Test
    void uniqueConstraintIsNamedExplicitly() {
        Table table = ScenarioVersionEntity.class.getAnnotation(Table.class);
        assertThat(table.uniqueConstraints()).hasSize(1);

        UniqueConstraint constraint = table.uniqueConstraints()[0];
        assertThat(constraint.name()).isEqualTo("uk_scenario_versions_scenario_id_version");
        assertThat(constraint.columnNames()).containsExactly("scenario_id", "version");
    }
}
