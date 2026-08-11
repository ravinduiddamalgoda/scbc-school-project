package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.GradeHead;

public interface GradeHeadDao extends JpaRepository<GradeHead, Integer> {

    @Query("""
            select h from GradeHead h
            where h.academic_year_id.id = ?1
            order by h.grade_id.id
            """)
    List<GradeHead> listByAcademicYear(Integer academicYearId);

    @Query("""
            select h from GradeHead h
            where h.academic_year_id.id = ?1 and h.grade_id.id = ?2
            """)
    GradeHead getByYearAndGrade(Integer academicYearId, Integer gradeId);
}
