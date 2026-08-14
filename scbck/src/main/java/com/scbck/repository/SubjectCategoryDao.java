package com.scbck.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.SubjectCategory;

public interface SubjectCategoryDao extends JpaRepository<SubjectCategory, Integer> {

    Optional<SubjectCategory> findByName(String name);

    /** Display order for every screen that lists categories. */
    @Query("select c from SubjectCategory c order by coalesce(c.sortOrder, 0), c.name")
    List<SubjectCategory> listOrdered();

    /** Subjects still pointing at a category, which blocks deleting it. */
    @Query("select count(s) from SubjectDetail s where s.category.id = ?1")
    long countSubjects(Integer categoryId);
}
