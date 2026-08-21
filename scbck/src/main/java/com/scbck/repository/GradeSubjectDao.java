package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.GradeSubject;

public interface GradeSubjectDao extends JpaRepository<GradeSubject, Integer> {

    /**
     * One grade's curriculum, in the order it prints.
     *
     * Core first, then the numbered baskets, then the general subjects - the
     * basket names sort into that order on their own, which is why they are
     * "Cat 1" and not "Category I".
     */
    @Query("""
            select gs from GradeSubject gs
            where gs.grade.id = ?1
            order by gs.basket, coalesce(gs.sortOrder, 9999), gs.subject.name
            """)
    List<GradeSubject> listForGrade(Integer gradeId);

    @Query("""
            select gs from GradeSubject gs
            order by gs.grade.id, gs.basket, coalesce(gs.sortOrder, 9999), gs.subject.name
            """)
    List<GradeSubject> listAll();

    /** Subject ids on a grade's curriculum - what the timetable pre-ticks. */
    @Query("select gs.subject.id from GradeSubject gs where gs.grade.id = ?1")
    List<Integer> subjectIdsForGrade(Integer gradeId);

    @Query("select count(gs) from GradeSubject gs where gs.subject.id = ?1")
    long countForSubject(Integer subjectId);
}
