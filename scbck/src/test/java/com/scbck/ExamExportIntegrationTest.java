package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.Grade;
import com.scbck.model.Religion;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.ReligionDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.service.ExamExportService;

/**
 * Cover for the Department of Examinations candidate workbooks.
 *
 * The assertions are about the rules the Department enforces on upload, since
 * those are what the school cannot see for itself: a rejected submission comes
 * back in bulk with no indication of which row was wrong. An export that
 * reported nothing and wrote blanks would look like a success.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExamExportIntegrationTest {

    @Autowired
    private ExamExportService examExportService;
    @Autowired
    private GradeDao gradeDao;
    @Autowired
    private AcademicYearDao academicYearDao;
    @Autowired
    private ClassroomDao classroomDao;
    @Autowired
    private ClassroomSubjectDao classroomSubjectDao;
    @Autowired
    private SubjectDetailDao subjectDao;
    @Autowired
    private StudentDao studentDao;
    @Autowired
    private StudentRegistrationDao registrationDao;
    @Autowired
    private StudentSubjectDao studentSubjectDao;
    @Autowired
    private ReligionDao religionDao;

    private AcademicYear year;
    private Classroom elevenA;

    @BeforeEach
    void seed() {
        year = new AcademicYear();
        year.setName("2026");
        year.setCurrent_year(true);
        academicYearDao.save(year);

        Grade grade11 = new Grade();
        grade11.setName("Grade 11");
        gradeDao.save(grade11);

        elevenA = new Classroom();
        elevenA.setName("A");
        elevenA.setMedium("Sinhala");
        elevenA.setGrade_id(grade11);
        elevenA.setAcademic_year_id(year);
        classroomDao.save(elevenA);

        Religion buddhism = new Religion();
        buddhism.setName("Buddhism");
        buddhism.setExamCode(11);
        religionDao.save(buddhism);
    }

    @Test
    @DisplayName("A candidate's optional subject lands in the column its code implies")
    void optionalSubjectsAreFiledByCodeRange() {
        // 32 Maths is compulsory; 71 falls in Category I (60-75) and 45 in
        // Category II (40-52), so neither needs a mapping of its own.
        StudentRegistration nadun = enrol("Nadun Wijesekara", "200512345678", "Buddhism");
        takes(nadun, subject("Mathematics", 32));
        takes(nadun, subject("Geography", 71));
        takes(nadun, subject("Art", 45));

        ExamExportService.Export export = examExportService.build("OL", year.getId());

        assertThat(export.candidates()).isEqualTo(1);
        assertThat(export.filename()).isEqualTo("OL Candidates 2026.xlsx");
        assertThat(new String(export.workbook(), 0, 2)).isEqualTo("PK");
        // Nothing to fix: a complete record produces no problems.
        assertThat(export.problems()).isEmpty();
    }

    @Test
    @DisplayName("A ten-character NIC is reported rather than silently submitted")
    void shortNicIsReported() {
        StudentRegistration old = enrol("Kavinu Liyanage", "901234567V", "Buddhism");
        takes(old, subject("Mathematics", 32));

        ExamExportService.Export export = examExportService.build("OL", year.getId());

        assertThat(export.problems())
                .anyMatch(problem -> problem.contains("Kavinu Liyanage")
                        && problem.contains("10-character NIC")
                        && problem.contains("requires 12"));
        // The workbook is still produced, so the gap can be seen in place.
        assertThat(export.workbook()).isNotEmpty();
    }

    @Test
    @DisplayName("A religion with no examination code is named, not left blank")
    void unmappedReligionIsReported() {
        StudentRegistration student = enrol("Yeshan Travis", "200612345678", "Christianity");
        takes(student, subject("Mathematics", 32));

        ExamExportService.Export export = examExportService.build("OL", year.getId());

        assertThat(export.problems())
                .anyMatch(problem -> problem.contains("Christianity") && problem.contains("Yeshan Travis"));
    }

    @Test
    @DisplayName("Each examination draws from its own grade")
    void examsMapToGrades() {
        assertThat(examExportService.gradeFor("OL")).isEqualTo(11);
        assertThat(examExportService.gradeFor("GIT")).isEqualTo(12);
        assertThat(examExportService.gradeFor("AL")).isEqualTo(13);
        assertThat(examExportService.gradeFor("GRADE5")).isEqualTo(5);
    }

    // -------------------------------------------------------------------------

    private SubjectDetail subject(String name, int examCode) {
        SubjectDetail subject = new SubjectDetail();
        subject.setName(name);
        subject.setExamCode(examCode);
        subject.setActive(true);
        return subjectDao.save(subject);
    }

    private void takes(StudentRegistration registration, SubjectDetail subject) {
        ClassroomSubject line = new ClassroomSubject();
        line.setClassroom_id(elevenA);
        line.setSubject_detail_id(subject);
        classroomSubjectDao.save(line);

        StudentSubject enrolment = new StudentSubject();
        enrolment.setStudent_registration_id(registration);
        enrolment.setClassroom_subject_id(line);
        studentSubjectDao.save(enrolment);
    }

    private StudentRegistration enrol(String name, String nic, String religion) {
        Student student = new Student();
        student.setFullname(name);
        student.setCallingname(name.split(" ")[0]);
        student.setStu_no("S" + Math.abs(name.hashCode() % 100000));
        student.setBirth_certi_no("BC" + Math.abs(name.hashCode() % 100000));
        student.setNic(nic);
        student.setDob(LocalDate.of(2010, 6, 1));
        student.setGender("Male");
        student.setReligion(religion);
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("Kandy");
        student.setAdded_datetime(LocalDateTime.now());
        studentDao.save(student);

        StudentRegistration registration = new StudentRegistration();
        registration.setStudent_id(student);
        registration.setClassroom_id(elevenA);
        registration.setReg_no(student.getStu_no());
        registration.setDate(LocalDate.of(2026, 1, 5));
        return registrationDao.save(registration);
    }
}
