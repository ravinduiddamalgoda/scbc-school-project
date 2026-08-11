package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs both seed scripts against the schema Hibernate generates from the
 * entities, twice each.
 *
 * It guards two things a seed script silently gets wrong otherwise: that its
 * column names still match the entities after a rename, and that it really is
 * re-runnable - a seed that duplicates its rows on a second run is worse than
 * one that fails, because nothing complains until a report double-counts.
 *
 * The database here is H2 in MySQL mode, not MySQL, so this proves the logic
 * rather than the dialect.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeedScriptIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void academicSeedRunsTwiceWithoutDuplicating() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            run(connection, "seed/academic-seed.sql", 2);

            assertThat(count(connection, "role")).isEqualTo(4);
            assertThat(count(connection, "status")).isEqualTo(3);
            assertThat(count(connection, "student_status")).isEqualTo(3);
            // Nothing below works without these: an employee with no
            // designation is not a teacher, and the sample data would then
            // build a school with no staff on it.
            assertThat(count(connection, "designation")).isEqualTo(3);
            assertThat(count(connection, "designation where role_id is null")).isZero();
            assertThat(count(connection, "module")).isEqualTo(10);
            assertThat(count(connection, "grade")).isEqualTo(13);
            assertThat(count(connection, "subject_detail")).isEqualTo(29);
            assertThat(count(connection, "registration_status")).isEqualTo(3);
            assertThat(count(connection, "academic_year")).isEqualTo(1);
            assertThat(count(connection, "term")).isEqualTo(3);
            assertThat(count(connection, "payment_type")).isEqualTo(4);
            // 11 grades x 7 letters, plus 2 A/L grades x 4 streams.
            assertThat(count(connection, "classroom")).isEqualTo(85);
            // The Medium wise report needs this set, so the seed must set it.
            assertThat(count(connection, "classroom where medium is null")).isZero();
        }
    }

    /**
     * The sample data is what makes a fresh install show nine populated reports
     * rather than nine empty ones, so the assertions below check it actually
     * fills every table a report reads - and that a second run adds nothing.
     */
    @Test
    void sampleDataRunsTwiceWithoutDuplicating() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            run(connection, "seed/academic-seed.sql", 1);
            run(connection, "seed/sample-data.sql", 1);

            int employees = count(connection, "employee");
            int students = count(connection, "student");
            int marks = count(connection, "student_has_attendence");

            assertThat(employees).isEqualTo(100);
            assertThat(students).isBetween(2000, 3500);
            assertThat(count(connection, "guardian")).isEqualTo((students + 1) / 2);

            // Names are generated from initials plus a name drawn out of two
            // short lists; the arithmetic has to keep them distinct, or a roll
            // of 2,800 reads as the same dozen people over and over.
            assertThat(distinct(connection, "fullname", "student")).isEqualTo(students);

            // Every class has a teacher and every grade a head, so neither
            // report prints a page of "Not assigned".
            assertThat(count(connection, "classroom where employee_id is null")).isZero();
            assertThat(count(connection, "grade_head")).isEqualTo(13);

            // Both media are represented; a single-column report proves nothing.
            assertThat(count(connection, "classroom where medium = 'English'")).isPositive();
            assertThat(count(connection, "classroom where medium = 'Sinhala'")).isPositive();

            assertThat(count(connection, "classroom_subject")).isPositive();
            assertThat(count(connection, "student_registration")).isEqualTo(students);
            assertThat(count(connection, "student_subject")).isPositive();

            // 15 school days across 85 classes.
            assertThat(count(connection, "attendence")).isEqualTo(15 * 85);
            assertThat(marks).isPositive();
            // Both outcomes occur, so a percentage is never trivially 100%.
            assertThat(count(connection, "student_has_attendence where attendant = true")).isPositive();
            assertThat(count(connection, "student_has_attendence where attendant = false")).isPositive();
            // The denormalised day totals must agree with the marks beneath them.
            assertThat(count(connection,
                    "attendence a where a.total_present + a.total_abscent <> a.total_child_count")).isZero();

            assertThat(count(connection, "payment")).isEqualTo(students * 2);
            assertThat(count(connection, "privilage")).isPositive();
            assertThat(count(connection, "user")).isEqualTo(3);
            assertThat(count(connection, "user_has_role")).isEqualTo(3);

            // Optional subjects are taken by a subset, so a "students taking
            // this subject" figure cannot just be the class size.
            assertThat(count(connection, "student_subject")).isLessThan(
                    count(connection, "student_registration")
                            * count(connection, "classroom_subject")
                            / Math.max(1, count(connection, "classroom")));

            // Second pass: nothing new.
            run(connection, "seed/sample-data.sql", 1);

            assertThat(count(connection, "employee")).isEqualTo(employees);
            assertThat(count(connection, "student")).isEqualTo(students);
            assertThat(count(connection, "student_has_attendence")).isEqualTo(marks);
            assertThat(count(connection, "payment")).isEqualTo(students * 2);
            assertThat(count(connection, "user")).isEqualTo(3);
        }
    }

    // -------------------------------------------------------------------------

    private void run(Connection connection, String path, int passes) throws Exception {
        // Comments come out before the split: a "--" line may itself contain a
        // semicolon, and splitting first would cut a statement in half.
        String script = Files.readString(Path.of(path)).replaceAll("(?m)^\\s*--.*$", "");

        String[] statements = Arrays.stream(script.split(";"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toArray(String[]::new);

        for (int pass = 1; pass <= passes; pass++) {
            for (String sql : statements) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                } catch (Exception error) {
                    throw new AssertionError(path + " pass " + pass + " failed on:\n" + sql, error);
                }
            }
        }
    }

    private int distinct(Connection connection, String column, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement
                        .executeQuery("select count(distinct " + column + ") from " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select count(*) from " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
