package com.scbck.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.SbaMark;

public interface SbaMarkDao extends JpaRepository<SbaMark, Integer> {

    /** Every mark behind one assessment sheet, across both of its grades. */
    @Query("""
            select m from SbaMark m
            where m.exam = ?1 and m.examYear = ?2 and m.subject.id = ?3
            order by m.student.stu_no, m.gradeNumber, m.termNumber
            """)
    List<SbaMark> listForSheet(String exam, Integer examYear, Integer subjectId);

    /** The marks an entry grid edits: one subject, one grade, one term. */
    @Query("""
            select m from SbaMark m
            where m.exam = ?1 and m.examYear = ?2 and m.subject.id = ?3
              and m.gradeNumber = ?4 and m.termNumber = ?5
            """)
    List<SbaMark> listForEntry(String exam, Integer examYear, Integer subjectId,
            Integer gradeNumber, Integer termNumber);

    @Query("""
            select m from SbaMark m
            where m.exam = ?1 and m.examYear = ?2 and m.subject.id = ?3
              and m.gradeNumber = ?4 and m.termNumber = ?5 and m.student.id = ?6
            """)
    Optional<SbaMark> find(String exam, Integer examYear, Integer subjectId,
            Integer gradeNumber, Integer termNumber, Integer studentId);

    /** Subjects that already have marks, so the screen can list what exists. */
    @Query("""
            select distinct m.subject.id from SbaMark m
            where m.exam = ?1 and m.examYear = ?2
            """)
    List<Integer> subjectIdsWithMarks(String exam, Integer examYear);
}
