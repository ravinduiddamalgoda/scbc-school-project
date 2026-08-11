package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.SubjectDetail;

public interface SubjectDetailDao extends JpaRepository<SubjectDetail, Integer> {

    @Query("select s from SubjectDetail s where lower(s.name) = lower(?1)")
    SubjectDetail getByName(String name);

    @Query("select s from SubjectDetail s where s.active = true order by s.name")
    List<SubjectDetail> listActive();

    /** Guards the delete: a subject already on a timetable cannot be removed. */
    @Query("select count(cs) from ClassroomSubject cs where cs.subject_detail_id.id = ?1")
    long countAssignments(Integer subjectId);
}
