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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Term;
import com.scbck.repository.TermDao;
import com.scbck.service.AcademicYearService;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Term CRUD.
 *
 * The Week Attendance report is a per-term breakdown, and a term is only a
 * pair of dates - but they have to be recorded somewhere, because "days
 * conducted in the first term" is otherwise unanswerable.
 *
 * Gated on the Class module: setting out the year's shape is the same job as
 * setting out its classes.
 */
@RestController
@RequestMapping("/api/terms")
public class TermController {

    private final TermDao termDao;
    private final AcademicYearService academicYearService;
    private final PrivilegeService privilegeService;

    public TermController(TermDao termDao, AcademicYearService academicYearService,
            PrivilegeService privilegeService) {
        this.termDao = termDao;
        this.academicYearService = academicYearService;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<Term> findAll(@RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);
        return termDao.listByAcademicYear(academicYearService.resolve(academicYearId).getId());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Term> create(@Valid @RequestBody Term term,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireInsert(PrivilegeService.MODULE_CLASS);

        term.setId(null);
        apply(term, academicYearId, null);

        return ResponseEntity.status(HttpStatus.CREATED).body(termDao.save(term));
    }

    @PutMapping("/{id}")
    @Transactional
    public Term update(@PathVariable Integer id, @Valid @RequestBody Term term,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_CLASS);

        Term existing = termDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Term " + id + " does not exist."));

        existing.setName(term.getName());
        existing.setStart_date(term.getStart_date());
        existing.setEnd_date(term.getEnd_date());
        apply(existing, academicYearId, id);

        return termDao.save(existing);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_CLASS);

        Term existing = termDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Term " + id + " does not exist."));

        // Attendance rows are dated, not term-linked, so removing a term drops
        // a column from the report without destroying a single mark.
        termDao.delete(existing);
        return MessageResponse.of(existing.getName() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void apply(Term term, Integer academicYearId, Integer selfId) {
        AcademicYear year = term.getAcademic_year_id() != null && academicYearId == null
                ? term.getAcademic_year_id()
                : academicYearService.resolve(academicYearId);
        term.setAcademic_year_id(year);

        if (term.getName() == null || term.getName().isBlank()) {
            throw ApiException.badRequest("A term name is required.");
        }
        term.setName(term.getName().trim());

        if (term.getEnd_date().isBefore(term.getStart_date())) {
            throw ApiException.badRequest(term.getName() + " ends before it starts.");
        }

        Term sameName = termDao.getByYearAndName(year.getId(), term.getName());
        if (sameName != null && !Objects.equals(sameName.getId(), selfId)) {
            throw ApiException.conflict("A term called " + term.getName()
                    + " already exists in " + year.getName() + ".");
        }

        // Overlapping terms would count the same school day twice, so the
        // attendance percentages would exceed 100% with nothing to show why.
        for (Term other : termDao.listByAcademicYear(year.getId())) {
            if (Objects.equals(other.getId(), selfId)) {
                continue;
            }
            boolean overlaps = !term.getStart_date().isAfter(other.getEnd_date())
                    && !term.getEnd_date().isBefore(other.getStart_date());
            if (overlaps) {
                throw ApiException.conflict(term.getName() + " overlaps " + other.getName()
                        + " (" + other.getStart_date() + " to " + other.getEnd_date() + ").");
            }
        }
    }
}
