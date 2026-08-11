package com.scbck.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Student;

public interface StudentDao extends JpaRepository<Student, Integer> {

    /**
     * Next zero-padded admission number.
     *
     * This previously read MAX(emp_no) from the employee table, so student
     * numbers were derived from staff numbers. It now reads the student table,
     * and COALESCE keeps the first insert working on an empty table.
     */
    @Query(value = """
            SELECT LPAD(COALESCE(MAX(CAST(s.stu_no AS UNSIGNED)), 0) + 1, 8, '0')
            FROM student s
            """, nativeQuery = true)
    String getNextStuNo();

    @Query("select s from Student s where s.birth_certi_no = ?1")
    Student getByBirthCertiNo(String birthCertiNo);

    @Query("select s from Student s where s.nic = ?1")
    Student getByNic(String nic);
}
