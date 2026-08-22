package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.CurriculumAlignment;
import com.scbck.dto.MarkSheet;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.Grade;
import com.scbck.model.GradeSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.model.Term;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.repository.TermDao;
import com.scbck.service.CurriculumAlignmentService;
import com.scbck.service.MarkSheetService;

/**
 * Cover for the grade 1 mark sheet the school sent back three times.
 *
 * Their screenshot showed a grade 1 class with eight subject columns - Art,
 * Dancing and Music among them - grouped under headings reading "6-9 Core" and
 * "6-9 Cat 1". Two separate faults produced that one picture:
 *
 * <ul>
 *   <li>the timetable held subjects the grade does not take, because the
 *       curriculum was never applied to classes that already existed; and
 *   <li>the column bands came from each subject's own classification rather
 *       than from the basket its grade puts it in, so even a corrected grade 1
 *       timetable would still have been headed "6-9 Core".
 * </ul>
 *
 * Both are asserted here, in that order, because fixing either alone leaves the
 * sheet looking wrong.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurriculumMarkSheetIntegrationTest {

    /** Its own year: the seed-script test is not transactional and keeps 2026. */
    private static final String FIXTURE_YEAR = "2096";

    @Autowired
    private GradeDao gradeDao;
    @Autowired
    private AcademicYearDao academicYearDao;
    @Autowired
    private TermDao termDao;
    @Autowired
    private ClassroomDao classroomDao;
    @Autowired
    private ClassroomSubjectDao classroomSubjectDao;
    @Autowired
    private SubjectDetailDao subjectDao;
    @Autowired
    private GradeSubjectDao gradeSubjectDao;
    @Autowired
    private CurriculumAlignmentService alignmentService;
    @Autowired
    private MarkSheetService markSheetService;

    private AcademicYear year;
    private Grade gradeOne;
    private Classroom classroom;
    private Term term;

    @BeforeEach
    void seed() {
        gradeOne = new Grade();
        gradeOne.setName("Grade 1");
        gradeDao.save(gradeOne);

        year = new AcademicYear();
        year.setName(FIXTURE_YEAR);
        year.setCurrent_year(false);
        year.setStart_date(LocalDate.of(2024, 1, 5));
        year.setEnd_date(LocalDate.of(2024, 12, 11));
        academicYearDao.save(year);

        term = new Term();
        term.setName("First Term");
        term.setStart_date(LocalDate.of(2024, 1, 5));
        term.setEnd_date(LocalDate.of(2024, 4, 3));
        term.setAcademic_year_id(year);
        termDao.save(term);

        classroom = new Classroom();
        classroom.setName("C");
        classroom.setGrade_id(gradeOne);
        classroom.setAcademic_year_id(year);
        classroom.setMedium("Sinhala");
        classroomDao.save(classroom);

        // The grade 1 curriculum, in the order the school reads it.
        int order = 1;
        for (String name : List.of("Sinhala", "Mathematics", "Environment Science", "Buddhism",
                "English")) {
            place(subject(name), GradeSubject.CORE, order++);
        }

        // The timetable as the school found it: the five above, minus Sinhala
        // and Environment Science, plus three subjects grade 1 does not take.
        line(subject("Buddhism"));
        line(subject("English"));
        line(subject("Mathematics"));
        line(subject("Art"));
        line(subject("Dancing"));
        line(subject("Music"));
    }

    @Test
    @DisplayName("Aligning a grade 1 class leaves it with exactly its five subjects")
    void alignmentGivesGradeOneItsOwnSubjects() {
        CurriculumAlignment result = alignmentService.align(year, classroom.getId(), false, false);

        assertThat(result.classesChanged()).isEqualTo(1);
        assertThat(result.marksAffected()).isZero();

        List<String> subjects = classroomSubjectDao.listByClassroom(classroom.getId()).stream()
                .map(line -> line.getSubject_detail_id().getName())
                .sorted()
                .toList();

        assertThat(subjects).containsExactly(
                "Buddhism", "English", "Environment Science", "Mathematics", "Sinhala");
    }

    @Test
    @DisplayName("A class with nothing at stake is corrected without being asked")
    void safeClassesAreAlignedAutomatically() {
        CurriculumAlignment result = alignmentService.alignSafeClasses(year);

        assertThat(result.classesChanged()).isEqualTo(1);
        assertThat(classroomSubjectDao.listByClassroom(classroom.getId())).hasSize(5);
    }

    @Test
    @DisplayName("The grade 1 sheet is banded and ordered by grade 1's curriculum")
    void markSheetBandsByTheGradesOwnCurriculum() {
        alignmentService.align(year, classroom.getId(), false, false);

        MarkSheet sheet = markSheetService.build(classroom.getId(), term.getId());

        // Curriculum order, not alphabetical: the school's own listing starts
        // with Sinhala, and the screenshot started with Buddhism.
        assertThat(sheet.subjects().stream().map(MarkSheet.Subject::name))
                .containsExactly("Sinhala", "Mathematics", "Environment Science", "Buddhism",
                        "English");

        // One band, named for the basket grade 1 puts these in - not "6-9 Core",
        // which is a fact about Sinhala rather than about this class.
        assertThat(sheet.categories()).hasSize(1);
        assertThat(sheet.categories().get(0).name()).isEqualTo(GradeSubject.CORE);
        assertThat(sheet.categories().get(0).span()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------

    private SubjectDetail subject(String name) {
        SubjectDetail existing = subjectDao.getByName(name);
        if (existing != null) {
            return existing;
        }
        SubjectDetail subject = new SubjectDetail();
        subject.setName(name);
        subject.setActive(Boolean.TRUE);
        return subjectDao.save(subject);
    }

    private void place(SubjectDetail subject, String basket, int order) {
        GradeSubject row = new GradeSubject();
        row.setGrade(gradeOne);
        row.setSubject(subject);
        row.setBasket(basket);
        row.setSortOrder(order);
        row.setClassTeacherTaught(Boolean.TRUE);
        gradeSubjectDao.save(row);
    }

    private void line(SubjectDetail subject) {
        ClassroomSubject link = new ClassroomSubject();
        link.setClassroom_id(classroom);
        link.setSubject_detail_id(subject);
        classroomSubjectDao.save(link);
    }
}
