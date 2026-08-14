package com.scbck.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item issued to one student.
 *
 * Hangs off the enrolment rather than the student, so an issue is automatically
 * tied to the class and academic year it happened in - which is what the sheets
 * are filed by, and what stops last year's uniform appearing on this year's
 * list.
 *
 * The paper sheets carry a signature column. That stays paper: the column is
 * printed blank for the student to sign, because a tick in a database is not
 * what the school needs when a parent says the books never arrived.
 */
@Entity
@Table(name = "student_distribution", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_distribution", columnNames = { "student_registration_id", "distribution_item_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_registration_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private StudentRegistration student_registration_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "distribution_item_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private DistributionItem distribution_item_id;

    /** How many were issued. Zero is not stored - the row is removed instead. */
    @Min(value = 0, message = "cannot be negative")
    @Column(name = "quantity")
    private Integer quantity;

    private LocalDate issued_date;

    @Column(name = "note")
    private String note;

    private LocalDateTime updated_datetime;

    private Integer updated_user_id;
}
