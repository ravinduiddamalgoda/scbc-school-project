package com.scbck.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.SbaCandidate;

public interface SbaCandidateDao extends JpaRepository<SbaCandidate, Integer> {

    @Query("""
            select c from SbaCandidate c
            where c.exam = ?1 and c.examYear = ?2 and c.subject.id = ?3
            """)
    List<SbaCandidate> listForSheet(String exam, Integer examYear, Integer subjectId);

    @Query("""
            select c from SbaCandidate c
            where c.exam = ?1 and c.examYear = ?2 and c.subject.id = ?3 and c.student.id = ?4
            """)
    Optional<SbaCandidate> find(String exam, Integer examYear, Integer subjectId, Integer studentId);
}
