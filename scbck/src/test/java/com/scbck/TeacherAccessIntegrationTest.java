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
import com.scbck.model.Employee;
import com.scbck.model.Grade;
import com.scbck.model.Module;
import com.scbck.model.Privilage;
import com.scbck.model.Role;
import com.scbck.model.Status;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentStatus;
import com.scbck.model.Term;
import com.scbck.model.User;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.ModuleDao;
import com.scbck.repository.PrivilageDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StatusDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.TermDao;
import com.scbck.repository.UserDao;

/**
 * Cover for what a teacher account can reach, and what it cannot.
 *
 * Two separate reports from the school meet here. The first was that a new
 * teacher account showed an empty class picker: the list came from an endpoint
 * gated on the Class module, which a teacher has no reason to hold, and the
 * browser rendered the 403 as an absence of data.
 *
 * The second is the rule that matters more: a teacher granted permission to
 * update attendance must only be able to update it for their own class. Being
 * able to see every class is not the same as being able to mark every class,
 * and the tests below hold those two apart deliberately - a teacher who has
 * been given the Attendance privilege in full still cannot touch a register
 * that is not theirs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherAccessIntegrationTest {

    private static final String PASSWORD = "TeacherPass123";

    /**
     * A year of its own: SeedScriptIntegrationTest loads the real seed scripts
     * without a transaction, so its 2026 outlives it in the shared database.
     */
    private static final String FIXTURE_YEAR = "2024";

    /**
     * A term inside that year, and in the past.
     *
     * The register refuses a date in the future, so a fixture year set in 2097
     * cannot be marked at all - the positive case failed on the guard rather
     * than on anything it was meant to prove.
     */
    private static final LocalDate TERM_START = LocalDate.of(2024, 1, 5);

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
    private ClassroomDao classroomDao;
    @Autowired
    private TermDao termDao;
    @Autowired
    private EmployeeDao employeeDao;
    @Autowired
    private StatusDao statusDao;
    @Autowired
    private StudentDao studentDao;
    @Autowired
    private StudentStatusDao studentStatusDao;
    @Autowired
    private StudentRegistrationDao registrationDao;
    @Autowired
    private ModuleDao moduleDao;
    @Autowired
    private PrivilageDao privilageDao;

    private AcademicYear year;
    private Classroom ownClass;
    private Classroom otherClass;
    private Student ownPupil;
    private Student otherPupil;

    @BeforeEach
    void seed() {
        Grade grade = new Grade();
        grade.setName("Grade 7");
        gradeDao.save(grade);

        year = new AcademicYear();
        year.setName(FIXTURE_YEAR);
        year.setCurrent_year(false);
        year.setStart_date(TERM_START);
        year.setEnd_date(LocalDate.of(2024, 12, 11));
        academicYearDao.save(year);

        Term term = new Term();
        term.setName("First Term");
        term.setStart_date(TERM_START);
        term.setEnd_date(LocalDate.of(2024, 4, 3));
        term.setAcademic_year_id(year);
        termDao.save(term);

        Employee pasidu = employee("Pasidu Rathnayake", "911111111V", "pasidu.staff@scbc.test");
        Employee nimal = employee("Nimal Silva", "922222222V", "nimal.staff@scbc.test");

        // Two classes of the same grade: one this teacher is responsible for,
        // one they are not. Everything below turns on that difference.
        ownClass = classroom("A", grade, pasidu);
        otherClass = classroom("B", grade, nimal);

        StudentStatus active = new StudentStatus();
        active.setName("Active");
        studentStatusDao.save(active);

        ownPupil = student("Amara Perera", "09700001", "BC9700001", active);
        otherPupil = student("Kasun Fernando", "09700002", "BC9700002", active);
        enrol(ownPupil, ownClass);
        enrol(otherPupil, otherClass);

        // Granted Attendance in full, which is the case the school asked
        // about: the privilege is held, and it must still reach only their own
        // class.
        teacherAccount("pasidu", pasidu);
        grantAttendanceToTeachers();
    }

    // ---- Seeing the list ----------------------------------------------------

    @Test
    @DisplayName("A teacher can list classes at all, which is what the empty picker was about")
    void teacherCanSeeTheClassPicker() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(get("/api/classes")
                .param("academicYearId", String.valueOf(year.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Asked for their own, a teacher gets only the class they are responsible for")
    void mineOnlyNarrowsToTheirOwnClass() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(get("/api/classes")
                .param("academicYearId", String.valueOf(year.getId()))
                .param("mineOnly", "true")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[0].editable").value(true));
    }

    @Test
    @DisplayName("The full list marks which classes are the caller's to change")
    void editableFlagDistinguishesTheirOwn() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(get("/api/classes")
                .param("academicYearId", String.valueOf(year.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='A')].editable").value(
                        org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$[?(@.name=='B')].editable").value(
                        org.hamcrest.Matchers.contains(false)));
    }

    // ---- Marking it ---------------------------------------------------------

    @Test
    @DisplayName("A teacher may mark the register of their own class")
    void teacherMarksTheirOwnRegister() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(saveAttendance(ownClass, ownPupil, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(1));
    }

    @Test
    @DisplayName("Holding the Attendance privilege does not let a teacher mark another class")
    void teacherCannotMarkAnotherClass() throws Exception {
        MockHttpSession session = signIn("pasidu");

        // The account has Attendance select, insert and update. The refusal is
        // not about the privilege - it is about whose class this is.
        mockMvc.perform(saveAttendance(otherClass, otherPupil, session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reading the class list does not let a teacher delete a class")
    void readingIsNotManaging() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/classes/" + ownClass.getId())
                .session(session)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder saveAttendance(Classroom classroom,
            Student student, MockHttpSession session) {

        String body = "{\"classroomId\":" + classroom.getId()
                + ",\"date\":\"" + TERM_START + "\""
                + ",\"marks\":[{\"studentId\":" + student.getId() + ",\"present\":true}]}";

        return put("/api/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .session(session)
                .with(csrf());
    }

    private User teacherAccount(String username, Employee employee) {
        User account = new User();
        account.setUsername(username);
        account.setUseremail(username + "@scbc.test");
        account.setStatus(true);
        account.setAdded_datetime(LocalDateTime.now());
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRoles(Set.of(teacherRole()));
        account.setEmployee_id(employee);
        return userDao.save(account);
    }

    private Role teacherRole() {
        return roleDao.findByName("Teacher").orElseGet(() -> {
            Role role = new Role();
            role.setName("Teacher");
            return roleDao.save(role);
        });
    }

    /** Grants the Teacher role Attendance rights, as the school would. */
    private void grantAttendanceToTeachers() {
        Module attendance = moduleDao.findAll().stream()
                .filter(module -> "Attendance".equalsIgnoreCase(module.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Module created = new Module();
                    created.setName("Attendance");
                    return moduleDao.save(created);
                });

        Privilage grant = new Privilage();
        grant.setRole_id(teacherRole());
        grant.setModule_id(attendance);
        grant.setPrivilage_select(true);
        grant.setPrivilage_insert(true);
        grant.setPrivilage_update(true);
        grant.setPrivilage_delete(false);
        privilageDao.save(grant);
    }

    private Employee employee(String name, String nic, String email) {
        Status active = statusDao.findAll().stream()
                .filter(status -> "Active".equalsIgnoreCase(status.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Status created = new Status();
                    created.setName("Active");
                    return statusDao.save(created);
                });

        Employee employee = new Employee();
        employee.setFullname(name);
        employee.setCallingname(name.split(" ")[0]);
        employee.setNic(nic);
        employee.setGender("Male");
        employee.setDob(LocalDate.of(1985, 3, 12));
        employee.setEmail(email);
        employee.setCivilstatus("Single");
        employee.setMobileno("0770000001");
        employee.setAddress("Kandy");
        employee.setStatus_id(active);
        employee.setAdded_datetime(LocalDateTime.now());
        return employeeDao.save(employee);
    }

    private Classroom classroom(String name, Grade grade, Employee classTeacher) {
        Classroom classroom = new Classroom();
        classroom.setName(name);
        classroom.setGrade_id(grade);
        classroom.setAcademic_year_id(year);
        classroom.setMedium("Sinhala");
        classroom.setEmployee_id(classTeacher);
        return classroomDao.save(classroom);
    }

    private Student student(String name, String admissionNo, String birthCertNo,
            StudentStatus status) {
        Student student = new Student();
        student.setFullname(name);
        student.setCallingname(name.split(" ")[0]);
        student.setStu_no(admissionNo);
        student.setBirth_certi_no(birthCertNo);
        student.setDob(LocalDate.of(2012, 7, 4));
        student.setGender("Female");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("Kandy");
        student.setStudent_status_id(status);
        student.setAdded_datetime(LocalDateTime.now());
        return studentDao.save(student);
    }

    private void enrol(Student student, Classroom classroom) {
        StudentRegistration enrolment = new StudentRegistration();
        enrolment.setStudent_id(student);
        enrolment.setClassroom_id(classroom);
        enrolment.setReg_no("R" + student.getStu_no());
        enrolment.setDate(TERM_START);
        registrationDao.save(enrolment);
    }

    private MockHttpSession signIn(String username) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
