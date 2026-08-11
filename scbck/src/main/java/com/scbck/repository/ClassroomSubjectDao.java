package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.ClassroomSubject;
import com.scbck.repository.projection.CountByKey;

public interface ClassroomSubjectDao extends JpaRepository<ClassroomSubject, Integer> {

    @Query("""
            select cs from ClassroomSubject cs
            where cs.classroom_id.id = ?1
            order by cs.subject_detail_id.name
            """)
    List<ClassroomSubject> listByClassroom(Integer classroomId);

    /**
     * Every subject assignment in one academic year, ordered so the report can
     * walk it in a single pass.
     */
    @Query("""
            select cs from ClassroomSubject cs
            where cs.classroom_id.academic_year_id.id = ?1
            order by cs.classroom_id.grade_id.id, cs.classroom_id.name, cs.subject_detail_id.name
            """)
    List<ClassroomSubject> listByAcademicYear(Integer academicYearId);

    @Query("select count(ss) from StudentSubject ss where ss.classroom_subject_id.id = ?1")
    long countStudents(Integer classroomSubjectId);

    /**
     * Timetable size per class, aggregated in one query so a class listing does
     * not cost one extra round trip per row.
     */
    @Query("""
            select cs.classroom_id.id as keyId, count(cs.id) as total
            from ClassroomSubject cs
            where cs.classroom_id.academic_year_id.id = ?1
            group by cs.classroom_id.id
            """)
    List<CountByKey> countByClassroom(Integer academicYearId);
}
