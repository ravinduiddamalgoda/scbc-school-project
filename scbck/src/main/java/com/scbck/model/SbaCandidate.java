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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The two per-candidate columns of the Department's assessment sheet that are
 * not term marks: the group a candidate worked in, and their project mark.
 *
 * Held apart from {@link SbaMark} because they have a different grain. A term
 * mark is awarded five times across two grades; a group and a project mark are
 * awarded once for the whole assessment. Folding them into the mark table would
 * mean either repeating them on all five rows - and having to decide which copy
 * is right when they disagree - or inventing a sixth "term" that is not a term.
 */
@Entity
@Table(name = "sba_candidate",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sba_candidate",
                columnNames = { "student_id", "subject_detail_id", "exam", "exam_year" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SbaCandidate {

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

    @Column(name = "exam", length = 4)
    @NotNull(message = "is required")
    private String exam;

    @Column(name = "exam_year")
    @NotNull(message = "is required")
    private Integer examYear;

    /** The working group the candidate was assigned to, as the school labels it. */
    @Column(name = "group_name", length = 20)
    @Size(max = 20, message = "is too long")
    private String groupName;

    /** The project mark, or null when no project has been assessed. */
    @Column(name = "project_marks")
    @Min(value = 0, message = "cannot be negative")
    @Max(value = 100, message = "cannot be more than 100")
    private Integer projectMarks;
}
