package com.scbck.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.AcademicYear;
import com.scbck.model.FeeStructure;
import com.scbck.model.Grade;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.FeeStructureDao;
import com.scbck.repository.GradeDao;

/**
 * Sets each grade's fee for the current year, so the payment form has a figure
 * to offer instead of an empty box.
 *
 * The school's figures are 195,000 for every grade from 1 to 11, and 25,000
 * covering grades 12 and 13 together. That last part is why grade 13 is seeded
 * at zero rather than left out: a missing row means "the school has not set a
 * fee", which makes the form ask the clerk to type one, whereas a zero says
 * "nothing further is due this year", which is what the school means.
 *
 * Seeded per year and only when that year has no fees at all, so raising the
 * fee for next year is done once on the Academic setup screen and is never
 * undone by a restart.
 */
@Component
@Order(40)
public class FeeStructureBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeeStructureBootstrap.class);

    private static final BigDecimal PRIMARY_TO_OL = new BigDecimal("195000.00");
    private static final BigDecimal AL = new BigDecimal("25000.00");

    private final AcademicYearDao academicYearDao;
    private final GradeDao gradeDao;
    private final FeeStructureDao feeStructureDao;

    public FeeStructureBootstrap(AcademicYearDao academicYearDao, GradeDao gradeDao,
            FeeStructureDao feeStructureDao) {
        this.academicYearDao = academicYearDao;
        this.gradeDao = gradeDao;
        this.feeStructureDao = feeStructureDao;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AcademicYear> current = academicYearDao.listCurrent();
        if (current.isEmpty()) {
            return;
        }

        AcademicYear year = current.get(0);
        if (feeStructureDao.countForYear(year.getId()) > 0) {
            return;
        }

        List<FeeStructure> rows = new ArrayList<>();
        for (Grade grade : gradeDao.findAll()) {
            Integer number = numberOf(grade.getName());
            if (number == null) {
                continue;
            }

            FeeStructure row = new FeeStructure();
            row.setAcademicYear(year);
            row.setGrade(grade);

            if (number <= 11) {
                row.setAnnualFee(PRIMARY_TO_OL);
                row.setNote("Annual fee for " + grade.getName() + ".");
            } else if (number == 12) {
                row.setAnnualFee(AL);
                row.setNote("Covers grades 12 and 13 together.");
            } else {
                row.setAnnualFee(BigDecimal.ZERO);
                row.setNote("Already covered by the grade 12 fee.");
            }

            rows.add(row);
        }

        if (!rows.isEmpty()) {
            feeStructureDao.saveAll(rows);
            log.info("Fees: set {} grade fee(s) for {}.", rows.size(), year.getName());
        }
    }

    /** "Grade 10" -> 10. Null for a grade named anything else. */
    private static Integer numberOf(String gradeName) {
        if (gradeName == null) {
            return null;
        }
        String digits = gradeName.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
