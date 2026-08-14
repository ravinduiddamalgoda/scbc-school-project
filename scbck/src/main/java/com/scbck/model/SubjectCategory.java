package com.scbck.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A grouping of subjects - "Core", "Category 2", "Aesthetic".
 *
 * This used to be a free-text column on {@link SubjectDetail} whose allowed
 * values were a hard-coded array in the client. That made the mark sheet's
 * column bands unchangeable from inside the application: the sheets group the
 * compulsory subjects, then the basket a student picks one subject from, then
 * the aesthetic basket, and a school that added a fourth basket had no way to
 * say so.
 *
 * {@code sortOrder} is what fixes the left-to-right order of those bands on the
 * mark sheet. Ties fall back to name, so a category added without a thought
 * about ordering still lands somewhere predictable rather than moving between
 * exports.
 */
@Entity
@Table(name = "subject_category")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubjectCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "is required")
    @Column(name = "name", unique = true, length = 60)
    private String name;

    /**
     * Position of the category's column band on the mark sheet, lowest first.
     */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * How many subjects from this category a student is expected to take, or
     * null when there is no rule.
     *
     * The sheets imply this without recording it - every student has marks in
     * all seven compulsory subjects but exactly one from each optional basket.
     * Stored so the marks screen can warn when a student's picks do not match,
     * rather than the mistake surfacing as a wrong average months later.
     */
    @Column(name = "expected_subjects")
    private Integer expectedSubjects;

    /** Retired categories stay readable on old sheets but cannot be assigned. */
    @Column(name = "active")
    private Boolean active = Boolean.TRUE;
}
