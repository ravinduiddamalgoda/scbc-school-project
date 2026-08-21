package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.SubjectDetail;

public interface SubjectDetailDao extends JpaRepository<SubjectDetail, Integer> {

    @Query("select s from SubjectDetail s where lower(s.name) = lower(?1)")
    SubjectDetail getByName(String name);

    /**
     * Subjects still in use, in curriculum order.
     *
     * A null {@code active} counts as active. The column was added to a table
     * that already had rows, so Hibernate filled it with null rather than true
     * - and {@code active = true} silently excluded every subject an existing
     * database already held. The visible symptom was the Classes screen's
     * timetable button appearing to do nothing: the drawer opened onto "no
     * subjects to choose from" for a school with twenty-nine of them.
     *
     * Ordered by the curriculum sort order first so the pickers read Sinhala,
     * Buddhism, Mathematics rather than alphabetically; subjects with no order
     * set fall to the end, by name, exactly as before.
     */
    @Query("""
            select s from SubjectDetail s
            where s.active is null or s.active = true
            order by coalesce(s.sortOrder, 9999), s.name
            """)
    List<SubjectDetail> listActive();

    /** Guards the delete: a subject already on a timetable cannot be removed. */
    @Query("select count(cs) from ClassroomSubject cs where cs.subject_detail_id.id = ?1")
    long countAssignments(Integer subjectId);
}
