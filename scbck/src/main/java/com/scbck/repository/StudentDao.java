package com.scbck.repository;

import java.util.List;

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

    /**
     * Looks a student up by admission number.
     *
     * Zero-padded on insert, but the office types "3960" rather than
     * "00003960", so the comparison is on the numeric value: a clerk who has
     * the number from a paper file should not have to count leading zeroes.
     * The CAST is on the stored column only, so an admission number that is not
     * a number simply does not match rather than failing the query.
     */
    @Query(value = """
            SELECT * FROM student s
            WHERE s.stu_no = :admissionNo
               OR (s.stu_no REGEXP '^[0-9]+$' AND :admissionNo REGEXP '^[0-9]+$'
                   AND CAST(s.stu_no AS DECIMAL(18,0)) = CAST(:admissionNo AS DECIMAL(18,0)))
            LIMIT 1
            """, nativeQuery = true)
    Student getByAdmissionNo(String admissionNo);

    /**
     * Admission-number or name search for the payment and report screens.
     *
     * One query rather than two because the box is one box: the clerk types
     * either, and having to say which in advance is exactly the friction the
     * school asked to be rid of.
     */
    @Query("""
            select s from Student s
            where (lower(s.fullname) like lower(concat('%', ?1, '%'))
               or lower(s.callingname) like lower(concat('%', ?1, '%'))
               or s.stu_no like concat('%', ?1, '%'))
              and (s.student_status_id is null or lower(s.student_status_id.name) <> 'deleted')
            order by s.stu_no
            """)
    List<Student> search(String term);

    /**
     * The children registered under one guardian.
     *
     * This is the whole basis of the parent portal's access control: a parent
     * account is linked to a guardian, and may see exactly the students this
     * query returns for them - never a student id supplied by the caller.
     */
    @Query("""
            select s from Student s
            where s.guardian_id.id = ?1
              and (s.student_status_id is null or lower(s.student_status_id.name) <> 'deleted')
            order by s.dob
            """)
    List<Student> listByGuardian(Integer guardianId);
}
