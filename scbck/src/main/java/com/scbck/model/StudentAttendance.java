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
 * One student's mark on one day's register: present or absent.
 *
 * Absences are stored, not implied by a missing row. "Absent" and "not marked
 * yet" are different facts, and the percentage in the Week Attendance report
 * is wrong if they are conflated - a half-marked register would otherwise read
 * as a day of perfect attendance.
 *
 * Table name follows the ER model's spelling; see {@link Attendance}.
 */
@Entity
@Table(name = "student_has_attendence", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_attendance", columnNames = { "student_id", "attendence_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attendence_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Attendance attendence_id;

    /** True when the student was present. The ER model calls it "attendant". */
    @NotNull(message = "is required")
    private Boolean attendant;
}
