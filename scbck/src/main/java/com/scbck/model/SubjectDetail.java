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
 * A subject on the curriculum - "Buddhism", "Combined Maths", "Accounts".
 *
 * The ER model has the table (subject_detail) with only id and name. A subject
 * that is retired must stay in the database so historical reports keep their
 * meaning, which is what {@code active} is for; {@code code} gives the reports
 * a short column heading, and {@code category} groups the A/L optional baskets
 * the report spreadsheets label "Category 1/2/3".
 */
@Entity
@Table(name = "subject_detail")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubjectDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", unique = true)
    @NotNull(message = "is required")
    private String name;

    /** Short form used as a column heading when the full name will not fit. */
    @Column(name = "code", length = 12)
    private String code;

    /** Free-text grouping, e.g. "Core", "Optional", "Category 1". */
    @Column(name = "category")
    private String category;

    /** Retired subjects stay readable in old reports but cannot be assigned. */
    @Column(name = "active")
    private Boolean active = Boolean.TRUE;
}
