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
 * One day's attendance for one class - the register page a class teacher marks.
 *
 * The existence of a row is itself information: it means school was conducted
 * for this class on this date. That is what the attendance reports count as
 * "days conducted", so a holiday is simply a date with no row, and no report
 * has to be told the school calendar separately.
 *
 * The table is named "attendence" because that is the spelling in the ER model
 * (scbcer.mwb); renaming it would break a forward-engineered schema. The Java
 * side spells it correctly.
 */
@Entity
@Table(name = "attendence", uniqueConstraints = @UniqueConstraint(
        name = "uk_attendance_classroom_date", columnNames = { "classroom_id", "date" }))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "date")
    @NotNull(message = "is required")
    private LocalDate date;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "classroom_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Classroom classroom_id;

    /**
     * Day totals, kept because the ER model defines them.
     *
     * They are recomputed from the per-student marks every time the register is
     * saved, and no report reads them - the reports count the marks. A stored
     * total that could disagree with the rows beneath it is exactly the failure
     * the old spreadsheets had.
     */
    private Integer total_present;

    private Integer total_abscent;

    private Integer total_child_count;
}
