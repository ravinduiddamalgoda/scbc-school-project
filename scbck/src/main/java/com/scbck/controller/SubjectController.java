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
import com.scbck.model.SubjectDetail;
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
    private final PrivilegeService privilegeService;

    public SubjectController(SubjectDetailDao subjectDao, PrivilegeService privilegeService) {
        this.subjectDao = subjectDao;
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
        existing.setCategory(blankToNull(subject.getCategory()));
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
}
