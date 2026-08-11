package com.scbck.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentAttendance;
import com.scbck.repository.projection.CountByKey;

public interface StudentAttendanceDao extends JpaRepository<StudentAttendance, Integer> {

    @Query("select m from StudentAttendance m where m.attendence_id.id = ?1")
    List<StudentAttendance> listByAttendance(Integer attendanceId);

    /**
     * Every mark for one class over a period, in register order.
     *
     * The month register needs each individual mark, not a total, so this is
     * the one report query that reads rows rather than aggregating - bounded by
     * one class and one month, it is a few hundred of them.
     */
    @Query("""
            select m from StudentAttendance m
            where m.attendence_id.classroom_id.id = ?1
              and m.attendence_id.date between ?2 and ?3
            order by m.attendence_id.date, m.student_id.stu_no
            """)
    List<StudentAttendance> listByClassroomBetween(Integer classroomId, LocalDate from, LocalDate to);

    /**
     * Days present per student for one class over a period. Only "present"
     * marks are counted; an absence is a stored row too, which is what keeps a
     * half-marked register from reading as a full one.
     */
    @Query("""
            select m.student_id.id as keyId, count(m.id) as total
            from StudentAttendance m
            where m.attendence_id.classroom_id.id = ?1
              and m.attendence_id.date between ?2 and ?3
              and m.attendant = true
            group by m.student_id.id
            """)
    List<CountByKey> countPresentByStudent(Integer classroomId, LocalDate from, LocalDate to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StudentAttendance m where m.attendence_id.id = ?1")
    void deleteByAttendance(Integer attendanceId);
}
