package com.scbck.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Attendance;
import com.scbck.repository.projection.CountByKey;

public interface AttendanceDao extends JpaRepository<Attendance, Integer> {

    @Query("select a from Attendance a where a.classroom_id.id = ?1 and a.date = ?2")
    Attendance getByClassroomAndDate(Integer classroomId, LocalDate date);

    /** The register pages for one class over a period, oldest first. */
    @Query("""
            select a from Attendance a
            where a.classroom_id.id = ?1 and a.date between ?2 and ?3
            order by a.date
            """)
    List<Attendance> listByClassroomBetween(Integer classroomId, LocalDate from, LocalDate to);

    /** How many days school was conducted for one class in a period. */
    @Query("""
            select count(a) from Attendance a
            where a.classroom_id.id = ?1 and a.date between ?2 and ?3
            """)
    long countDays(Integer classroomId, LocalDate from, LocalDate to);

    /**
     * How many days school was conducted for each class in a period.
     *
     * A day counts because a register was marked for it, so holidays need no
     * separate calendar - they are simply dates nobody marked.
     */
    @Query("""
            select a.classroom_id.id as keyId, count(a.id) as total
            from Attendance a
            where a.classroom_id.academic_year_id.id = ?1 and a.date between ?2 and ?3
            group by a.classroom_id.id
            """)
    List<CountByKey> countDaysConducted(Integer academicYearId, LocalDate from, LocalDate to);

    /**
     * How many classes already have a register on one date.
     *
     * Guards declaring a holiday retrospectively: attendance on a date means
     * school was conducted, and a register on a day no report counts would be
     * marks that exist but are invisible.
     */
    @Query("""
            select count(a) from Attendance a
            where a.classroom_id.academic_year_id.id = ?1 and a.date = ?2
            """)
    long countDaysAcrossClasses(Integer academicYearId, LocalDate date);
}
