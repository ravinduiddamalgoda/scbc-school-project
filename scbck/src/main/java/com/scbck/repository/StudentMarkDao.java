package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentMark;

public interface StudentMarkDao extends JpaRepository<StudentMark, Integer> {

    /** How many marks stand against the given timetable lines. */
    @Query("""
            select count(m) from StudentMark m
            where m.student_subject_id.classroom_subject_id.id in ?1
            """)
    long countForClassroomSubjects(List<Integer> classroomSubjectIds);

    /**
     * Clears the marks recorded against the given timetable lines.
     *
     * Taking a subject off a class deletes the enrolment lines for it, and
     * these rows point at those lines - so without this the delete hits a
     * foreign key and the whole save comes back as a conflict. That is what the
     * school reported, twice, as "the time table button not working": the
     * drawer opened, the subject was unticked, and saving failed with a message
     * about duplicate values that had nothing to do with the cause.
     *
     * Deliberately explicit rather than a database-level cascade. Marks are the
     * one thing in this system nobody expects to lose as a side effect, so the
     * deletion happens somewhere it can be counted first and reported back.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from StudentMark m
            where m.student_subject_id.id in (
                select ss.id from StudentSubject ss
                where ss.classroom_subject_id.id in ?1)
            """)
    void deleteByClassroomSubjectIds(List<Integer> classroomSubjectIds);

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
