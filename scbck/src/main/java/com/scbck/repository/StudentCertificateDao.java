package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentCertificate;

public interface StudentCertificateDao extends JpaRepository<StudentCertificate, Integer> {

    /** Everything issued to one student, most recent first. */
    @Query("""
            select c from StudentCertificate c
            where c.student_id.id = ?1
            order by c.issued_date desc, c.id desc
            """)
    List<StudentCertificate> listByStudent(Integer studentId);

    /** The issue log, newest first, for the certificates screen. */
    @Query("select c from StudentCertificate c order by c.issued_date desc, c.id desc")
    List<StudentCertificate> listRecent();
}
