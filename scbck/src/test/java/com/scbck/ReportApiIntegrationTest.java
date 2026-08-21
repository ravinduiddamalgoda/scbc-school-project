package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import com.scbck.model.Employee;
import com.scbck.model.Grade;
import com.scbck.model.RegistrationStatus;
import com.scbck.model.Role;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentStatus;
import com.scbck.model.StudentSubject;
import com.scbck.model.SubjectCategory;
import com.scbck.model.SubjectDetail;
import com.scbck.model.User;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.RegistrationStatusDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.SubjectCategoryDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.repository.UserDao;

/**
 * End-to-end cover for the reporting pipeline: records in, figures out, PDF on
 * the way to the printer.
 *
 * The point of the assertions is that no number in a report is stored anywhere
 * - each one is derived from the register at the moment it is asked for. The
 * fixture below is deliberately uneven (two classes, a shared teacher, an
 * optional subject only some students take, one deleted student and one
 * cancelled enrolment) so a report that simply echoed a class size would fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123";

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
    private SubjectDetailDao subjectDao;
    @Autowired
    private EmployeeDao employeeDao;
    @Autowired
    private ClassroomDao classroomDao;
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
    private RegistrationStatusDao registrationStatusDao;
    @Autowired
    private SubjectCategoryDao subjectCategoryDao;

    private Classroom gradeSixA;
    private Classroom gradeSixB;
    private ClassroomSubject sixAArt;

    /** Staff numbers are unique, so the fixture hands them out in sequence. */
    private int staffSequence;

    @BeforeEach
    void seed() {
        seedAdminAccount();

        AcademicYear year = new AcademicYear();
        year.setName("2026");
        year.setCurrent_year(true);
        academicYearDao.save(year);

        Grade grade6 = grade("Grade 6");
        Grade grade12 = grade("Grade 12");

        StudentStatus active = studentStatus("Active");
        StudentStatus deleted = studentStatus("Deleted");
        RegistrationStatus enrolled = registrationStatus("Active");
        RegistrationStatus cancelled = registrationStatus("Cancelled");

        Employee perera = employee("Mr Perera", "900000001V", "perera@scbc.test", "0770000001");
        Employee silva = employee("Ms Silva", "900000002V", "silva@scbc.test", "0770000002");

        SubjectDetail maths = subject("Maths", "Core");
        SubjectDetail art = subject("Art", "Aesthetic");

        gradeSixA = classroom(grade6, "A", year, perera);
        gradeSixB = classroom(grade6, "B", year, silva);
        // A class with no teacher, to prove the report prints it rather than
        // dropping it.
        Classroom twelveMaths = classroom(grade12, "MATHS", year, null);

        // Perera takes Maths for both Grade 6 classes: one distinct teacher, not
        // two, which is the figure the Subject Wise Teachers report must show.
        ClassroomSubject sixAMaths = timetable(gradeSixA, maths, perera);
        ClassroomSubject sixBMaths = timetable(gradeSixB, maths, perera);
        sixAArt = timetable(gradeSixA, art, silva);
        timetable(twelveMaths, maths, null);

        // Grade 6 A: three students on the roll, only two of them take Art.
        StudentRegistration amara = enrol(student("Amara", active), gradeSixA, enrolled);
        StudentRegistration bimal = enrol(student("Bimal", active), gradeSixA, enrolled);
        StudentRegistration chathu = enrol(student("Chathu", active), gradeSixA, enrolled);

        takes(amara, sixAMaths);
        takes(bimal, sixAMaths);
        takes(chathu, sixAMaths);
        takes(amara, sixAArt);
        takes(bimal, sixAArt);

        // Grade 6 B: one on the roll, plus two who must not be counted - a
        // soft-deleted student, and a cancelled enrolment.
        StudentRegistration dilan = enrol(student("Dilan", active), gradeSixB, enrolled);
        takes(dilan, sixBMaths);

        StudentRegistration erandi = enrol(student("Erandi", deleted), gradeSixB, enrolled);
        takes(erandi, sixBMaths);

        StudentRegistration fathima = enrol(student("Fathima", active), gradeSixB, cancelled);
        takes(fathima, sixBMaths);
    }

    // ---- Reports ------------------------------------------------------------

    @Test
    @DisplayName("The catalogue names every report and the inputs it needs")
    void catalogueListsEveryReport() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports").session(session))
                .andExpect(status().isOk())
                // Ten since the Annual Attendance Register joined the monthly
                // one: the school reads attendance by month and by year.
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].key").value("class-teachers"))
                .andExpect(jsonPath("$[0].parameters").value(org.hamcrest.Matchers.contains("academicYear")))
                // The client renders its parameter controls from this, so a
                // report needing a class has to say so here.
                .andExpect(jsonPath("$[?(@.key=='attendance-register')].parameters[*]")
                        .value(org.hamcrest.Matchers.hasItems("classroom", "month")))
                .andExpect(jsonPath("$[?(@.key=='fee-details')].parameters[*]")
                        .value(org.hamcrest.Matchers.hasItem("student")));
    }

    @Test
    @DisplayName("Class Teachers lists every class, including one with no teacher")
    void classTeachersReportListsAssignmentsAndGaps() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/class-teachers").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value("2026"))
                // Grades 6 and 12 fall in different bands, so there are two.
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].title").value("Grade 6 to Grade 9"))
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("A"))
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("Mr Perera"))
                .andExpect(jsonPath("$.sections[0].rows[1][2]").value("Ms Silva"))
                .andExpect(jsonPath("$.sections[1].title").value("Grade 12 to Grade 13"))
                .andExpect(jsonPath("$.sections[1].rows[0][2]").value("Not assigned"));
    }

    @Test
    @DisplayName("Student counts exclude deleted students and cancelled enrolments")
    void studentCountReportCountsOnlyLiveEnrolments() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/student-counts").session(session))
                .andExpect(status().isOk())
                // Grade 6 A: three active enrolments.
                .andExpect(jsonPath("$.sections[0].rows[0][3]").value("3"))
                // Grade 6 B: three enrolment rows, but one student is deleted and
                // one enrolment is cancelled, so the roll is one.
                .andExpect(jsonPath("$.sections[0].rows[1][3]").value("1"))
                .andExpect(jsonPath("$.sections[0].footer[3]").value("4"));
    }

    @Test
    @DisplayName("Subject teachers counts a teacher once however many classes they take")
    void subjectTeacherReportCountsDistinctTeachers() throws Exception {
        MockHttpSession session = signIn();

        String body = mockMvc.perform(get("/api/reports/subject-teachers").session(session))
                .andExpect(status().isOk())
                // Columns are [Grade, Maths, Art]. Category order comes from each
                // category's own sortOrder, so Core prints before the optional
                // baskets - the order this report always claimed to use. While
                // the category was free text it sorted by name instead, which
                // put "Aesthetic" ahead of "Core" and read as a bug in the data.
                .andExpect(jsonPath("$.sections[0].columns[0].header").value("Grade"))
                .andExpect(jsonPath("$.sections[0].columns[1].header").value("Maths"))
                .andExpect(jsonPath("$.sections[0].columns[2].header").value("Art"))
                // Maths: Mr Perera, taking two classes but counted once. Art: Ms Silva.
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("Grade 6 to Grade 9");
    }

    @Test
    @DisplayName("Subject student counts reflect who actually takes each subject")
    void subjectStudentCountReportCountsPerSubject() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/subject-student-counts").session(session))
                .andExpect(status().isOk())
                // Grade 6 A row: [Grade, Class, Maths, Art] - three take Maths,
                // two take Art. A report echoing the class size would say 3 and 3.
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("A"))
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("3"))
                .andExpect(jsonPath("$.sections[0].rows[0][3]").value("2"))
                // Grade 6 B does not offer Art at all: blank, not zero.
                .andExpect(jsonPath("$.sections[0].rows[1][2]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[1][3]").value(""));
    }

    @Test
    @DisplayName("An unknown report key is a 404, not an empty document")
    void unknownReportKeyIsNotFound() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/attendance-summary").session(session))
                .andExpect(status().isNotFound());
    }

    // ---- PDF ----------------------------------------------------------------

    @Test
    @DisplayName("Every report renders to a real PDF with a named attachment")
    void everyReportRendersToPdf() throws Exception {
        MockHttpSession session = signIn();

        for (String key : new String[] { "class-teachers", "student-counts",
                "subject-teachers", "subject-student-counts" }) {

            byte[] pdf = mockMvc.perform(get("/api/reports/" + key + "/pdf").session(session))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("attachment")))
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("2026.pdf")))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1))
                    .as("%s should begin with the PDF magic number", key)
                    .isEqualTo("%PDF-");
            assertThat(pdf.length).as("%s should not be an empty document", key).isGreaterThan(800);
        }
    }

    @Test
    @DisplayName("Reports need a session, like every other endpoint")
    void reportsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/reports/class-teachers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reports/class-teachers/pdf"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Enrolment rules ----------------------------------------------------

    @Test
    @DisplayName("A student cannot be enrolled in a subject another class is taught")
    void enrolmentRejectsSubjectsFromAnotherClass() throws Exception {
        MockHttpSession session = signIn();

        Student outsider = student("Gayan", studentStatus("Active"));

        mockMvc.perform(post("/api/enrolments")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "studentId": %d, "classroomId": %d, "classroomSubjectIds": [%d] }
                        """.formatted(outsider.getId(), gradeSixB.getId(), sixAArt.getId()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not on the timetable")));
    }

    @Test
    @DisplayName("Enrolling a student changes the count the report reads")
    void enrolmentImmediatelyMovesTheReportedFigure() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/student-counts").session(session))
                .andExpect(jsonPath("$.sections[0].rows[1][3]").value("1"));

        Student newcomer = student("Hasini", studentStatus("Active"));

        mockMvc.perform(post("/api/enrolments")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "studentId": %d, "classroomId": %d }
                        """.formatted(newcomer.getId(), gradeSixB.getId()))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regNo").isNotEmpty())
                .andExpect(jsonPath("$.status.name").value("Active"));

        mockMvc.perform(get("/api/reports/student-counts").session(session))
                .andExpect(jsonPath("$.sections[0].rows[1][3]").value("2"));
    }

    @Test
    @DisplayName("A class can be added without naming an academic year")
    void classCreationDefaultsToTheCurrentYear() throws Exception {
        MockHttpSession session = signIn();

        // The year picker sits on "current year" until someone changes it, so
        // this is the payload the screen sends by default. It used to be
        // rejected outright with "academicYearId is required".
        Integer gradeId = gradeDao.findAll().get(0).getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/classes")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Z", "gradeId": %d, "medium": "Sinhala" }
                        """.formatted(gradeId))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Z"))
                .andExpect(jsonPath("$.academicYear.name").value("2026"));
    }

    @Test
    @DisplayName("Editing a class without naming a year leaves it in its own year")
    void classUpdateKeepsItsExistingYear() throws Exception {
        MockHttpSession session = signIn();

        AcademicYear lastYear = new AcademicYear();
        lastYear.setName("2025");
        lastYear.setCurrent_year(false);
        academicYearDao.save(lastYear);

        Classroom old = new Classroom();
        old.setName("OLD");
        old.setGrade_id(gradeSixA.getGrade_id());
        old.setAcademic_year_id(lastYear);
        classroomDao.save(old);

        // No academicYearId in the payload. Resolving it would silently move
        // this 2025 class into 2026 - a year's enrolments filed under the wrong
        // year because someone corrected a class name.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/classes/" + old.getId())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "OLD B", "gradeId": %d }
                        """.formatted(old.getGrade_id().getId()))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("OLD B"))
                .andExpect(jsonPath("$.academicYear.name").value("2025"));
    }

    @Test
    @DisplayName("Replacing a timetable keeps the enrolments of the subjects that stay")
    void timetableReplacementSparesTheSubjectsThatSurvive() throws Exception {
        MockHttpSession session = signIn();

        SubjectDetail maths = subjectDao.getByName("Maths");
        Integer teacherId = employeeDao.findAll().get(0).getId();

        // Grade 6 A is taught Maths and Art, and Amara takes both. Drop Art.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/classes/" + gradeSixA.getId() + "/subjects")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [ { "subjectId": %d, "teacherId": %d } ]
                        """.formatted(maths.getId(), teacherId))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject.name").value("Maths"))
                // The three students taking Maths are still taking it: the line
                // survived the replacement, so it kept its id and its enrolments.
                .andExpect(jsonPath("$[0].studentCount").value(3));

        // Art is gone from the report, and took its two students with it.
        mockMvc.perform(get("/api/reports/subject-student-counts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].columns.length()").value(3))
                .andExpect(jsonPath("$.sections[0].columns[2].header").value("Maths"))
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("3"));
    }

    // ---- Fixture helpers ----------------------------------------------------

    private void seedAdminAccount() {
        if (userDao.getByUsername("Admin") != null) {
            return;
        }

        Role adminRole = new Role();
        adminRole.setName("Admin");
        roleDao.save(adminRole);

        User admin = new User();
        admin.setUsername("Admin");
        admin.setUseremail("admin@scbc.test");
        admin.setStatus(true);
        admin.setAdded_datetime(LocalDateTime.now());
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRoles(Set.of(adminRole));
        userDao.save(admin);
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

    private Grade grade(String name) {
        Grade grade = new Grade();
        grade.setName(name);
        return gradeDao.save(grade);
    }

    private StudentStatus studentStatus(String name) {
        StudentStatus existing = studentStatusDao.findAll().stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        StudentStatus status = new StudentStatus();
        status.setName(name);
        return studentStatusDao.save(status);
    }

    private RegistrationStatus registrationStatus(String name) {
        RegistrationStatus existing = registrationStatusDao.getByName(name);
        if (existing != null) {
            return existing;
        }

        RegistrationStatus status = new RegistrationStatus();
        status.setName(name);
        return registrationStatusDao.save(status);
    }

    private Employee employee(String name, String nic, String email, String mobile) {
        Employee employee = new Employee();
        employee.setFullname(name);
        employee.setCallingname(name);
        employee.setNic(nic);
        employee.setEmail(email);
        employee.setMobileno(mobile);
        employee.setGender("Male");
        employee.setCivilstatus("Single");
        employee.setDob(LocalDate.of(1985, 1, 1));
        employee.setAddress("Colombo");
        employee.setEmp_no(String.format("%08d", ++staffSequence));
        return employeeDao.save(employee);
    }

    private SubjectDetail subject(String name, String category) {
        SubjectDetail subject = new SubjectDetail();
        subject.setName(name);
        subject.setCategory(category(category));
        subject.setActive(true);
        return subjectDao.save(subject);
    }

    /** Categories are rows now, so the fixture creates them on first use. */
    private SubjectCategory category(String name) {
        return subjectCategoryDao.findByName(name).orElseGet(() -> {
            SubjectCategory created = new SubjectCategory();
            created.setName(name);
            created.setSortOrder("Core".equals(name) ? 0 : 1);
            created.setActive(true);
            return subjectCategoryDao.save(created);
        });
    }

    private Classroom classroom(Grade grade, String name, AcademicYear year, Employee teacher) {
        Classroom classroom = new Classroom();
        classroom.setGrade_id(grade);
        classroom.setName(name);
        classroom.setAcademic_year_id(year);
        classroom.setEmployee_id(teacher);
        return classroomDao.save(classroom);
    }

    private ClassroomSubject timetable(Classroom classroom, SubjectDetail subject, Employee teacher) {
        ClassroomSubject link = new ClassroomSubject();
        link.setClassroom_id(classroom);
        link.setSubject_detail_id(subject);
        link.setEmployee_id(teacher);
        return classroomSubjectDao.save(link);
    }

    private Student student(String name, StudentStatus status) {
        Student student = new Student();
        student.setFullname(name);
        student.setCallingname(name);
        student.setBirth_certi_no("BC" + name);
        student.setGender("Female");
        student.setDob(LocalDate.of(2014, 5, 1));
        student.setReligion("Buddhism");
        student.setNationality("Sinhalese");
        student.setPrevious_scl("None");
        student.setAddress("Colombo");
        student.setStudent_status_id(status);
        student.setStu_no(name);
        return studentDao.save(student);
    }

    private StudentRegistration enrol(Student student, Classroom classroom, RegistrationStatus status) {
        StudentRegistration registration = new StudentRegistration();
        registration.setStudent_id(student);
        registration.setClassroom_id(classroom);
        registration.setRegistration_status_id(status);
        registration.setDate(LocalDate.now());
        registration.setReg_no(String.format("%010d", registrationDao.nextRegSequence()));
        return registrationDao.save(registration);
    }

    private void takes(StudentRegistration registration, ClassroomSubject line) {
        StudentSubject row = new StudentSubject();
        row.setStudent_registration_id(registration);
        row.setClassroom_subject_id(line);
        studentSubjectDao.save(row);
    }
}
