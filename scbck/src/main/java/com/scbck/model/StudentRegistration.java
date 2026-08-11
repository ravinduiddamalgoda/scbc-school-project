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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A student's enrolment into one class for one academic year.
 *
 * The student table carries only a grade, which is why "Student Count of
 * Classes" was impossible to produce: a grade holds seven classes and nothing
 * said which one a student sat in. This is the row that says so.
 *
 * A student may hold at most one enrolment per classroom; moving them to a
 * different class is an update of the existing row, not a second one.
 */
@Entity
@Table(name = "student_registration", uniqueConstraints = @UniqueConstraint(
        name = "uk_registration_student_classroom", columnNames = { "student_id", "classroom_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Zero-padded enrolment reference, generated server-side. */
    @Column(name = "reg_no", unique = true)
    @Length(max = 10)
    private String reg_no;

    @Column(name = "date")
    private LocalDate date;

    private BigDecimal total_fee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "classroom_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Classroom classroom_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registration_status_id", referencedColumnName = "id")
    private RegistrationStatus registration_status_id;
}
