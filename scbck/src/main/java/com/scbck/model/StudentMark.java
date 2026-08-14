package com.scbck.model;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One student's mark in one subject for one term.
 *
 * It hangs off {@link StudentSubject} rather than off the student and the
 * subject separately, which means a mark can only exist for a subject the
 * student is actually enrolled in, on a class that actually offers it. The
 * source spreadsheets had no such constraint and it shows: a student with a
 * mark under an optional subject they never took changes their average and
 * their rank, and nothing in the sheet says which of the two facts is wrong.
 *
 * Absence is its own field rather than a magic mark value. The sheets write
 * "AB" in the mark cell, so absence and zero look alike to anything summing the
 * column - but an absent student is excluded from the subject's pass counts,
 * while a zero is a fail that belongs in them.
 */
@Entity
@Table(name = "student_mark", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_mark_term", columnNames = { "student_subject_id", "term_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_subject_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private StudentSubject student_subject_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "term_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Term term_id;

    /**
     * The mark out of 100, or null when the student was absent.
     *
     * Nullable rather than zero-defaulted: a subject nobody has entered a mark
     * for yet must print as blank, not as a fail.
     */
    @Min(value = 0, message = "cannot be below 0")
    @Max(value = 100, message = "cannot be above 100")
    @Column(name = "marks")
    private Integer marks;

    /** True when the student sat no paper; prints as "AB". */
    @Column(name = "absent")
    private Boolean absent = Boolean.FALSE;

    @Column(name = "note")
    private String note;

    private LocalDateTime added_datetime;

    private LocalDateTime updated_datetime;

    /**
     * Who last wrote this mark.
     *
     * Any teacher may enter marks for any class, so the audit trail is what
     * makes that workable: a disputed mark can be traced to the account that
     * entered it.
     */
    private Integer updated_user_id;
}
