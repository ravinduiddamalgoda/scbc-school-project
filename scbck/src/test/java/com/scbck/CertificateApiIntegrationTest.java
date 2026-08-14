package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.scbck.model.Role;
import com.scbck.model.Student;
import com.scbck.model.StudentStatus;
import com.scbck.model.User;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.UserDao;

/**
 * Cover for the two certificates: drafting from the record, issuing, and
 * reprinting what was issued.
 *
 * The reprint assertion is the one that matters. A certificate is a document a
 * family may present years later, so it has to be reproducible from what was
 * signed - not regenerated from a student record that has changed since.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CertificateApiIntegrationTest {

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
    private StudentDao studentDao;
    @Autowired
    private StudentStatusDao studentStatusDao;

    private Student student;

    @BeforeEach
    void seed() {
        seedAdminAccount();

        StudentStatus active = new StudentStatus();
        active.setName("Active");
        studentStatusDao.save(active);

        student = new Student();
        student.setFullname("Nadun Wijesekara");
        student.setCallingname("N. Wijesekara");
        student.setStu_no("3501");
        student.setBirth_certi_no("BC3501");
        student.setDob(LocalDate.of(2009, 3, 14));
        student.setGender("Male");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("12 Temple Road, Kandy");
        student.setStudent_status_id(active);
        student.setAdded_datetime(LocalDateTime.now());
        studentDao.save(student);
    }

    @Test
    @DisplayName("A leaving certificate draft is filled in from the student record")
    void leavingDraftIsPrefilled() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/certificates/draft")
                .param("studentId", String.valueOf(student.getId()))
                .param("type", "LEAVING")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LEAVING"))
                .andExpect(jsonPath("$.studentName").value("Nadun Wijesekara"))
                .andExpect(jsonPath("$.nameWithInitials").value("N. Wijesekara"))
                .andExpect(jsonPath("$.admissionNo").value("3501"))
                .andExpect(jsonPath("$.religion").value("Buddhism"))
                // No guardian on this record, so the student's own address fills
                // the line rather than leaving the form incomplete.
                .andExpect(jsonPath("$.guardianAddress").value("12 Temple Road, Kandy"));
    }

    @Test
    @DisplayName("A character certificate draft uses the pronouns for the recorded gender")
    void characterDraftUsesRecordedPronouns() throws Exception {
        MockHttpSession session = signIn();

        MvcResult male = mockMvc.perform(draft("CHARACTER", session))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(male.getResponse().getContentAsString()).contains("He studied in the");

        // A record with no gender must not be guessed at: they/them reads
        // correctly and cannot misgender anyone.
        student.setGender(null);
        studentDao.save(student);

        MvcResult unknown = mockMvc.perform(draft("CHARACTER", session))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unknown.getResponse().getContentAsString()).contains("They studied in the");
    }

    @Test
    @DisplayName("An issued certificate reprints as it was issued, not as the record now reads")
    void reprintUsesTheIssuedText() throws Exception {
        MockHttpSession session = signIn();

        MvcResult issued = mockMvc.perform(post("/api/certificates")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "student_id": { "id": %d },
                          "type": "LEAVING",
                          "issued_date": "2026-08-14",
                          "studentName": "Nadun Wijesekara",
                          "admissionNo": "3501",
                          "reasonForLeaving": "Family relocating to Colombo",
                          "conduct": "Excellent"
                        }
                        """.formatted(student.getId()))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reasonForLeaving").value("Family relocating to Colombo"))
                .andReturn();

        Integer certificateId = com.jayway.jsonpath.JsonPath.read(
                issued.getResponse().getContentAsString(), "$.id");

        // The record changes after the certificate was handed over.
        student.setFullname("Nadun Perera Wijesekara");
        student.setStu_no("9999");
        studentDao.save(student);

        MvcResult reprint = mockMvc.perform(get("/api/certificates/" + certificateId + "/pdf")
                .session(session))
                .andExpect(status().isOk())
                .andReturn();

        byte[] pdf = reprint.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        // The filename still carries the name as issued.
        assertThat(reprint.getResponse().getHeader("Content-Disposition"))
                .contains("Leaving Certificate Nadun Wijesekara.pdf");

        mockMvc.perform(get("/api/certificates")
                .param("studentId", String.valueOf(student.getId()))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentName").value("Nadun Wijesekara"))
                .andExpect(jsonPath("$[0].admissionNo").value("3501"));
    }

    @Test
    @DisplayName("An unknown certificate type is refused")
    void unknownTypeIsRejected() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(draft("TRANSCRIPT", session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("LEAVING")));
    }

    // -------------------------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder draft(String type, MockHttpSession session) {
        return get("/api/certificates/draft")
                .param("studentId", String.valueOf(student.getId()))
                .param("type", type)
                .session(session);
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
