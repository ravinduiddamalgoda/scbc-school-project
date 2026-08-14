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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One student's entry for one public examination in one year.
 *
 * The candidate workbooks ask for three things the school does not otherwise
 * record: which attempt this is, whether the candidate has special needs, and -
 * for the Grade 5 scholarship - the family's income band. They live here rather
 * than on the student because they are facts about sitting an exam, not about
 * the person: a resit is a second attempt, and putting the number on the
 * student record would overwrite the first.
 */
@Entity
@Table(name = "exam_registration", uniqueConstraints = @UniqueConstraint(
        name = "uk_exam_registration", columnNames = { "student_id", "exam", "academic_year_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExamRegistration {

    /** G.C.E. Ordinary Level - Grade 11. */
    public static final String OL = "OL";

    /** G.C.E. Advanced Level - Grade 13. */
    public static final String AL = "AL";

    /** General Information Technology - Grade 12. */
    public static final String GIT = "GIT";

    /** Grade 5 scholarship. */
    public static final String GRADE5 = "GRADE5";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private AcademicYear academic_year_id;

    /** One of {@link #OL}, {@link #AL}, {@link #GIT}, {@link #GRADE5}. */
    @NotNull(message = "is required")
    @Column(name = "exam", length = 12)
    private String exam;

    /** Which sitting this is; the workbooks default it to 1. */
    @Min(value = 1, message = "cannot be below 1")
    @Column(name = "attempt")
    private Integer attempt = 1;

    /** Printed as YES/NO in the workbook's special-needs column. */
    @Column(name = "special_needs")
    private Boolean specialNeeds = Boolean.FALSE;

    /** Grade 5 scholarship only, e.g. "ABOVE 180 000". */
    @Column(name = "income_level")
    private String incomeLevel;

    @Column(name = "note")
    private String note;

    private LocalDateTime updated_datetime;

    private Integer updated_user_id;
}
