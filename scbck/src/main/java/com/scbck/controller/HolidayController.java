package com.scbck.controller;

import java.time.LocalDate;
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
import com.scbck.model.Holiday;
import com.scbck.model.Term;
import com.scbck.repository.AttendanceDao;
import com.scbck.repository.HolidayDao;
import com.scbck.repository.TermDao;
import com.scbck.service.AcademicYearService;
import com.scbck.service.PrivilegeService;

/**
 * The days school is not conducted.
 *
 * Shares the Class module's privileges with terms and grade heads: they are the
 * same job, done once at the start of a year, by the same person.
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayDao holidayDao;
    private final TermDao termDao;
    private final AttendanceDao attendanceDao;
    private final AcademicYearService academicYearService;
    private final PrivilegeService privilegeService;

    public HolidayController(HolidayDao holidayDao, TermDao termDao, AttendanceDao attendanceDao,
            AcademicYearService academicYearService, PrivilegeService privilegeService) {
        this.holidayDao = holidayDao;
        this.termDao = termDao;
        this.attendanceDao = attendanceDao;
        this.academicYearService = academicYearService;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<Holiday> findAll(@RequestParam(required = false) Integer academicYearId) {
        // Read by the marks and attendance screens, whose users are not
        // necessarily Class administrators.
        privilegeService.requireAcademicReferenceAccess();
        return holidayDao.listByAcademicYear(academicYearService.resolve(academicYearId).getId());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Holiday> create(@RequestBody Holiday holiday,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireInsert(PrivilegeService.MODULE_CLASS);

        AcademicYear year = academicYearService.resolve(academicYearId);
        apply(holiday, year, null);

        return ResponseEntity.status(HttpStatus.CREATED).body(holidayDao.save(holiday));
    }

    @PutMapping("/{id}")
    @Transactional
    public Holiday update(@PathVariable Integer id, @RequestBody Holiday holiday,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_CLASS);

        Holiday existing = require(id);
        AcademicYear year = existing.getAcademic_year_id() == null
                ? academicYearService.resolve(academicYearId)
                : existing.getAcademic_year_id();

        existing.setDate(holiday.getDate());
        existing.setName(holiday.getName());
        existing.setCategory(holiday.getCategory());
        existing.setNote(holiday.getNote());
        apply(existing, year, id);

        return holidayDao.save(existing);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_CLASS);

        Holiday existing = require(id);
        holidayDao.delete(existing);

        return MessageResponse.of(existing.getName() + " removed. That date counts as a school day again.");
    }

    // -------------------------------------------------------------------------

    private Holiday require(Integer id) {
        return holidayDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Holiday " + id + " does not exist."));
    }

    /**
     * Validates a holiday against the year it belongs to.
     *
     * The register check is the important one. Attendance already taken on a
     * date means school was conducted, and quietly declaring it a holiday
     * afterwards would leave a register that no report counts - the marks would
     * still be there, invisible.
     */
    private void apply(Holiday holiday, AcademicYear year, Integer selfId) {
        // Checked here rather than with @Valid on the parameter: the academic
        // year is assigned by the server, so bean validation on the entity
        // would reject every request before this method could set it.
        if (holiday.getDate() == null) {
            throw ApiException.badRequest("A holiday needs a date.");
        }
        if (holiday.getName() == null || holiday.getName().isBlank()) {
            throw ApiException.badRequest("A holiday needs a name.");
        }
        holiday.setName(holiday.getName().trim());
        holiday.setAcademic_year_id(year);

        LocalDate date = holiday.getDate();

        boolean clash = holidayDao.listByAcademicYear(year.getId()).stream()
                .anyMatch(other -> other.getDate().equals(date) && !Objects.equals(other.getId(), selfId));
        if (clash) {
            throw ApiException.conflict(date + " is already recorded as a holiday.");
        }

        long registers = attendanceDao.countDaysAcrossClasses(year.getId(), date);
        if (registers > 0) {
            throw ApiException.conflict("Attendance has already been marked for " + date
                    + " in " + registers + " class(es). Remove those registers first, or leave the day"
                    + " as a school day.");
        }

        // A date outside every term is not an error - schools close over the
        // holidays too - but one outside the year is almost certainly a typo.
        warnIfOutsideYear(holiday, year);
    }

    private void warnIfOutsideYear(Holiday holiday, AcademicYear year) {
        List<Term> terms = termDao.listByAcademicYear(year.getId());
        if (terms.isEmpty()) {
            return;
        }

        LocalDate earliest = terms.stream().map(Term::getStart_date).min(LocalDate::compareTo).orElse(null);
        LocalDate latest = terms.stream().map(Term::getEnd_date).max(LocalDate::compareTo).orElse(null);

        if (earliest != null && latest != null
                && (holiday.getDate().isBefore(earliest) || holiday.getDate().isAfter(latest))) {
            throw ApiException.badRequest(holiday.getDate()
                    + " falls outside " + year.getName() + "'s terms (" + earliest + " to " + latest
                    + "). Check the date, or extend the term dates first.");
        }
    }
}
