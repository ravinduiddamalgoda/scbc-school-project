package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Term;

public interface TermDao extends JpaRepository<Term, Integer> {

    /** In calendar order, which is the order the report prints its columns. */
    @Query("""
            select t from Term t
            where t.academic_year_id.id = ?1
            order by t.start_date, t.id
            """)
    List<Term> listByAcademicYear(Integer academicYearId);

    @Query("""
            select t from Term t
            where t.academic_year_id.id = ?1 and lower(t.name) = lower(?2)
            """)
    Term getByYearAndName(Integer academicYearId, String name);
}
