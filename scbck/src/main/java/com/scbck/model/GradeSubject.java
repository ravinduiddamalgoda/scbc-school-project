package com.scbck.model;

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
 * One subject on one grade's curriculum.
 *
 * Which subjects a grade is taught was, until now, nowhere in the system. The
 * Classes screen offered all twenty-nine subjects for every class from grade 1
 * to grade 13, the mark sheet printed whatever had been ticked, and the Subject
 * Wise Teachers report counted against that same free choice - so the answer to
 * "how many Sinhala teachers does grade 4 need" depended on what somebody
 * happened to tick, not on the curriculum.
 *
 * This table is the curriculum: grade 1 takes five subjects, grade 6 takes
 * thirteen, and both the timetable editor and the reports read the list from
 * here rather than from the whole subject table.
 *
 * It is deliberately separate from {@link SubjectCategory}. A category is the
 * examination basket a subject is classified under and one subject sits in
 * several of them across the grades - Sinhala is 6-9 Core, O/L Core and an A/L
 * Category 1 subject all at once, which a single category column on the subject
 * cannot express. {@link #basket} records the column the subject prints in on
 * that grade's sheet, which is the only part of the grouping that is per-grade.
 */
@Entity
@Table(name = "grade_subject",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grade_subject",
                columnNames = { "grade_id", "subject_detail_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GradeSubject {

    /** Every student in the grade takes it. */
    public static final String CORE = "Core";

    /** The A/L and O/L optional baskets, one subject picked from each. */
    public static final String CATEGORY_1 = "Cat 1";
    public static final String CATEGORY_2 = "Cat 2";
    public static final String CATEGORY_3 = "Cat 3";

    /** The two A/L subjects every candidate sits regardless of stream. */
    public static final String GENERAL = "General";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Grade grade;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_detail_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private SubjectDetail subject;

    /**
     * Which column of the grade's mark sheet this subject prints in - one of
     * {@link #CORE}, {@link #CATEGORY_1}, {@link #CATEGORY_2},
     * {@link #CATEGORY_3} or {@link #GENERAL}.
     */
    @Column(name = "basket", length = 20)
    private String basket = CORE;

    /**
     * Curriculum order within the basket, lowest first.
     *
     * The school's sheets read Sinhala, Buddhism, Mathematics, Science - not
     * alphabetically - and this is what holds that order per grade, which the
     * subject's own sort order cannot do because the order differs between
     * grade 5 and grade 10.
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * True when the subject is taught by the class teacher rather than by a
     * subject teacher.
     *
     * The Subject Wise Teachers report needs this: in grades 1 to 5 Sinhala,
     * Mathematics, Environment Science and Buddhism are all taken by the class
     * teacher, so the teacher count for those subjects must equal the number of
     * classes and never anything else. English, Tamil and IT in the same grades
     * are taken by visiting subject teachers, so they are counted normally.
     */
    @Column(name = "class_teacher_taught")
    private Boolean classTeacherTaught = Boolean.FALSE;
}
