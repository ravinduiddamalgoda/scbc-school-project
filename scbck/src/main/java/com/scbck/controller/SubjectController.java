package com.scbck.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
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
import com.scbck.model.SubjectDetail;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.SubjectCategoryDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Curriculum subject CRUD.
 *
 * The subject_detail table is in the ER model but nothing in the application
 * ever wrote to it, so both subject reports had no source data to read.
 */
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectDetailDao subjectDao;
    private final SubjectCategoryDao categoryDao;
    private final GradeSubjectDao gradeSubjectDao;
    private final PrivilegeService privilegeService;

    public SubjectController(SubjectDetailDao subjectDao, SubjectCategoryDao categoryDao,
            GradeSubjectDao gradeSubjectDao, PrivilegeService privilegeService) {
        this.subjectDao = subjectDao;
        this.categoryDao = categoryDao;
        this.gradeSubjectDao = gradeSubjectDao;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<SubjectDetail> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_SUBJECT);
        return subjectDao.findAll(Sort.by("name"));
    }

    @GetMapping("/{id}")
    public SubjectDetail findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_SUBJECT);
        return subjectDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subject " + id + " does not exist."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<SubjectDetail> create(@Valid @RequestBody SubjectDetail subject) {
        privilegeService.requireInsert(PrivilegeService.MODULE_SUBJECT);

        assertNameIsFree(subject.getName(), null);

        subject.setId(null);
        subject.setName(subject.getName().trim());
        subject.setCategory(resolveCategory(subject.getCategory()));
        if (subject.getActive() == null) {
            subject.setActive(Boolean.TRUE);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(subjectDao.save(subject));
    }

    @PutMapping("/{id}")
    @Transactional
    public SubjectDetail update(@PathVariable Integer id, @Valid @RequestBody SubjectDetail subject) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_SUBJECT);

        SubjectDetail existing = subjectDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subject " + id + " does not exist."));

        assertNameIsFree(subject.getName(), id);

        existing.setName(subject.getName().trim());
        existing.setCode(blankToNull(subject.getCode()));
        existing.setCategory(resolveCategory(subject.getCategory()));
        existing.setSortOrder(subject.getSortOrder());
        existing.setActive(subject.getActive() == null ? Boolean.TRUE : subject.getActive());

        return subjectDao.save(existing);
    }

    /**
     * Hard delete, but only while the subject is unused.
     *
     * Once it is on a timetable the row is what gives every historical report
     * its column heading, so deleting it would silently rewrite the past.
     * Retiring it with {@code active = false} is the way to take it out of use.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_SUBJECT);

        SubjectDetail existing = subjectDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subject " + id + " does not exist."));

        long assignments = subjectDao.countAssignments(id);
        if (assignments > 0) {
            throw ApiException.conflict(existing.getName() + " is on the timetable of " + assignments
                    + " class(es). Remove it from those classes first, or mark it inactive to retire it.");
        }

        // The curriculum points at subjects too, and deleting out from under it
        // leaves a grade holding a row for a subject that no longer exists -
        // which surfaces as a foreign key error rather than as an explanation.
        long onCurriculum = gradeSubjectDao.countForSubject(id);
        if (onCurriculum > 0) {
            throw ApiException.conflict(existing.getName() + " is on the curriculum of "
                    + onCurriculum + " grade(s). Take it off under Academic setup first, or mark it"
                    + " inactive to retire it.");
        }

        subjectDao.delete(existing);
        return MessageResponse.of(existing.getName() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void assertNameIsFree(String name, Integer selfId) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("A subject name is required.");
        }

        SubjectDetail existing = subjectDao.getByName(name.trim());
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw ApiException.conflict("A subject named " + name.trim() + " already exists.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Resolves the category the client referenced by id.
     *
     * The payload carries the whole nested object because that is what the
     * client received; only the id is trusted, so a stale or edited name in the
     * request body cannot rename a category as a side effect of saving a
     * subject.
     */
    private SubjectCategory resolveCategory(SubjectCategory submitted) {
        if (submitted == null || submitted.getId() == null) {
            return null;
        }
        return categoryDao.findById(submitted.getId())
                .orElseThrow(() -> ApiException
                        .badRequest("Subject category " + submitted.getId() + " does not exist."));
    }
}
