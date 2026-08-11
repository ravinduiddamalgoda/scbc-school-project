package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentSubject;
import com.scbck.repository.projection.CountByKey;

public interface StudentSubjectDao extends JpaRepository<StudentSubject, Integer> {

    @Query("""
            select ss from StudentSubject ss
            where ss.student_registration_id.id = ?1
            order by ss.classroom_subject_id.subject_detail_id.name
            """)
    List<StudentSubject> listByRegistration(Integer registrationId);

    /**
     * Bulk deletes bypass the persistence context, so both of these flush before
     * they run and clear after: without that, rows already loaded in this
     * transaction would linger and the re-insert that follows would collide with
     * the unique constraint on (registration, timetable line).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StudentSubject ss where ss.student_registration_id.id = ?1")
    void deleteByRegistration(Integer registrationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StudentSubject ss where ss.classroom_subject_id.id in ?1")
    void deleteByClassroomSubjectIds(List<Integer> classroomSubjectIds);

    /**
     * How many students take each subject, per class, for one academic year.
     *
     * Same activity filter as the class head count, so the two reports can never
     * disagree about who is on the roll.
     */
    @Query("""
            select ss.classroom_subject_id.id as keyId, count(ss.id) as total
            from StudentSubject ss
            where ss.classroom_subject_id.classroom_id.academic_year_id.id = ?1
              and (ss.student_registration_id.registration_status_id is null
                   or lower(ss.student_registration_id.registration_status_id.name) = 'active')
              and (ss.student_registration_id.student_id.student_status_id is null
                   or lower(ss.student_registration_id.student_id.student_status_id.name) <> 'deleted')
            group by ss.classroom_subject_id.id
            """)
    List<CountByKey> countActiveByClassroomSubject(Integer academicYearId);
}
