package com.scbck.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.CurriculumAlignment;
import com.scbck.model.AcademicYear;
import com.scbck.repository.AcademicYearDao;
import com.scbck.service.CurriculumAlignmentService;

/**
 * Corrects class timetables that do not match their grade's curriculum, where
 * doing so destroys nothing.
 *
 * The curriculum has said what each grade is taught since it was introduced,
 * but classes created before that carried whatever the sample data gave them -
 * grade 1 classes holding Combined Maths and Chemistry, which then appeared as
 * columns on the grade 1 mark sheet, because the sheet's columns come from the
 * timetable.
 *
 * The correction was available as a reviewed, confirmed action on the Classes
 * screen. The school reported the same problem three times without finding it,
 * which is a fair verdict on hiding a data repair behind a button on a page
 * nobody visits. So the safe half now happens on start-up.
 *
 * "Safe" is doing real work here: only classes whose corrections would delete
 * no marks are touched. A class where somebody has genuinely been entering
 * marks against a subject the grade does not take is a judgement, not a repair,
 * and is left for the dialog - which reports exactly how many marks are at
 * stake and requires them to be confirmed.
 *
 * Idempotent by construction: once a timetable matches the curriculum there is
 * nothing to change, so a second start-up does nothing at all.
 */
@Component
@Order(50)
public class CurriculumAlignmentBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CurriculumAlignmentBootstrap.class);

    private final AcademicYearDao academicYearDao;
    private final CurriculumAlignmentService alignmentService;

    public CurriculumAlignmentBootstrap(AcademicYearDao academicYearDao,
            CurriculumAlignmentService alignmentService) {
        this.academicYearDao = academicYearDao;
        this.alignmentService = alignmentService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AcademicYear> current = academicYearDao.listCurrent();
        if (current.isEmpty()) {
            return;
        }

        AcademicYear year = current.get(0);
        CurriculumAlignment result = alignmentService.alignSafeClasses(year);

        if (result.classesChanged() > 0) {
            log.info("Curriculum: corrected {} class timetable(s) for {} - {} subject(s) added, "
                    + "{} removed.", result.classesChanged(), year.getName(),
                    result.subjectsAdded(), result.subjectsRemoved());
        }

        long stillNeedingAttention = result.changes().stream()
                .filter(change -> change.marksAffected() > 0)
                .count();

        if (stillNeedingAttention > 0) {
            log.warn("Curriculum: {} class timetable(s) still do not match their curriculum and "
                    + "were left alone, because correcting them would delete {} recorded mark(s). "
                    + "Review them with \"Align to curriculum\" on the Classes screen.",
                    stillNeedingAttention, result.marksAffected());
        }
    }
}
