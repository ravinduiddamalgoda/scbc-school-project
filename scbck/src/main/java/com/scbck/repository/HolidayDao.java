package com.scbck.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Holiday;

public interface HolidayDao extends JpaRepository<Holiday, Integer> {

    @Query("""
            select h from Holiday h
            where h.academic_year_id.id = ?1
            order by h.date
            """)
    List<Holiday> listByAcademicYear(Integer academicYearId);

    /**
     * The holiday on a date, if any.
     *
     * Matched by date alone rather than by year as well: a register is marked
     * against a class, and the class already fixes the year, so asking "is this
     * date a holiday" must not depend on the caller passing the right one.
     */
    @Query("select h from Holiday h where h.date = ?1")
    List<Holiday> findByDate(LocalDate date);

    @Query("""
            select h from Holiday h
            where h.academic_year_id.id = ?1 and h.date between ?2 and ?3
            order by h.date
            """)
    List<Holiday> listBetween(Integer academicYearId, LocalDate from, LocalDate to);
}
