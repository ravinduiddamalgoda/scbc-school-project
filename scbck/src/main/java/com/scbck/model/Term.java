package com.scbck.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One term within an academic year.
 *
 * The Week Attendance report is a per-term breakdown - days conducted, days
 * attended, percentage - so the dates each term covers have to be a record
 * somewhere. They were not: the source workbook simply had three column groups
 * labelled "First Term", "Second Term", "Third Term" and whoever filled it in
 * knew which dates those meant.
 *
 * Terms may not overlap within a year; the server enforces that, because an
 * overlapping pair would count the same school day twice.
 */
@Entity
@Table(name = "term", uniqueConstraints = @UniqueConstraint(
        name = "uk_term_year_name", columnNames = { "academic_year_id", "name" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "is required")
    private String name;

    @NotNull(message = "is required")
    private LocalDate start_date;

    @NotNull(message = "is required")
    private LocalDate end_date;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    private AcademicYear academic_year_id;
}
