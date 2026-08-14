package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.ExamRegistration;

public interface ExamRegistrationDao extends JpaRepository<ExamRegistration, Integer> {

    /**
     * Every entry for one examination in one year, loaded in a single query and
     * indexed by the caller - the export walks a whole grade, so a lookup per
     * candidate would be one round trip per row.
     */
    @Query("""
            select e from ExamRegistration e
            where e.exam = ?1 and e.academic_year_id.id = ?2
            """)
    List<ExamRegistration> listByExamAndYear(String exam, Integer academicYearId);

    @Query("""
            select e from ExamRegistration e
            where e.student_id.id = ?1 and e.exam = ?2 and e.academic_year_id.id = ?3
            """)
    ExamRegistration getByStudentExamAndYear(Integer studentId, String exam, Integer academicYearId);
}
