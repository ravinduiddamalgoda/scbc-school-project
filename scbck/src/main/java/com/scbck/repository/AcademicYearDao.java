package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.AcademicYear;

public interface AcademicYearDao extends JpaRepository<AcademicYear, Integer> {

    @Query("select y from AcademicYear y where y.name = ?1")
    AcademicYear getByName(String name);

    /**
     * The year reports default to. A list is returned rather than a single row
     * so a database that somehow holds two flagged years yields the newest one
     * instead of throwing.
     */
    @Query("select y from AcademicYear y where y.current_year = true order by y.id desc")
    List<AcademicYear> listCurrent();

    /** Clears the flag everywhere before a new current year is set. */
    @Modifying
    @Query("update AcademicYear y set y.current_year = false where y.current_year = true")
    void clearCurrentFlag();
}
