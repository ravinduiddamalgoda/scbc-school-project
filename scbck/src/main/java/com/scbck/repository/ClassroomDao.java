package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Classroom;

public interface ClassroomDao extends JpaRepository<Classroom, Integer> {

    /**
     * Every class in one academic year, ordered the way the reports print them.
     * Grades are ordered by id because the seeded rows are inserted in grade
     * order; the numeric level parsed out of the name decides the report band.
     */
    @Query("""
            select c from Classroom c
            where c.academic_year_id.id = ?1
            order by c.grade_id.id, c.name
            """)
    List<Classroom> listByAcademicYear(Integer academicYearId);

    @Query("""
            select c from Classroom c
            where c.academic_year_id.id = ?1 and c.grade_id.id = ?2 and lower(c.name) = lower(?3)
            """)
    Classroom getByYearGradeAndName(Integer academicYearId, Integer gradeId, String name);

    /** Guards the delete: a class with students on its roll cannot be removed. */
    @Query("select count(r) from StudentRegistration r where r.classroom_id.id = ?1")
    long countEnrolments(Integer classroomId);
}
