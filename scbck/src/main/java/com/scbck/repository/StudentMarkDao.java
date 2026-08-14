package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentMark;

public interface StudentMarkDao extends JpaRepository<StudentMark, Integer> {

    /**
     * Every mark recorded for one class in one term.
     *
     * The whole sheet is loaded in a single query because it is computed as a
     * whole: rank needs every student's total, and the summary block needs
     * every mark in every column, so fetching per student would mean one query
     * per row for a page that always shows all of them.
     */
    @Query("""
            select m from StudentMark m
            where m.term_id.id = ?2
              and m.student_subject_id.classroom_subject_id.classroom_id.id = ?1
            """)
    List<StudentMark> listByClassroomAndTerm(Integer classroomId, Integer termId);

    /** The existing mark for one enrolment line in one term, if any. */
    @Query("""
            select m from StudentMark m
            where m.student_subject_id.id = ?1 and m.term_id.id = ?2
            """)
    StudentMark getByStudentSubjectAndTerm(Integer studentSubjectId, Integer termId);

    /** Guards deleting a term that marks already hang off. */
    @Query("select count(m) from StudentMark m where m.term_id.id = ?1")
    long countByTerm(Integer termId);
}
