package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.DistributionItem;

public interface DistributionItemDao extends JpaRepository<DistributionItem, Integer> {

    /**
     * The columns for one kind of sheet: the items for this grade plus the ones
     * that apply to every grade.
     *
     * Textbooks are per grade; uniform sizes are not, so an item with no grade
     * is offered to all of them rather than to none.
     */
    @Query("""
            select i from DistributionItem i
            where i.kind = ?1
              and (i.gradeId is null or i.gradeId = ?2)
              and i.active = true
            order by coalesce(i.sortOrder, 0), i.name
            """)
    List<DistributionItem> listForSheet(String kind, Integer gradeId);

    @Query("select i from DistributionItem i order by i.kind, coalesce(i.sortOrder, 0), i.name")
    List<DistributionItem> listAll();
}
