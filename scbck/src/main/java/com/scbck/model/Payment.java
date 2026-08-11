package com.scbck.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A fee payment received from a student.
 *
 * The Fees Details report is a payment history read grade by grade, so each
 * payment points at the enrolment it settles rather than only at the student:
 * the enrolment is what knows which grade and which year the money was for.
 * Reading the grade off the student record instead would relabel every past
 * payment the moment the student was promoted.
 */
@Entity
@Table(name = "payment")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "bill_no", unique = true)
    @Length(max = 12)
    private String bill_no;

    @NotNull(message = "is required")
    private BigDecimal amount_paid;

    /** What was owed for the period this payment covers. */
    private BigDecimal amount_due;

    /** Still outstanding after this payment; kept as the ER model defines it. */
    private BigDecimal balance_amount;

    @NotNull(message = "is required")
    private LocalDate paid_date;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_type_id", referencedColumnName = "id")
    private PaymentType payment_type_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student_id;

    /** The year and grade the payment was for. Optional for ad-hoc receipts. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_registration_id", referencedColumnName = "id")
    private StudentRegistration student_registration_id;
}
