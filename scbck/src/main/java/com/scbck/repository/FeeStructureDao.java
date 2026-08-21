package com.scbck.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.FeeStructure;

public interface FeeStructureDao extends JpaRepository<FeeStructure, Integer> {

    @Query("""
            select f from FeeStructure f
            where f.academicYear.id = ?1 and f.grade.id = ?2
            """)
    Optional<FeeStructure> find(Integer academicYearId, Integer gradeId);

    @Query("""
            select f from FeeStructure f
            where f.academicYear.id = ?1
            order by f.grade.id
            """)
    List<FeeStructure> listForYear(Integer academicYearId);

    @Query("select count(f) from FeeStructure f where f.academicYear.id = ?1")
    long countForYear(Integer academicYearId);
}
