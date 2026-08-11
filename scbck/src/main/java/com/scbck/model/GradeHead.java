package com.scbck.model;

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
 * The teacher heading a whole grade for one academic year.
 *
 * Distinct from a class teacher: a grade head is responsible for all seven
 * classes of a grade. The relationship is absent from the ER model, which is
 * why the Grade Heads workbook had a name column nothing could fill in.
 *
 * Scoped to a year, like everything else here, so last year's grade heads stay
 * readable in last year's report.
 */
@Entity
@Table(name = "grade_head", uniqueConstraints = @UniqueConstraint(
        name = "uk_grade_head_year_grade", columnNames = { "academic_year_id", "grade_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Grade grade_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private AcademicYear academic_year_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Employee employee_id;
}
