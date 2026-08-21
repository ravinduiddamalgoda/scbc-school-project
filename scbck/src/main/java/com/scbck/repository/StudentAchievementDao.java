package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentAchievement;

public interface StudentAchievementDao extends JpaRepository<StudentAchievement, Integer> {

    /**
     * One student's record, newest first within each kind.
     *
     * Newest first because that is the order a certificate reads best in - the
     * senior prefectship earned in the final year is the one that matters, and
     * burying it under a class monitor post from grade 4 makes the office
     * reorder the text by hand every time.
     */
    @Query("""
            select a from StudentAchievement a
            where a.student_id.id = ?1
            order by a.kind, coalesce(a.year, 0) desc, a.id desc
            """)
    List<StudentAchievement> listForStudent(Integer studentId);

    @Query("""
            select a from StudentAchievement a
            where a.student_id.id = ?1 and a.kind = ?2
            order by coalesce(a.year, 0) desc, a.id desc
            """)
    List<StudentAchievement> listForStudentAndKind(Integer studentId, String kind);

}
