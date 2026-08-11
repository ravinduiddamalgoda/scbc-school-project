package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentRegistration;
import com.scbck.repository.projection.CountByKey;

public interface StudentRegistrationDao extends JpaRepository<StudentRegistration, Integer> {

    /**
     * Next enrolment reference as a plain number; the caller zero-pads it.
     *
     * CAST to DECIMAL rather than MySQL's UNSIGNED: the same statement then runs
     * on the H2 instance the tests use. COALESCE keeps the first insert working
     * on an empty table.
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(r.reg_no AS DECIMAL(18,0))), 0) + 1
            FROM student_registration r
            """, nativeQuery = true)
    long nextRegSequence();

    @Query("select r from StudentRegistration r where r.student_id.id = ?1 order by r.id desc")
    List<StudentRegistration> listByStudent(Integer studentId);

    @Query("""
            select r from StudentRegistration r
            where r.classroom_id.id = ?1
            order by r.student_id.fullname
            """)
    List<StudentRegistration> listByClassroom(Integer classroomId);

    @Query("""
            select r from StudentRegistration r
            where r.student_id.id = ?1 and r.classroom_id.id = ?2
            """)
    StudentRegistration getByStudentAndClassroom(Integer studentId, Integer classroomId);

    /**
     * Head count per class for one academic year.
     *
     * An enrolment counts when it is active (or has no status recorded yet) and
     * the student behind it has not been soft-deleted - otherwise a removed
     * student would keep inflating their old class for ever.
     */
    @Query("""
            select r.classroom_id.id as keyId, count(r.id) as total
            from StudentRegistration r
            where r.classroom_id.academic_year_id.id = ?1
              and (r.registration_status_id is null or lower(r.registration_status_id.name) = 'active')
              and (r.student_id.student_status_id is null
                   or lower(r.student_id.student_status_id.name) <> 'deleted')
            group by r.classroom_id.id
            """)
    List<CountByKey> countActiveByClassroom(Integer academicYearId);
}
