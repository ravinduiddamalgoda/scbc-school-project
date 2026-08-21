package com.scbck.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
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
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a grade's fee is for one academic year.
 *
 * "Amount due" was a number the clerk typed on every receipt. Nothing checked
 * it, nothing defaulted it, and two clerks recording the same grade's fee on
 * the same day could - and did - write different figures, which made the
 * outstanding-balance column of the Fees Details report meaningless.
 *
 * Per year as well as per grade because a fee is set annually. Holding only the
 * current figure would silently restate what last year's families owed the
 * moment the school raised it, and the receipts already issued would stop
 * adding up.
 */
@Entity
@Table(name = "fee_structure",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fee_structure_year_grade",
                columnNames = { "academic_year_id", "grade_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Grade grade;

    /**
     * The full fee for the year.
     *
     * The school's figures are 195,000 for grades 1 to 11 and 25,000 covering
     * grades 12 and 13 together - which is why this is a plain amount per grade
     * rather than a formula: grade 13 charges nothing further because the
     * grade 12 fee already covered it, and only a table can say that.
     */
    @Column(name = "annual_fee", precision = 12, scale = 2)
    @NotNull(message = "is required")
    @PositiveOrZero(message = "cannot be negative")
    private BigDecimal annualFee;

    /** What the fee covers, shown beside the figure on the payment form. */
    @Column(name = "note", length = 255)
    private String note;
}
