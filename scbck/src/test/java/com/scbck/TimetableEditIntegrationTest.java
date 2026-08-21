package com.scbck;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.Grade;
import com.scbck.model.Role;
import com.scbck.model.Student;
import com.scbck.model.StudentMark;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentStatus;
import com.scbck.model.StudentSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.model.Term;
import com.scbck.model.User;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentMarkDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.repository.TermDao;
import com.scbck.repository.UserDao;

/**
 * Cover for editing a class timetable, which the school reported twice as "the
 * time table button not working".
 *
 * The button opens the drawer and the drawer loads. What failed was saving it:
 * unticking a subject deletes the enrolment lines for that subject, and
 * {@code student_mark} points at those lines. With any mark recorded the delete
 * hit a foreign key and the save came back as a conflict - which, from the
 * screen, looks exactly like a button that does nothing.
 *
 * Grade 1 classes had A/L subjects on them from the sample data, so unticking
 * those was the first thing anyone tried.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TimetableEditIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123";

    /**
     * A year of its own, because SeedScriptIntegrationTest loads the real seed
     * script without a transaction and leaves 2026 behind for whatever runs next.
     */
    private static final String FIXTURE_YEAR = "2098";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserDao userDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private GradeDao gradeDao;
    @Autowired
    private AcademicYearDao academicYearDao;
    @Autowired
    private TermDao termDao;
    @Autowired
    private ClassroomDao classroomDao;
    @Autowired
    private SubjectDetailDao subjectDao;
    @Autowired
    private ClassroomSubjectDao classroomSubjectDao;
    @Autowired
    private StudentDao studentDao;
    @Autowired
    private StudentStatusDao studentStatusDao;
    @Autowired
    private StudentRegistrationDao registrationDao;
    @Autowired
    private StudentSubjectDao studentSubjectDao;
    @Autowired
    private StudentMarkDao markDao;

    private Classroom classroom;
    private SubjectDetail sinhala;
    private SubjectDetail combinedMaths;

    @BeforeEach
    void seed() {
        seedAdminAccount();

        Grade grade = new Grade();
        grade.setName("Grade 1");
        gradeDao.save(grade);

        AcademicYear year = new AcademicYear();
        year.setName(FIXTURE_YEAR);
        year.setCurrent_year(true);
        year.setStart_date(LocalDate.of(2026, 1, 5));
        year.setEnd_date(LocalDate.of(2026, 12, 11));
        academicYearDao.save(year);

        Term term = new Term();
        term.setName("First Term");
        term.setStart_date(LocalDate.of(2026, 1, 5));
        term.setEnd_date(LocalDate.of(2026, 4, 3));
        term.setAcademic_year_id(year);
        termDao.save(term);

        classroom = new Classroom();
        classroom.setName("C");
        classroom.setGrade_id(grade);
        classroom.setAcademic_year_id(year);
        classroom.setMedium("Sinhala");
        classroomDao.save(classroom);

        sinhala = subject("Sinhala");
        // On the curriculum of grade 12, not of grade 1 - the sample data put
        // it on a primary timetable, which is what the school hit.
        combinedMaths = subject("Combined Maths");

        ClassroomSubject sinhalaLine = line(sinhala);
        ClassroomSubject mathsLine = line(combinedMaths);

        StudentStatus active = new StudentStatus();
        active.setName("Active");
        studentStatusDao.save(active);

        Student student = new Student();
        student.setFullname("Hasini Jayawardena");
        student.setCallingname("A.A. Hasini");
        // Well outside the range the sample data occupies: it is loaded by
        // SeedScriptIntegrationTest without a transaction, so its admission
        // numbers persist for whatever runs afterwards.
        student.setStu_no("09900078");
        student.setBirth_certi_no("BC9900078");
        student.setDob(LocalDate.of(2019, 4, 2));
        student.setGender("Female");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("Kandy");
        student.setStudent_status_id(active);
        student.setAdded_datetime(LocalDateTime.now());
        studentDao.save(student);

        StudentRegistration enrolment = new StudentRegistration();
        enrolment.setStudent_id(student);
        enrolment.setClassroom_id(classroom);
        enrolment.setReg_no("R78");
        enrolment.setDate(LocalDate.of(2026, 1, 5));
        registrationDao.save(enrolment);

        takes(enrolment, sinhalaLine);
        StudentSubject takesMaths = takes(enrolment, mathsLine);

        // The mark that makes the delete fail. Recorded against the subject the
        // school is about to take off the timetable.
        StudentMark mark = new StudentMark();
        mark.setStudent_subject_id(takesMaths);
        mark.setTerm_id(term);
        mark.setMarks(64);
        markDao.save(mark);
    }

    @Test
    @DisplayName("The timetable drawer loads the class's current subjects")
    void drawerLoadsSubjects() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/classes/" + classroom.getId() + "/subjects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("A subject can be taken off the timetable even once marks exist for it")
    void removingASubjectWithMarksSucceeds() throws Exception {
        MockHttpSession session = signIn();

        // Grade 1 keeps Sinhala and loses Combined Maths - the correction the
        // school was trying to make when the save failed.
        mockMvc.perform(put("/api/classes/" + classroom.getId() + "/subjects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"subjectId\":" + sinhala.getId() + ",\"teacherId\":null}]")
                .session(session)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject.name").value("Sinhala"));

        mockMvc.perform(get("/api/classes/" + classroom.getId() + "/subjects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
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

    private ClassroomSubject line(SubjectDetail subject) {
        ClassroomSubject link = new ClassroomSubject();
        link.setClassroom_id(classroom);
        link.setSubject_detail_id(subject);
        return classroomSubjectDao.save(link);
    }

    private StudentSubject takes(StudentRegistration enrolment, ClassroomSubject line) {
        StudentSubject takes = new StudentSubject();
        takes.setStudent_registration_id(enrolment);
        takes.setClassroom_subject_id(line);
        return studentSubjectDao.save(takes);
    }

    private MockHttpSession signIn() throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void seedAdminAccount() {
        if (userDao.getByUsername("Admin") != null) {
            return;
        }

        Role adminRole = roleDao.findByName("Admin").orElseGet(() -> {
            Role role = new Role();
            role.setName("Admin");
            return roleDao.save(role);
        });

        User admin = new User();
        admin.setUsername("Admin");
        admin.setUseremail("admin@scbc.test");
        admin.setStatus(true);
        admin.setAdded_datetime(LocalDateTime.now());
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRoles(Set.of(adminRole));
        userDao.save(admin);
    }
}
