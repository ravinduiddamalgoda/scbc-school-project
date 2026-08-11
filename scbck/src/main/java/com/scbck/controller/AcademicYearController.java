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
import com.scbck.model.AcademicYear;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Academic year CRUD.
 *
 * Every class, timetable line and enrolment hangs off a year, and every report
 * is scoped to one, so this is the first thing that has to exist in a fresh
 * database. It is gated on the Class module: whoever may organise classes may
 * open the year they sit in.
 */
@RestController
@RequestMapping("/api/academic-years")
public class AcademicYearController {

    private final AcademicYearDao academicYearDao;
    private final ClassroomDao classroomDao;
    private final PrivilegeService privilegeService;

    public AcademicYearController(AcademicYearDao academicYearDao, ClassroomDao classroomDao,
            PrivilegeService privilegeService) {
        this.academicYearDao = academicYearDao;
        this.classroomDao = classroomDao;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<AcademicYear> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);
        return academicYearDao.findAll(Sort.by(Sort.Direction.DESC, "name"));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AcademicYear> create(@Valid @RequestBody AcademicYear year) {
        privilegeService.requireInsert(PrivilegeService.MODULE_CLASS);

        assertNameIsFree(year.getName(), null);

        year.setId(null);
        year.setName(year.getName().trim());
        if (Boolean.TRUE.equals(year.getCurrent_year())) {
            academicYearDao.clearCurrentFlag();
        } else {
            year.setCurrent_year(Boolean.FALSE);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(academicYearDao.save(year));
    }

    @PutMapping("/{id}")
    @Transactional
    public AcademicYear update(@PathVariable Integer id, @Valid @RequestBody AcademicYear year) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_CLASS);

        AcademicYear existing = academicYearDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Academic year " + id + " does not exist."));

        assertNameIsFree(year.getName(), id);

        // Only one year is ever current, so promoting this one demotes the rest
        // before the new value is written.
        if (Boolean.TRUE.equals(year.getCurrent_year())) {
            academicYearDao.clearCurrentFlag();
        }

        existing.setName(year.getName().trim());
        existing.setStart_date(year.getStart_date());
        existing.setEnd_date(year.getEnd_date());
        existing.setCurrent_year(Boolean.TRUE.equals(year.getCurrent_year()));

        return academicYearDao.save(existing);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_CLASS);

        AcademicYear existing = academicYearDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Academic year " + id + " does not exist."));

        int classes = classroomDao.listByAcademicYear(id).size();
        if (classes > 0) {
            throw ApiException.conflict(existing.getName() + " still holds " + classes
                    + " class(es). Delete those first - removing the year would orphan their reports.");
        }

        academicYearDao.delete(existing);
        return MessageResponse.of("Academic year " + existing.getName() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void assertNameIsFree(String name, Integer selfId) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("An academic year name is required.");
        }

        AcademicYear existing = academicYearDao.getByName(name.trim());
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw ApiException.conflict("The academic year " + name.trim() + " already exists.");
        }
    }
}
