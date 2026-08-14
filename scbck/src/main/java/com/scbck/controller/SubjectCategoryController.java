package com.scbck.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.SubjectCategory;
import com.scbck.repository.SubjectCategoryDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * CRUD for the subject category bands.
 *
 * Shares the Subject module's privileges rather than introducing one of its
 * own: a user who may edit the curriculum may edit how it is grouped, and a
 * separate privilege would only produce the state where someone can add a
 * subject but not the band to file it under.
 */
@RestController
@RequestMapping("/api/subject-categories")
public class SubjectCategoryController {

    private final SubjectCategoryDao categoryDao;
    private final PrivilegeService privilegeService;

    public SubjectCategoryController(SubjectCategoryDao categoryDao, PrivilegeService privilegeService) {
        this.categoryDao = categoryDao;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<SubjectCategory> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_SUBJECT);
        return categoryDao.listOrdered();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<SubjectCategory> create(@Valid @RequestBody SubjectCategory category) {
        privilegeService.requireInsert(PrivilegeService.MODULE_SUBJECT);

        assertNameIsFree(category.getName(), null);

        category.setId(null);
        category.setName(category.getName().trim());
        if (category.getSortOrder() == null) {
            // New bands go to the end rather than to the front, so adding one
            // cannot silently reorder the columns of an existing mark sheet.
            category.setSortOrder(nextSortOrder());
        }
        if (category.getActive() == null) {
            category.setActive(Boolean.TRUE);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(categoryDao.save(category));
    }

    @PutMapping("/{id}")
    @Transactional
    public SubjectCategory update(@PathVariable Integer id, @Valid @RequestBody SubjectCategory category) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_SUBJECT);

        SubjectCategory existing = require(id);
        assertNameIsFree(category.getName(), id);

        existing.setName(category.getName().trim());
        existing.setSortOrder(category.getSortOrder() == null ? existing.getSortOrder() : category.getSortOrder());
        existing.setExpectedSubjects(category.getExpectedSubjects());
        existing.setActive(category.getActive() == null ? Boolean.TRUE : category.getActive());

        return categoryDao.save(existing);
    }

    /**
     * Deletes only while no subject is filed under the category.
     *
     * The alternative - cascading to null - would quietly change what every
     * historical mark sheet groups by, so an in-use category has to be retired
     * with {@code active = false} instead.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_SUBJECT);

        SubjectCategory existing = require(id);

        long subjects = categoryDao.countSubjects(id);
        if (subjects > 0) {
            throw ApiException.conflict(existing.getName() + " groups " + subjects
                    + " subject(s). Move them to another category first, or mark it inactive to retire it.");
        }

        categoryDao.delete(existing);
        return MessageResponse.of(existing.getName() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private SubjectCategory require(Integer id) {
        return categoryDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subject category " + id + " does not exist."));
    }

    private void assertNameIsFree(String name, Integer selfId) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("A category name is required.");
        }

        categoryDao.findByName(name.trim()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), selfId)) {
                throw ApiException.conflict("A category named " + name.trim() + " already exists.");
            }
        });
    }

    private int nextSortOrder() {
        return categoryDao.listOrdered().stream()
                .map(SubjectCategory::getSortOrder)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1) + 1;
    }
}
