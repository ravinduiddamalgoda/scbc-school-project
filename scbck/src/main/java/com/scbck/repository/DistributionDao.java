package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.StudentDistribution;

public interface DistributionDao extends JpaRepository<StudentDistribution, Integer> {

    /**
     * Everything issued to one class, in one pass.
     *
     * The sheet is a grid, so it is loaded as one - a query per student would
     * be one round trip per row of a page that always shows the whole class.
     */
    @Query("""
            select d from StudentDistribution d
            where d.student_registration_id.classroom_id.id = ?1
              and d.distribution_item_id.kind = ?2
            """)
    List<StudentDistribution> listByClassroomAndKind(Integer classroomId, String kind);

    @Query("""
            select d from StudentDistribution d
            where d.student_registration_id.id = ?1 and d.distribution_item_id.id = ?2
            """)
    StudentDistribution getByRegistrationAndItem(Integer registrationId, Integer itemId);

    /** Guards deleting an item that has already been handed out. */
    @Query("select count(d) from StudentDistribution d where d.distribution_item_id.id = ?1")
    long countByItem(Integer itemId);
}
