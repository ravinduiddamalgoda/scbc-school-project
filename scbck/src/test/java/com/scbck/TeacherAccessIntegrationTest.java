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
import com.scbck.model.Classroom;
import com.scbck.model.Grade;
import com.scbck.model.Role;
import com.scbck.model.Term;
import com.scbck.model.User;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.TermDao;
import com.scbck.repository.UserDao;

/**
 * Cover for what a freshly created teacher account can actually do.
 *
 * The school made one and found the attendance register unusable: the class
 * dropdown was empty and read "No classes in this year". Nothing was wrong with
 * the classes. The list came from an endpoint gated on the Class module, which
 * a teacher has no reason to hold, and the browser rendered the resulting 403
 * as an absence of data.
 *
 * A teacher needs to pick a class and a term to do the two things the school
 * explicitly wants teachers doing - marking attendance and entering marks - so
 * those three lists are reference data to them, not Class administration. This
 * fixes the account in front of the school rather than asking them to grant a
 * privilege that would also let every teacher delete classes.
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
    private static final String FIXTURE_YEAR = "2097";

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

    private AcademicYear year;

    @BeforeEach
    void seed() {
        Grade grade = new Grade();
        grade.setName("Grade 7");
        gradeDao.save(grade);

        year = new AcademicYear();
        year.setName(FIXTURE_YEAR);
        year.setCurrent_year(false);
        year.setStart_date(LocalDate.of(2097, 1, 5));
        year.setEnd_date(LocalDate.of(2097, 12, 11));
        academicYearDao.save(year);

        Term term = new Term();
        term.setName("First Term");
        term.setStart_date(LocalDate.of(2097, 1, 5));
        term.setEnd_date(LocalDate.of(2097, 4, 3));
        term.setAcademic_year_id(year);
        termDao.save(term);

        Classroom classroom = new Classroom();
        classroom.setName("A");
        classroom.setGrade_id(grade);
        classroom.setAcademic_year_id(year);
        classroom.setMedium("Sinhala");
        classroomDao.save(classroom);

        // Deliberately no privilege rows at all. This is what a teacher account
        // looks like the moment it is created, before anybody has been through
        // the permission matrix - and it is the state the school reported from.
        teacherAccount("pasidu");
    }

    @Test
    @DisplayName("A teacher with no privilege rows can still list classes to mark")
    void teacherCanSeeTheClassPicker() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(get("/api/classes")
                .param("academicYearId", String.valueOf(year.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("A"));
    }

    @Test
    @DisplayName("A teacher can also see the terms and holidays those screens need")
    void teacherCanSeeTermsAndHolidays() throws Exception {
        MockHttpSession session = signIn("pasidu");

        mockMvc.perform(get("/api/terms")
                .param("academicYearId", String.valueOf(year.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/holidays")
                .param("academicYearId", String.valueOf(year.getId()))
                .session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reading the class list does not let a teacher delete a class")
    void readingIsNotManaging() throws Exception {
        MockHttpSession session = signIn("pasidu");

        Integer classroomId = classroomDao.listByAcademicYear(year.getId()).get(0).getId();

        // The relaxation is on reading only. Managing classes still needs the
        // Class module, which this account does not have.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/classes/" + classroomId)
                .session(session)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------

    private void teacherAccount(String username) {
        Role teacher = roleDao.findByName("Teacher").orElseGet(() -> {
            Role role = new Role();
            role.setName("Teacher");
            return roleDao.save(role);
        });

        User account = new User();
        account.setUsername(username);
        account.setUseremail(username + "@scbc.test");
        account.setStatus(true);
        account.setAdded_datetime(LocalDateTime.now());
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRoles(Set.of(teacher));
        userDao.save(account);
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
