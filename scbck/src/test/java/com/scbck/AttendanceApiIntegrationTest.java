package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.hamcrest.Matchers;
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
import com.scbck.model.GradeHead;
import com.scbck.model.Payment;
import com.scbck.model.PaymentType;
import com.scbck.model.RegistrationStatus;
import com.scbck.model.Role;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentStatus;
import com.scbck.model.Term;
import com.scbck.model.User;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.GradeHeadDao;
import com.scbck.repository.PaymentDao;
import com.scbck.repository.PaymentTypeDao;
import com.scbck.repository.RegistrationStatusDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.TermDao;
import com.scbck.repository.UserDao;

/**
 * Attendance marking end to end, and the four v2 reports that depend on the
 * records it and its neighbours create.
 *
 * The fixture marks a deliberately uneven week - one student always in, one
 * often out, one left unmarked on a day - so a report that assumed a full
 * class, or treated "not marked" as "absent", would produce different numbers
 * from the ones asserted here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123";

    /** A settled week in the past, so nothing here depends on today's date. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

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
    private EmployeeDao employeeDao;
    @Autowired
    private GradeHeadDao gradeHeadDao;
    @Autowired
    private ClassroomDao classroomDao;
    @Autowired
    private StudentDao studentDao;
    @Autowired
    private StudentStatusDao studentStatusDao;
    @Autowired
    private StudentRegistrationDao registrationDao;
    @Autowired
    private RegistrationStatusDao registrationStatusDao;
    @Autowired
    private PaymentDao paymentDao;
    @Autowired
    private PaymentTypeDao paymentTypeDao;

    private Classroom sinhalaClass;
    private Classroom englishClass;
    private Student amara;
    private Student bimal;
    private Student chathu;
    private int staffSequence;
    private int regSequence;

    @BeforeEach
    void seed() {
        seedAdminAccount();

        AcademicYear year = new AcademicYear();
        year.setName("2026");
        year.setCurrent_year(true);
        year.setStart_date(LocalDate.of(2026, 1, 5));
        year.setEnd_date(LocalDate.of(2026, 12, 11));
        academicYearDao.save(year);

        term(year, "First Term", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 4, 3));
        term(year, "Second Term", LocalDate.of(2026, 4, 27), LocalDate.of(2026, 8, 7));

        Grade grade6 = grade("Grade 6");
        Employee perera = employee("Mr Perera");
        Employee silva = employee("Ms Silva");

        gradeHead(grade6, year, silva);

        sinhalaClass = classroom(grade6, "A", year, perera, "Sinhala");
        englishClass = classroom(grade6, "B", year, null, "English");

        StudentStatus active = studentStatus("Active");
        registrationStatus("Active");

        amara = student("Amara", "0001", active);
        bimal = student("Bimal", "0002", active);
        chathu = student("Chathu", "0003", active);

        enrol(amara, sinhalaClass);
        enrol(bimal, sinhalaClass);
        enrol(chathu, englishClass);
    }

    // ---- Marking ------------------------------------------------------------

    @Test
    @DisplayName("The register lists the roll before it has ever been saved")
    void sheetIsReturnedForAnUnmarkedDay() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/attendance")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("date", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(false))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.unmarked").value(2))
                .andExpect(jsonPath("$.classTeacher.name").value("Mr Perera"))
                // In admission-number order, so the page reads the same daily.
                .andExpect(jsonPath("$.students[0].name").value("Amara"))
                .andExpect(jsonPath("$.students[0].present").doesNotExist());
    }

    @Test
    @DisplayName("Saving twice corrects the day rather than adding a second register")
    void savingIsIdempotentPerClassAndDate() throws Exception {
        MockHttpSession session = signIn();

        markDay(session, sinhalaClass, MONDAY, present(amara), absent(bimal));

        mockMvc.perform(get("/api/attendance")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("date", MONDAY.toString()))
                .andExpect(jsonPath("$.marked").value(true))
                .andExpect(jsonPath("$.present").value(1))
                .andExpect(jsonPath("$.absent").value(1));

        // Correcting the same day: Bimal turned up after all.
        markDay(session, sinhalaClass, MONDAY, present(amara), present(bimal));

        mockMvc.perform(get("/api/attendance")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("date", MONDAY.toString()))
                .andExpect(jsonPath("$.present").value(2))
                .andExpect(jsonPath("$.absent").value(0));

        mockMvc.perform(get("/api/attendance/days")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("from", MONDAY.toString())
                .param("to", MONDAY.plusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("A student from another class cannot be marked on this register")
    void marksAreRejectedForStudentsNotOnTheRoll() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(put("/api/attendance")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "classroomId": %d, "date": "%s",
                          "marks": [ { "studentId": %d, "present": true } ] }
                        """.formatted(sinhalaClass.getId(), MONDAY, chathu.getId()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("not on the roll")));
    }

    @Test
    @DisplayName("Attendance cannot be marked ahead of time")
    void futureDatesAreRejected() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(put("/api/attendance")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "classroomId": %d, "date": "%s", "marks": [] }
                        """.formatted(sinhalaClass.getId(), LocalDate.now().plusDays(1)))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("future")));
    }

    @Test
    @DisplayName("Removing a day's register makes it count as school not conducted")
    void deletingARegisterRemovesTheDay() throws Exception {
        MockHttpSession session = signIn();

        markDay(session, sinhalaClass, MONDAY, present(amara), present(bimal));

        String body = mockMvc.perform(get("/api/attendance")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("date", MONDAY.toString()))
                .andReturn().getResponse().getContentAsString();

        int registerId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete("/api/attendance/" + registerId).session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attendance")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("date", MONDAY.toString()))
                .andExpect(jsonPath("$.marked").value(false));
    }

    // ---- Reports ------------------------------------------------------------

    @Test
    @DisplayName("The register report has one column per day school was conducted")
    void registerReportColumnsFollowTheDaysMarked() throws Exception {
        MockHttpSession session = signIn();

        // Monday and Tuesday only. Wednesday is deliberately never marked.
        markDay(session, sinhalaClass, MONDAY, present(amara), present(bimal));
        markDay(session, sinhalaClass, MONDAY.plusDays(1), present(amara), absent(bimal));

        mockMvc.perform(get("/api/reports/attendance-register")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(Matchers.containsString("Grade 6 A")))
                // Adm. No., Name, 2 day columns, 1 week column, Total.
                .andExpect(jsonPath("$.sections[0].columns.length()").value(6))
                .andExpect(jsonPath("$.sections[0].columns[2].header").value("2"))
                .andExpect(jsonPath("$.sections[0].columns[3].header").value("3"))
                .andExpect(jsonPath("$.sections[0].columns[4].header").value("W1"))
                // Amara in both days, Bimal in one.
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[0][3]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[0][5]").value("2"))
                .andExpect(jsonPath("$.sections[0].rows[1][3]").value("0"))
                .andExpect(jsonPath("$.sections[0].rows[1][5]").value("1"))
                // Class totals for each day, then the week, then the month.
                .andExpect(jsonPath("$.sections[0].footer[2]").value("2"))
                .andExpect(jsonPath("$.sections[0].footer[3]").value("1"))
                .andExpect(jsonPath("$.sections[0].footer[5]").value("3"));
    }

    @Test
    @DisplayName("An unmarked student stays blank in the register, not absent")
    void unmarkedStudentsAreNotCountedAsAbsent() throws Exception {
        MockHttpSession session = signIn();

        // Only Amara is marked; Bimal is left alone.
        markDay(session, sinhalaClass, MONDAY, present(amara));

        mockMvc.perform(get("/api/reports/attendance-register")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("1"))
                // Blank, not "0": nobody said Bimal was away.
                .andExpect(jsonPath("$.sections[0].rows[1][2]").value(""));
    }

    @Test
    @DisplayName("The term summary counts days conducted and days attended per term")
    void termAttendanceReportBreaksDownByTerm() throws Exception {
        MockHttpSession session = signIn();

        // Two days in the first term, one in the second.
        markDay(session, sinhalaClass, MONDAY, present(amara), present(bimal));
        markDay(session, sinhalaClass, MONDAY.plusDays(1), present(amara), absent(bimal));
        markDay(session, sinhalaClass, LocalDate.of(2026, 5, 4), present(amara), absent(bimal));

        mockMvc.perform(get("/api/reports/term-attendance")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId())))
                .andExpect(status().isOk())
                // Adm. No., Name, 2 terms x 3, plus Total x 3.
                .andExpect(jsonPath("$.sections[0].columns.length()").value(11))
                .andExpect(jsonPath("$.sections[0].columns[2].header").value("First Term — Days"))
                // Amara: 2 of 2 in the first term, 1 of 1 in the second.
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("2"))
                .andExpect(jsonPath("$.sections[0].rows[0][3]").value("2"))
                .andExpect(jsonPath("$.sections[0].rows[0][4]").value("100.0%"))
                .andExpect(jsonPath("$.sections[0].rows[0][10]").value("100.0%"))
                // Bimal: 1 of 2, then 0 of 1 - and 1 of 3 overall.
                .andExpect(jsonPath("$.sections[0].rows[1][3]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[1][4]").value("50.0%"))
                .andExpect(jsonPath("$.sections[0].rows[1][6]").value("0"))
                .andExpect(jsonPath("$.sections[0].rows[1][9]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[1][10]").value("33.3%"));
    }

    @Test
    @DisplayName("Medium counts split the roll by language of instruction")
    void mediumCountReportSplitsByMedium() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/medium-counts").session(session))
                .andExpect(status().isOk())
                // Grade 6: two in the Sinhala class, one in the English class.
                .andExpect(jsonPath("$.sections[0].rows[0][0]").value("Grade 6"))
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("2"))
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("1"))
                .andExpect(jsonPath("$.sections[0].rows[0][4]").value("3"));
    }

    @Test
    @DisplayName("Grade Heads lists grades with nobody named as well")
    void gradeHeadReportShowsGapsToo() throws Exception {
        MockHttpSession session = signIn();

        grade("Grade 7");

        mockMvc.perform(get("/api/reports/grade-heads").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].rows[0][0]").value("Grade 6"))
                // The contact number sits ahead of the name: the reason anyone
                // looks a grade head up is to reach them.
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("0770000002"))
                .andExpect(jsonPath("$.sections[0].rows[0][2]").value("Ms Silva"))
                // The gap is the thing the report exists to surface.
                .andExpect(jsonPath("$.sections[0].rows[1][1]").value(""))
                .andExpect(jsonPath("$.sections[0].rows[1][2]").value("Not assigned"))
                .andExpect(jsonPath("$.sections[0].footer[2]").value("1 of 2 assigned"));
    }

    @Test
    @DisplayName("Fees Details reads the grade off the enrolment, not the student")
    void feeReportUsesTheEnrolmentGrade() throws Exception {
        MockHttpSession session = signIn();

        PaymentType cash = paymentType("Cash");
        StudentRegistration enrolment = registrationDao.listByStudent(amara.getId()).get(0);

        payment(amara, enrolment, cash, "21000", "21000", LocalDate.of(2026, 2, 10));
        payment(amara, enrolment, cash, "21000", "0", LocalDate.of(2026, 6, 10));

        mockMvc.perform(get("/api/reports/fee-details")
                .session(session)
                .param("studentId", String.valueOf(amara.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(Matchers.containsString("Amara")))
                .andExpect(jsonPath("$.sections[0].subtitle").value(Matchers.containsString("0001")))
                // Oldest first, and the grade comes from the enrolment.
                .andExpect(jsonPath("$.sections[0].rows[0][0]").value("Grade 6"))
                .andExpect(jsonPath("$.sections[0].rows[0][1]").value("2026"))
                .andExpect(jsonPath("$.sections[0].rows[0][5]").value("21,000.00"))
                .andExpect(jsonPath("$.sections[0].rows.length()").value(2))
                // Two receipts of 21,000 each.
                .andExpect(jsonPath("$.sections[0].footer[5]").value("42,000.00"))
                .andExpect(jsonPath("$.sections[0].footer[7]").value("2 payment(s)"));
    }

    @Test
    @DisplayName("A report needing a class says so rather than failing obscurely")
    void reportsRequiringAClassAskForOne() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/reports/attendance-register").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a class to run this report for."));

        mockMvc.perform(get("/api/reports/fee-details").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a student to run this report for."));

        mockMvc.perform(get("/api/reports/attendance-register")
                .session(session)
                .param("classroomId", String.valueOf(sinhalaClass.getId()))
                .param("month", "March"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("2026-08")));
    }

    @Test
    @DisplayName("Every v2 report renders to a real PDF")
    void v2ReportsRenderToPdf() throws Exception {
        MockHttpSession session = signIn();

        markDay(session, sinhalaClass, MONDAY, present(amara), absent(bimal));
        payment(amara, registrationDao.listByStudent(amara.getId()).get(0),
                paymentType("Cash"), "21000", "0", LocalDate.of(2026, 2, 10));

        checkPdf(session, "/api/reports/medium-counts/pdf");
        checkPdf(session, "/api/reports/grade-heads/pdf");
        checkPdf(session, "/api/reports/attendance-register/pdf?classroomId="
                + sinhalaClass.getId() + "&month=2026-03");
        checkPdf(session, "/api/reports/term-attendance/pdf?classroomId=" + sinhalaClass.getId());
        checkPdf(session, "/api/reports/fee-details/pdf?studentId=" + amara.getId());
    }

    // ---- Terms --------------------------------------------------------------

    @Test
    @DisplayName("Overlapping terms are refused")
    void overlappingTermsAreRejected() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/terms")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Extra Term", "start_date": "2026-03-01", "end_date": "2026-05-01" }
                        """)
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("overlaps")));
    }

    @Test
    @DisplayName("A register cannot be opened on a holiday")
    void holidaysBlockAttendance() throws Exception {
        MockHttpSession session = signIn();

        Integer yearId = academicYearDao.findAll().get(0).getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/holidays")
                .param("academicYearId", String.valueOf(yearId))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "date": "2026-05-01", "name": "May Day", "category": "Public holiday" }
                        """)
                .with(csrf()))
                .andExpect(status().isCreated());

        // Both attendance reports count a day as conducted because a register
        // exists, so one opened on a holiday would read as a whole-class absence.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/attendance")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "classroomId": %d, "date": "2026-05-01", "marks": [] }
                        """.formatted(sinhalaClass.getId()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("May Day")));
    }

    @Test
    @DisplayName("A holiday cannot be declared on a day already registered")
    void holidayRefusedWhenAttendanceExists() throws Exception {
        MockHttpSession session = signIn();

        Integer yearId = academicYearDao.findAll().get(0).getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/attendance")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "classroomId": %d, "date": "2026-05-04", "marks": [] }
                        """.formatted(sinhalaClass.getId()))
                .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/holidays")
                .param("academicYearId", String.valueOf(yearId))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "date": "2026-05-04", "name": "Declared late" }
                        """)
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("already been marked")));
    }

    // ---- Helpers ------------------------------------------------------------

    private void checkPdf(MockHttpSession session, String url) throws Exception {
        byte[] pdf = mockMvc.perform(get(url).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1))
                .as("%s should begin with the PDF magic number", url)
                .isEqualTo("%PDF-");
        assertThat(pdf.length).as("%s should not be an empty document", url).isGreaterThan(800);
    }

    private String present(Student student) {
        return "{\"studentId\":" + student.getId() + ",\"present\":true}";
    }

    private String absent(Student student) {
        return "{\"studentId\":" + student.getId() + ",\"present\":false}";
    }

    private void markDay(MockHttpSession session, Classroom classroom, LocalDate date, String... marks)
            throws Exception {
        mockMvc.perform(put("/api/attendance")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"classroomId\":" + classroom.getId() + ",\"date\":\"" + date
                        + "\",\"marks\":[" + String.join(",", marks) + "]}")
                .with(csrf()))
                .andExpect(status().isOk());
    }

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

    private Term term(AcademicYear year, String name, LocalDate from, LocalDate to) {
        Term term = new Term();
        term.setAcademic_year_id(year);
        term.setName(name);
        term.setStart_date(from);
        term.setEnd_date(to);
        return termDao.save(term);
    }

    private Employee employee(String name) {
        Employee employee = new Employee();
        employee.setFullname(name);
        employee.setCallingname(name);
        employee.setNic("90000000" + (++staffSequence) + "V");
        employee.setEmail("staff" + staffSequence + "@scbc.test");
        employee.setMobileno("077000000" + staffSequence);
        employee.setGender("Female");
        employee.setCivilstatus("Single");
        employee.setDob(LocalDate.of(1985, 1, 1));
        employee.setAddress("Colombo");
        employee.setEmp_no(String.format("%08d", staffSequence));
        return employeeDao.save(employee);
    }

    private GradeHead gradeHead(Grade grade, AcademicYear year, Employee employee) {
        GradeHead head = new GradeHead();
        head.setGrade_id(grade);
        head.setAcademic_year_id(year);
        head.setEmployee_id(employee);
        return gradeHeadDao.save(head);
    }

    private Classroom classroom(Grade grade, String name, AcademicYear year, Employee teacher, String medium) {
        Classroom classroom = new Classroom();
        classroom.setGrade_id(grade);
        classroom.setName(name);
        classroom.setAcademic_year_id(year);
        classroom.setEmployee_id(teacher);
        classroom.setMedium(medium);
        return classroomDao.save(classroom);
    }

    private StudentStatus studentStatus(String name) {
        StudentStatus status = new StudentStatus();
        status.setName(name);
        return studentStatusDao.save(status);
    }

    private RegistrationStatus registrationStatus(String name) {
        RegistrationStatus status = new RegistrationStatus();
        status.setName(name);
        return registrationStatusDao.save(status);
    }

    private Student student(String name, String admissionNo, StudentStatus status) {
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
        student.setStu_no(admissionNo);
        return studentDao.save(student);
    }

    private StudentRegistration enrol(Student student, Classroom classroom) {
        StudentRegistration registration = new StudentRegistration();
        registration.setStudent_id(student);
        registration.setClassroom_id(classroom);
        registration.setRegistration_status_id(registrationStatusDao.getByName("Active"));
        registration.setDate(LocalDate.of(2026, 1, 5));
        registration.setReg_no(String.format("%010d", ++regSequence));
        return registrationDao.save(registration);
    }

    private PaymentType paymentType(String name) {
        PaymentType existing = paymentTypeDao.getByName(name);
        if (existing != null) {
            return existing;
        }
        PaymentType type = new PaymentType();
        type.setName(name);
        return paymentTypeDao.save(type);
    }

    private Payment payment(Student student, StudentRegistration enrolment, PaymentType type,
            String paid, String due, LocalDate date) {
        Payment payment = new Payment();
        payment.setStudent_id(student);
        payment.setStudent_registration_id(enrolment);
        payment.setPayment_type_id(type);
        payment.setAmount_paid(new java.math.BigDecimal(paid));
        payment.setAmount_due(new java.math.BigDecimal(due));
        payment.setBalance_amount(new java.math.BigDecimal(due).subtract(new java.math.BigDecimal(paid)));
        payment.setPaid_date(date);
        payment.setBill_no(String.format("%08d", paymentDao.nextBillSequence()));
        return paymentDao.save(payment);
    }
}
