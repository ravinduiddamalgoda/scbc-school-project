package com.scbck.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.FeeStructure;
import com.scbck.model.Grade;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.FeeStructureDao;
import com.scbck.repository.GradeDao;
import com.scbck.service.PrivilegeService;

/**
 * The fee each grade is charged for a year.
 *
 * Gated on the Payment module: the fee is what every receipt is measured
 * against, so whoever may record money coming in is the one who needs to say
 * how much was owed.
 */
@RestController
@RequestMapping("/api/fee-structures")
public class FeeStructureController {

    private final FeeStructureDao feeStructureDao;
    private final AcademicYearDao academicYearDao;
    private final GradeDao gradeDao;
    private final PrivilegeService privilegeService;

    public FeeStructureController(FeeStructureDao feeStructureDao, AcademicYearDao academicYearDao,
            GradeDao gradeDao, PrivilegeService privilegeService) {
        this.feeStructureDao = feeStructureDao;
        this.academicYearDao = academicYearDao;
        this.gradeDao = gradeDao;
        this.privilegeService = privilegeService;
    }

    /**
     * The fee table for a year.
     *
     * Every grade appears, whether or not a fee has been set for it, so the
     * screen shows the gaps rather than hiding them - an unpriced grade is
     * exactly what the office needs to see.
     */
    @GetMapping
    public List<FeeStructure> list(@RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_PAYMENT);

        AcademicYear year = resolveYear(academicYearId);
        if (year == null) {
            return List.of();
        }

        List<FeeStructure> existing = feeStructureDao.listForYear(year.getId());

        return gradeDao.findAll().stream()
                .sorted((left, right) -> Integer.compare(left.getId(), right.getId()))
                .map(grade -> existing.stream()
                        .filter(row -> row.getGrade().getId().equals(grade.getId()))
                        .findFirst()
                        .orElseGet(() -> unpriced(year, grade)))
                .toList();
    }

    /**
     * Sets the fees for a year, one row per grade.
     *
     * Whole-table rather than row-by-row because that is how a fee revision is
     * decided - the school agrees next year's fees as a schedule, not one grade
     * at a time.
     */
    @PutMapping
    @Transactional
    public List<FeeStructure> save(@RequestParam(required = false) Integer academicYearId,
            @RequestBody List<FeeStructure> rows) {

        privilegeService.requireUpdate(PrivilegeService.MODULE_PAYMENT);

        AcademicYear year = resolveYear(academicYearId);
        if (year == null) {
            throw ApiException.badRequest("No academic year is set as current.");
        }

        for (FeeStructure incoming : rows == null ? List.<FeeStructure>of() : rows) {
            if (incoming.getGrade() == null || incoming.getGrade().getId() == null) {
                continue;
            }
            Integer gradeId = incoming.getGrade().getId();

            Grade grade = gradeDao.findById(gradeId)
                    .orElseThrow(() -> ApiException.badRequest("Grade " + gradeId + " does not exist."));

            BigDecimal fee = incoming.getAnnualFee();
            if (fee == null) {
                // Clearing a fee means "the school has not set one", which the
                // payment form treats as "type it" rather than as zero.
                feeStructureDao.find(year.getId(), gradeId).ifPresent(feeStructureDao::delete);
                continue;
            }
            if (fee.signum() < 0) {
                throw ApiException.badRequest("A fee cannot be negative.");
            }

            FeeStructure row = feeStructureDao.find(year.getId(), gradeId)
                    .orElseGet(FeeStructure::new);
            row.setAcademicYear(year);
            row.setGrade(grade);
            row.setAnnualFee(fee);
            row.setNote(incoming.getNote());
            feeStructureDao.save(row);
        }

        return list(year.getId());
    }

    // -------------------------------------------------------------------------

    /** A placeholder row for a grade with no fee set, never persisted. */
    private FeeStructure unpriced(AcademicYear year, Grade grade) {
        FeeStructure row = new FeeStructure();
        row.setAcademicYear(year);
        row.setGrade(grade);
        return row;
    }

    private AcademicYear resolveYear(Integer academicYearId) {
        if (academicYearId != null) {
            return academicYearDao.findById(academicYearId)
                    .orElseThrow(() -> ApiException
                            .notFound("Academic year " + academicYearId + " does not exist."));
        }
        List<AcademicYear> current = academicYearDao.listCurrent();
        return current.isEmpty() ? null : current.get(0);
    }
}
