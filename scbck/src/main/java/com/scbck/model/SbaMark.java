package com.scbck.model;

import java.time.LocalDateTime;
import java.util.List;

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
 * One School Based Assessment mark: one student, one subject, one term of one
 * grade.
 *
 * Not the same thing as {@link StudentMark}, which is a term examination result
 * for the school's own report. An SBA mark is coursework assessed continuously
 * and submitted to the Department of Examinations, and the two are entered at
 * different times by different people for different purposes - which is why the
 * school asked for a module of its own rather than more columns on the marks
 * screen.
 *
 * The grain is deliberately (grade, term) rather than a row per candidate with
 * five mark columns. The Department's sheet merges two grades - grades 12 and
 * 13 for A/L, grades 10 and 11 for O/L - but the marks are awarded a term at a
 * time, a year apart, by whoever teaches the subject that year. Storing them
 * the way they are awarded means a teacher entering grade 12's third term
 * cannot overwrite what grade 13 recorded, and the merge happens on the way
 * out, where it belongs.
 */
@Entity
@Table(name = "sba_mark",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sba_mark",
                columnNames = { "student_id", "subject_detail_id", "exam", "exam_year",
                        "grade_number", "term_number" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SbaMark {

    /** G.C.E. Advanced Level - grades 12 and 13. */
    public static final String AL = "AL";

    /** G.C.E. Ordinary Level - grades 10 and 11. */
    public static final String OL = "OL";

    public static final List<String> EXAMS = List.of(AL, OL);

    /**
     * The grades each examination's assessment is collected over, junior first.
     *
     * The Department's sheet prints them the other way round - the senior
     * grade's terms come first, because they are the most recent - which the
     * exporter handles rather than the storage.
     */
    public static List<Integer> gradesFor(String exam) {
        return AL.equals(exam) ? List.of(12, 13) : List.of(10, 11);
    }

    /**
     * Which terms of a grade are assessed.
     *
     * The junior grade contributes all three terms; the senior grade only its
     * first two, because the examination itself falls in the third. This is the
     * shape of the Department's sheet: five mark columns, not six.
     */
    public static List<Integer> termsFor(String exam, int grade) {
        int senior = AL.equals(exam) ? 13 : 11;
        return grade == senior ? List.of(1, 2) : List.of(1, 2, 3);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_detail_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private SubjectDetail subject;

    /** {@link #AL} or {@link #OL}. */
    @Column(name = "exam", length = 4)
    @NotNull(message = "is required")
    private String exam;

    /**
     * The year of the examination the assessment counts towards, not the year
     * the mark was awarded.
     *
     * A grade 12 mark awarded in 2025 belongs to the 2026 A/L sheet, and
     * filing it under 2025 is what would split one candidate's five marks
     * across two workbooks.
     */
    @Column(name = "exam_year")
    @NotNull(message = "is required")
    private Integer examYear;

    /** 12 or 13 for A/L; 10 or 11 for O/L. */
    @Column(name = "grade_number")
    @NotNull(message = "is required")
    private Integer gradeNumber;

    /** 1, 2 or 3. */
    @Column(name = "term_number")
    @NotNull(message = "is required")
    @Min(value = 1, message = "must be 1, 2 or 3")
    @Max(value = 3, message = "must be 1, 2 or 3")
    private Integer termNumber;

    /**
     * The mark out of 20, as the Department's sheet awards it.
     *
     * Null means "not assessed", which is different from zero: a candidate who
     * did not submit the work scores zero, one whose teacher has not marked it
     * yet has no mark, and the total must not treat them alike.
     */
    @Column(name = "marks")
    @Min(value = 0, message = "cannot be negative")
    @Max(value = 100, message = "cannot be more than 100")
    private Integer marks;

    private LocalDateTime added_datetime;

    private LocalDateTime updated_datetime;

    /** Who last wrote the mark - the accountability trail for an SBA return. */
    private Integer updated_user_id;
}
