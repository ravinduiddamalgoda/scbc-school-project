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
 * A subject as it is taught to one class, and by whom.
 *
 * This link is missing from the ER model entirely, which is why neither the
 * "Subject Wise Teachers" nor the "Subject wise Student Count" report could be
 * produced: {@code subject_detail} listed the subjects and {@code employee}
 * listed the staff, but nothing recorded that a given teacher takes a given
 * subject for a given class.
 */
@Entity
@Table(name = "classroom_subject", uniqueConstraints = @UniqueConstraint(
        name = "uk_classroom_subject", columnNames = { "classroom_id", "subject_detail_id" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassroomSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "classroom_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Classroom classroom_id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_detail_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private SubjectDetail subject_detail_id;

    /** The subject teacher. Optional until the timetable is settled. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", referencedColumnName = "id")
    private Employee employee_id;
}
