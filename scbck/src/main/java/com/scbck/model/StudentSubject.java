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
 * One subject a student actually takes.
 *
 * It points at {@link ClassroomSubject} rather than at the subject directly, so
 * a student can only be enrolled in a subject their own class is offered - the
 * database cannot hold "Grade 6 B takes Combined Maths" by accident.
 *
 * This is what makes the counts in the "Subject wise Student Count" report
 * real numbers rather than a copy of the class size: optional subjects (Art,
 * Music, Dancing) are taken by a subset of the class, which is exactly what the
 * source spreadsheet shows.
 */
@Entity
@Table(name = "student_subject", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_subject", columnNames = { "student_registration_id", "classroom_subject_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_registration_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private StudentRegistration student_registration_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "classroom_subject_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private ClassroomSubject classroom_subject_id;
}
