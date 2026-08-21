package com.scbck;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.scbck.model.Attendance;
import com.scbck.model.Classroom;
import com.scbck.model.Grade;
import com.scbck.model.Role;
import com.scbck.model.Student;
import com.scbck.model.StudentAttendance;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentStatus;
import com.scbck.model.User;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.AttendanceDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StudentAttendanceDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.UserDao;

/**
 * Cover for the rule deciding which attendance letter the school may send.
 *
 * This is the assertion that matters most in the attendance work: the
 * twenty- and forty-day notices are formal notices under Circular No. 53/2023,
 * and one sent to a family whose child does not meet the rule is worse than
 * none at all. The threshold is therefore tested at the boundary, and tested
 * for the case that separates a correct implementation from a plausible one -
 * absences spread across a term rather than run together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AbsenceLetterIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123";

    /**
     * A year of its own, for the same reason as the timetable fixture: the seed
     * script test is not transactional and leaves 2026 in the shared database.
     */
    private static final String FIXTURE_YEAR = "2099";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserDao userDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private StudentDao studentDao;
    @Autowired
    private StudentStatusDao studentStatusDao;
    @Autowired
    private GradeDao gradeDao;
    @Autowired
    private AcademicYearDao academicYearDao;
    @Autowired
    private ClassroomDao classroomDao;
    @Autowired
    private StudentRegistrationDao registrationDao;
    @Autowired
    private AttendanceDao attendanceDao;
    @Autowired
    private StudentAttendanceDao markDao;

    private Student student;
    private Classroom classroom;

    /** The Monday the fixture's school days start from. */
    private static final LocalDate TERM_START = LocalDate.of(2026, 1, 5);

    @BeforeEach
    void seed() {
        seedAdminAccount();

        StudentStatus active = new StudentStatus();
        active.setName("Active");
        studentStatusDao.save(active);

        Grade grade = new Grade();
        grade.setName("Grade 11");
        gradeDao.save(grade);

        AcademicYear year = new AcademicYear();
        year.setName(FIXTURE_YEAR);
        year.setCurrent_year(true);
        year.setStart_date(TERM_START);
        year.setEnd_date(LocalDate.of(2026, 12, 11));
        academicYearDao.save(year);

        classroom = new Classroom();
        classroom.setName("B");
        classroom.setGrade_id(grade);
        classroom.setAcademic_year_id(year);
        classroom.setMedium("Sinhala");
        classroomDao.save(classroom);

        student = new Student();
        student.setFullname("Dilan Amarasinghe Malm");
        student.setCallingname("D. A. Malm");
        student.setStu_no("3960");
        student.setBirth_certi_no("BC3960");
        student.setDob(LocalDate.of(2010, 5, 2));
        student.setGender("Male");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("8 Asgiriya Road, Kandy");
        student.setStudent_status_id(active);
        student.setGrade_id(grade);
        student.setAdded_datetime(LocalDateTime.now());
        studentDao.save(student);

        StudentRegistration enrolment = new StudentRegistration();
        enrolment.setStudent_id(student);
        enrolment.setClassroom_id(classroom);
        enrolment.setReg_no("R3960");
        enrolment.setDate(TERM_START);
        registrationDao.save(enrolment);
    }

    @Test
    @DisplayName("Below twenty continuous absences, neither notice is offered")
    void shortRunOffersOnlyTheWeekLetter() throws Exception {
        MockHttpSession session = signIn();

        markSchoolDays(19, false);

        mockMvc.perform(summary(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consecutiveAbsentDays").value(19))
                .andExpect(jsonPath("$.availableLetters").value(org.hamcrest.Matchers.contains("WEEK")));

        // And the endpoint refuses even when asked directly, so the guard does
        // not depend on the browser having hidden the button.
        mockMvc.perform(letter("TWENTY_DAY", session)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Twenty continuous absences unlock the twenty-day notice, not the forty-day one")
    void twentyContinuousAbsencesUnlockTheFirstNotice() throws Exception {
        MockHttpSession session = signIn();

        markSchoolDays(20, false);

        mockMvc.perform(summary(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consecutiveAbsentDays").value(20))
                .andExpect(jsonPath("$.availableLetters")
                        .value(org.hamcrest.Matchers.contains("WEEK", "TWENTY_DAY")));

        mockMvc.perform(letter("TWENTY_DAY", session))
                .andExpect(status().isOk());
        mockMvc.perform(letter("FORTY_DAY", session))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Forty continuous absences unlock both notices")
    void fortyContinuousAbsencesUnlockBoth() throws Exception {
        MockHttpSession session = signIn();

        markSchoolDays(40, false);

        mockMvc.perform(summary(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableLetters")
                        .value(org.hamcrest.Matchers.contains("WEEK", "TWENTY_DAY", "FORTY_DAY")));

        mockMvc.perform(letter("FORTY_DAY", session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A day present breaks the run, however many absences surround it")
    void attendanceBreaksTheRun() throws Exception {
        MockHttpSession session = signIn();

        // Thirty absences, one day present, then five more. The circular is
        // about *continuous* absence, so this is a run of five - a total of
        // thirty-five absences must not unlock anything.
        markSchoolDays(30, false);
        markSchoolDay(30, true);
        for (int day = 31; day < 36; day++) {
            markSchoolDay(day, false);
        }

        mockMvc.perform(summary(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysAbsent").value(35))
                .andExpect(jsonPath("$.consecutiveAbsentDays").value(5))
                .andExpect(jsonPath("$.availableLetters").value(org.hamcrest.Matchers.contains("WEEK")));

        mockMvc.perform(letter("TWENTY_DAY", session)).andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder summary(MockHttpSession session) {
        return get("/api/attendance/students/" + student.getId())
                .param("from", TERM_START.toString())
                .param("to", TERM_START.plusDays(120).toString())
                .session(session);
    }

    private org.springframework.test.web.servlet.RequestBuilder letter(String type,
            MockHttpSession session) {
        return get("/api/attendance/students/" + student.getId() + "/letter")
                .param("type", type)
                .param("from", TERM_START.toString())
                .param("to", TERM_START.plusDays(120).toString())
                .session(session);
    }

    private void markSchoolDays(int count, boolean present) {
        for (int day = 0; day < count; day++) {
            markSchoolDay(day, present);
        }
    }

    /**
     * Marks one school day, weekends skipped.
     *
     * Weekdays only, because the run is counted in school days: a register only
     * exists for a day school was conducted, and a fixture that marked
     * Saturdays would prove the wrong thing.
     */
    private void markSchoolDay(int index, boolean present) {
        LocalDate date = TERM_START.plusDays(index / 5L * 7 + index % 5);

        Attendance register = new Attendance();
        register.setClassroom_id(classroom);
        register.setDate(date);
        attendanceDao.save(register);

        StudentAttendance mark = new StudentAttendance();
        mark.setAttendence_id(register);
        mark.setStudent_id(student);
        mark.setAttendant(present);
        markDao.save(mark);
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
