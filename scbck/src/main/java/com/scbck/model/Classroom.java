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
 * One class within a grade for one academic year - "Grade 6 / B", or
 * "Grade 12 / BIO-MATHS" where the A/L streams take the place of a letter.
 *
 * {@code employee_id} is the class teacher. It is the column the "Class
 * Teachers" report reads, and the reason that report could not be produced
 * before: the table existed in the ER model but nothing in the application
 * mapped it.
 */
@Entity
@Table(name = "classroom", uniqueConstraints = @UniqueConstraint(name = "uk_classroom_year_grade_name",
        columnNames = { "academic_year_id", "grade_id", "name" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** The class label inside the grade: "A", "B", or a stream name. */
    @NotNull(message = "is required")
    private String name;

    /**
     * Language of instruction - "Sinhala" or "English".
     *
     * A property of the class, not of the student: the Medium wise Student
     * Count report asks how many children sit in each medium, and that is
     * decided by which class they are in.
     */
    @Column(name = "medium", length = 20)
    private String medium;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Grade grade_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private AcademicYear academic_year_id;

    /** The class teacher. Optional: a class can be waiting for an assignment. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", referencedColumnName = "id")
    private Employee employee_id;
}
