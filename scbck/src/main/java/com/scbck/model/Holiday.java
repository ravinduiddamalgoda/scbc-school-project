package com.scbck.model;

import java.time.LocalDate;

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
 * A day within the academic year on which school is not conducted.
 *
 * Both attendance reports derive "days school was conducted" from the existence
 * of a register, which is why a holiday needs recording rather than merely
 * remembering: without one, nothing stops a register being opened on Poya day,
 * and that day then counts in every percentage as a day the whole class was
 * absent.
 *
 * School-wide rather than per class. A day off that applied to one class and
 * not another would be a timetable change, not a holiday, and the school's own
 * calendar does not work that way.
 */
@Entity
@Table(name = "holiday", uniqueConstraints = @UniqueConstraint(
        name = "uk_holiday_year_date", columnNames = { "academic_year_id", "date" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "is required")
    @Column(name = "date")
    private LocalDate date;

    @NotNull(message = "is required")
    @Column(name = "name")
    private String name;

    /**
     * Public holiday, school event, unscheduled closure - free text, because
     * the distinction matters to the school and not to the arithmetic.
     */
    @Column(name = "category", length = 40)
    private String category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private AcademicYear academic_year_id;

    @Column(name = "note")
    private String note;
}
