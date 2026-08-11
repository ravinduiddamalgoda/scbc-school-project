package com.scbck.model;

import java.time.LocalDate;

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
 * An academic year, e.g. "2026".
 *
 * The ER model defines this table with nothing but a name. Every report is
 * scoped to one year, so the start/end dates and the "current" flag are added
 * here: without them the server has no way to decide which year a report
 * defaults to, and the client would have to hard-code one.
 */
@Entity
@Table(name = "academic_year")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", unique = true)
    @NotNull(message = "is required")
    private String name;

    private LocalDate start_date;

    private LocalDate end_date;

    /**
     * Exactly one year carries this flag; the server clears it from the others
     * whenever a new one is marked current.
     */
    @Column(name = "current_year")
    private Boolean current_year;
}
