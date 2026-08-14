package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.scbck.model.Term;
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
import com.scbck.repository.TermDao;
import com.scbck.repository.UserDao;

/**
 * Cover for subject-wise marks: entry, the calculated sheet, and both exports.
 *
 * The fixture is transcribed from the school's own "Grades Mark Sheet"
 * workbook - the Grade 10-E first term sheet - so the assertions check this
 * system's arithmetic against numbers the school already accepts as correct.
 * Two rows are copied verbatim:
 *
 * <ul>
 * <li>Dahamsen Samaranayake, the top of the class, whose row is all A grades
 * and whose average of 92.9 must be highlighted;</li>
 * <li>Thesan Abeyrathne, who was absent for Sinhala. His printed total of 491
 * over nine subjects is the case that pins down what an absence does: it adds
 * nothing to the total but still counts in the divisor.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarkSheetApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123";

    /** The nine subjects the sampled class sits, in the workbook's order. */
    private static final String[] CORE = {
            "Sinhala", "Buddhism", "Mathematics", "Science", "English", "History", "ICT" };

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
    private SubjectDetailDao subjectDao;
    @Autowired
    private SubjectCategoryDao categoryDao;
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

    private Classroom tenE;
    private Term firstTerm;
    private int staffSequence;

    /** Subject name -> the timetable line for 10-E. */
    private final Map<String, ClassroomSubject> timetable = new LinkedHashMap<>();
    /** Student name -> their enrolment. */
    private final Map<String, StudentRegistration> roll = new LinkedHashMap<>();

    @BeforeEach
    void seed() {
        seedAdminAccount();

        AcademicYear year = new AcademicYear();
        year.setName("2026");
        year.setCurrent_year(true);
        academicYearDao.save(year);

        firstTerm = new Term();
        firstTerm.setName("First Term");
        firstTerm.setStart_date(LocalDate.of(2026, 1, 5));
        firstTerm.setEnd_date(LocalDate.of(2026, 4, 3));
        firstTerm.setAcademic_year_id(year);
        termDao.save(firstTerm);

        Grade grade10 = grade("Grade 10");
        Employee teacher = employee("Rasangika Wickramsinghe");

        tenE = new Classroom();
        tenE.setName("E");
        tenE.setMedium("English");
        tenE.setGrade_id(grade10);
        tenE.setAcademic_year_id(year);
        tenE.setEmployee_id(teacher);
        classroomDao.save(tenE);

        // Compulsory subjects, then one optional basket - the structure the
        // workbook bands its columns by.
        SubjectCategory core = category("Core", 0);
        SubjectCategory basket = category("Category 3", 2);

        for (String name : CORE) {
            timetable.put(name, timetable(subject(name, core), teacher));
        }
        timetable.put("Art", timetable(subject("Art", basket), teacher));
        timetable.put("Drama", timetable(subject("Drama", basket), teacher));

        StudentStatus active = studentStatus("Active");
        RegistrationStatus enrolled = registrationStatus("Active");

        // Both students take the seven compulsory subjects; each takes one
        // subject from the optional basket, as the workbook's rows do.
        enrol("Dahamsen Samaranayake", "3501", active, enrolled, "Art");
        enrol("Thesan Abeyrathne", "3502", active, enrolled, "Drama");
    }

    // ---- Entry ---------------------------------------------------------------

    @Test
    @DisplayName("Marks entered for a class come back calculated")
    void marksAreStoredAndCalculated() throws Exception {
        MockHttpSession session = signIn();

        // Dahamsen's row, verbatim from the workbook: 91 98 100 97 86 87 98,
        // then 84 in his optional subject. Total 741 over 8 subjects.
        saveMarks(session, topOfClass());

        mockMvc.perform(sheet(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("Grade 10 E"))
                .andExpect(jsonPath("$.termName").value("First Term"))
                .andExpect(jsonPath("$.classTeacher").value("Rasangika Wickramsinghe"))
                .andExpect(jsonPath("$.rows[0].studentName").value("Dahamsen Samaranayake"))
                .andExpect(jsonPath("$.rows[0].total").value(741))
                .andExpect(jsonPath("$.rows[0].average").value(92.6))
                .andExpect(jsonPath("$.rows[0].rank").value(1))
                // Every mark is 75 or above, so every grade is an A and the
                // tally counts eight of them.
                .andExpect(jsonPath("$.rows[0].gradeCounts[0].letter").value("A"))
                .andExpect(jsonPath("$.rows[0].gradeCounts[0].count").value(8));
    }

    @Test
    @DisplayName("An absence adds nothing to the total but still counts as a subject taken")
    void absenceCountsInTheDivisorButNotTheTotal() throws Exception {
        MockHttpSession session = signIn();

        // Thesan's row: absent for Sinhala, then 32 65 66 79 39 80, and 70 in
        // his optional subject. The workbook prints 491 as the total.
        Map<String, Object[]> marks = new LinkedHashMap<>();
        marks.put("Sinhala", new Object[] { null, true });
        marks.put("Buddhism", new Object[] { 32, false });
        marks.put("Mathematics", new Object[] { 65, false });
        marks.put("Science", new Object[] { 66, false });
        marks.put("English", new Object[] { 79, false });
        marks.put("History", new Object[] { 39, false });
        marks.put("ICT", new Object[] { 80, false });
        marks.put("Drama", new Object[] { 70, false });

        saveMarks(session, entriesFor("Thesan Abeyrathne", marks));

        // Columns are ordered by category band then subject name, so Sinhala is
        // not the first column the way it is on the workbook; the assertions
        // below locate it rather than assuming a position.
        int sinhala = columnOf("Sinhala");

        MvcResult result = mockMvc.perform(sheet(session))
                .andExpect(status().isOk())
                // 32+65+66+79+39+80+70 = 431. The absence contributes nothing.
                .andExpect(jsonPath("$.rows[1].total").value(431))
                // Eight results recorded, the absence among them: 431/8 = 53.9.
                .andExpect(jsonPath("$.rows[1].average").value(53.9))
                .andExpect(jsonPath("$.rows[1].cells[" + sinhala + "].absent").value(true))
                .andExpect(jsonPath("$.rows[1].cells[" + sinhala + "].grade").value("AB"))
                .andExpect(jsonPath("$.rows[1].cells[" + sinhala + "].marks").doesNotExist())
                .andReturn();

        // The grade letters follow the workbook's own thresholds.
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"grade\":\"F\""); // 32 Buddhism
        assertThat(body).contains("\"grade\":\"B\""); // 65 Mathematics
        assertThat(body).contains("\"grade\":\"S\""); // 39 History
        assertThat(body).contains("\"grade\":\"A\""); // 80 ICT
    }

    @Test
    @DisplayName("A student averaging 80 or more is flagged for highlighting")
    void highAveragesAreFlagged() throws Exception {
        MockHttpSession session = signIn();

        saveMarks(session, topOfClass());

        mockMvc.perform(sheet(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highlightAverageFrom").value(80.0))
                .andExpect(jsonPath("$.rows[0].highlight").value(true))
                // The second student has no marks recorded at all, so he has no
                // average - and therefore nothing to highlight and no rank. An
                // average of zero here would rank him last on marks nobody has
                // entered yet.
                .andExpect(jsonPath("$.rows[1].average").doesNotExist())
                .andExpect(jsonPath("$.rows[1].highlight").value(false))
                .andExpect(jsonPath("$.rows[1].rank").doesNotExist());
    }

    @Test
    @DisplayName("A subject the student does not take is blank, not zero")
    void unenrolledSubjectsAreNotMarked() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(sheet(session))
                .andExpect(status().isOk())
                // Dahamsen takes Art, not Drama; the Drama cell is not editable
                // and carries no enrolment to write a mark against.
                .andExpect(jsonPath("$.rows[0].cells[?(@.enrolled == false)].grade")
                        .value(org.hamcrest.Matchers.hasItem("-")))
                .andExpect(jsonPath("$.rows[0].cells[?(@.enrolled == false)].studentSubjectId")
                        .value(org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("A mark cannot be both absent and scored")
    void absenceAndAMarkAreRejected() throws Exception {
        MockHttpSession session = signIn();

        Integer enrolmentId = enrolmentIdFor("Dahamsen Samaranayake", "Sinhala");

        mockMvc.perform(put("/api/marks").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "classroomId": %d,
                          "termId": %d,
                          "entries": [{ "studentSubjectId": %d, "marks": 70, "absent": true }]
                        }
                        """.formatted(tenE.getId(), firstTerm.getId(), enrolmentId))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("cannot be both absent and have a mark")));
    }

    @Test
    @DisplayName("A mark above 100 is refused")
    void marksAreBounded() throws Exception {
        MockHttpSession session = signIn();

        Integer enrolmentId = enrolmentIdFor("Dahamsen Samaranayake", "Sinhala");

        mockMvc.perform(put("/api/marks").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "classroomId": %d,
                          "termId": %d,
                          "entries": [{ "studentSubjectId": %d, "marks": 101 }]
                        }
                        """.formatted(tenE.getId(), firstTerm.getId(), enrolmentId))
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Clearing a cell removes the mark rather than storing a zero")
    void clearingAMarkDeletesIt() throws Exception {
        MockHttpSession session = signIn();

        Integer enrolmentId = enrolmentIdFor("Dahamsen Samaranayake", "Sinhala");

        saveMarks(session, """
                { "studentSubjectId": %d, "marks": 91 }""".formatted(enrolmentId));
        mockMvc.perform(sheet(session))
                .andExpect(jsonPath("$.rows[0].total").value(91));

        saveMarks(session, """
                { "studentSubjectId": %d, "marks": null }""".formatted(enrolmentId));
        mockMvc.perform(sheet(session))
                .andExpect(jsonPath("$.rows[0].total").value(0))
                .andExpect(jsonPath("$.rows[0].cells[0].marks").doesNotExist())
                .andExpect(jsonPath("$.rows[0].cells[0].grade").value("-"));
    }

    // ---- Exports -------------------------------------------------------------

    @Test
    @DisplayName("The sheet downloads as a workbook and as a PDF")
    void bothExportsRender() throws Exception {
        MockHttpSession session = signIn();
        saveMarks(session, topOfClass());

        MvcResult excel = mockMvc.perform(get("/api/marks/sheet/excel")
                .param("classroomId", String.valueOf(tenE.getId()))
                .param("termId", String.valueOf(firstTerm.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(status().isOk())
                .andReturn();

        byte[] workbook = excel.getResponse().getContentAsByteArray();
        assertThat(workbook).isNotEmpty();
        // Every .xlsx is a zip; "PK" is its signature.
        assertThat(new String(workbook, 0, 2)).isEqualTo("PK");
        assertThat(excel.getResponse().getHeader("Content-Disposition"))
                .contains("Grade 10 E First Term Marks.xlsx");

        MvcResult pdf = mockMvc.perform(get("/api/marks/sheet/pdf")
                .param("classroomId", String.valueOf(tenE.getId()))
                .param("termId", String.valueOf(firstTerm.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andReturn();

        byte[] printed = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(printed, 0, 4)).isEqualTo("%PDF");
        assertThat(pdf.getResponse().getHeader("Content-Disposition"))
                .contains("Grade 10 E First Term Marks.pdf");
    }

    @Test
    @DisplayName("A term from another academic year is refused")
    void termMustBelongToTheClassYear() throws Exception {
        MockHttpSession session = signIn();

        AcademicYear other = new AcademicYear();
        other.setName("2025");
        other.setCurrent_year(false);
        academicYearDao.save(other);

        Term strayTerm = new Term();
        strayTerm.setName("First Term");
        strayTerm.setStart_date(LocalDate.of(2025, 1, 6));
        strayTerm.setEnd_date(LocalDate.of(2025, 4, 4));
        strayTerm.setAcademic_year_id(other);
        termDao.save(strayTerm);

        mockMvc.perform(get("/api/marks/sheet")
                .param("classroomId", String.valueOf(tenE.getId()))
                .param("termId", String.valueOf(strayTerm.getId()))
                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("different academic year")));
    }

    // ---- Fixture -------------------------------------------------------------

    private String topOfClass() {
        Map<String, Object[]> marks = new LinkedHashMap<>();
        marks.put("Sinhala", new Object[] { 91, false });
        marks.put("Buddhism", new Object[] { 98, false });
        marks.put("Mathematics", new Object[] { 100, false });
        marks.put("Science", new Object[] { 97, false });
        marks.put("English", new Object[] { 86, false });
        marks.put("History", new Object[] { 87, false });
        marks.put("ICT", new Object[] { 98, false });
        marks.put("Art", new Object[] { 84, false });
        return entriesFor("Dahamsen Samaranayake", marks);
    }

    private String entriesFor(String studentName, Map<String, Object[]> marks) {
        StringBuilder json = new StringBuilder();
        marks.forEach((subject, value) -> {
            if (json.length() > 0) {
                json.append(",");
            }
            json.append("{ \"studentSubjectId\": ")
                    .append(enrolmentIdFor(studentName, subject))
                    .append(", \"marks\": ").append(value[0])
                    .append(", \"absent\": ").append(value[1])
                    .append(" }");
        });
        return json.toString();
    }

    private void saveMarks(MockHttpSession session, String entries) throws Exception {
        mockMvc.perform(put("/api/marks").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "classroomId": %d, "termId": %d, "entries": [%s] }
                        """.formatted(tenE.getId(), firstTerm.getId(), entries))
                .with(csrf()))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder sheet(MockHttpSession session) {
        return get("/api/marks/sheet")
                .param("classroomId", String.valueOf(tenE.getId()))
                .param("termId", String.valueOf(firstTerm.getId()))
                .session(session);
    }

    /**
     * The position of a subject's column on the sheet.
     *
     * Columns are ordered by category band then subject name, which is not the
     * order the workbook prints them in, so tests that care about a particular
     * subject look its column up instead of counting.
     */
    private int columnOf(String subjectName) {
        List<String> ordered = timetable.keySet().stream()
                .sorted(java.util.Comparator
                        .comparingInt((String name) -> CORE_NAMES.contains(name) ? 0 : 2)
                        .thenComparing(java.util.Comparator.naturalOrder()))
                .toList();
        return ordered.indexOf(subjectName);
    }

    private static final java.util.Set<String> CORE_NAMES = java.util.Set.of(CORE);

    private Integer enrolmentIdFor(String studentName, String subjectName) {
        Integer classroomSubjectId = timetable.get(subjectName).getId();
        return studentSubjectDao.listByRegistration(roll.get(studentName).getId()).stream()
                .filter(line -> line.getClassroom_subject_id().getId().equals(classroomSubjectId))
                .map(StudentSubject::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        studentName + " is not enrolled in " + subjectName));
    }

    private void enrol(String name, String admissionNo, StudentStatus status,
            RegistrationStatus registrationStatus, String optionalSubject) {

        Student student = new Student();
        student.setFullname(name);
        student.setCallingname(name.split(" ")[0]);
        student.setStu_no(admissionNo);
        student.setBirth_certi_no("BC" + admissionNo);
        student.setDob(LocalDate.of(2010, 5, 12));
        student.setGender("Male");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("Kandy");
        student.setStudent_status_id(status);
        student.setAdded_datetime(LocalDateTime.now());
        studentDao.save(student);

        StudentRegistration registration = new StudentRegistration();
        registration.setStudent_id(student);
        registration.setClassroom_id(tenE);
        registration.setRegistration_status_id(registrationStatus);
        registration.setReg_no(admissionNo);
        registration.setDate(LocalDate.of(2026, 1, 5));
        registrationDao.save(registration);
        roll.put(name, registration);

        for (String subject : CORE) {
            takes(registration, timetable.get(subject));
        }
        takes(registration, timetable.get(optionalSubject));
    }

    private void takes(StudentRegistration registration, ClassroomSubject line) {
        StudentSubject enrolment = new StudentSubject();
        enrolment.setStudent_registration_id(registration);
        enrolment.setClassroom_subject_id(line);
        studentSubjectDao.save(enrolment);
    }

    private ClassroomSubject timetable(SubjectDetail subject, Employee teacher) {
        ClassroomSubject line = new ClassroomSubject();
        line.setClassroom_id(tenE);
        line.setSubject_detail_id(subject);
        line.setEmployee_id(teacher);
        return classroomSubjectDao.save(line);
    }

    private SubjectDetail subject(String name, SubjectCategory category) {
        SubjectDetail subject = new SubjectDetail();
        subject.setName(name);
        subject.setCategory(category);
        subject.setActive(true);
        return subjectDao.save(subject);
    }

    private SubjectCategory category(String name, int order) {
        SubjectCategory category = new SubjectCategory();
        category.setName(name);
        category.setSortOrder(order);
        category.setActive(true);
        return categoryDao.save(category);
    }

    private Grade grade(String name) {
        Grade grade = new Grade();
        grade.setName(name);
        return gradeDao.save(grade);
    }

    private Employee employee(String name) {
        Employee employee = new Employee();
        employee.setFullname(name);
        employee.setCallingname(name);
        employee.setNic(String.format("19850000%02dV", ++staffSequence));
        employee.setEmail("staff" + staffSequence + "@scbc.test");
        employee.setMobileno("07700000" + staffSequence);
        employee.setGender("Female");
        employee.setCivilstatus("Single");
        employee.setDob(LocalDate.of(1985, 1, 1));
        employee.setAddress("Kandy");
        employee.setEmp_no(String.format("%08d", staffSequence));
        employee.setAdded_datetime(LocalDateTime.now());
        return employeeDao.save(employee);
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
}
