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

import com.scbck.model.Guardian;
import com.scbck.model.Role;
import com.scbck.model.Student;
import com.scbck.model.StudentStatus;
import com.scbck.model.User;
import com.scbck.repository.GuardianDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.UserDao;
import com.scbck.service.PrivilegeService;

/**
 * Cover for the one thing the parent portal has to get right: a parent sees
 * their own children and nobody else's.
 *
 * Every other module answers "may this user read Students?" - a question about
 * a whole table. A parent's answer is "yes, for these rows", which no privilege
 * module can express, so it is enforced structurally: the child list is derived
 * from the guardian on the caller's own account, and a student id in the URL is
 * checked against that list rather than trusted.
 *
 * The test that matters is therefore the hostile one - a parent asking for the
 * neighbour's child by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParentPortalIntegrationTest {

    private static final String PASSWORD = "ParentPass123";

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
    private GuardianDao guardianDao;

    private Student ownChild;
    private Student otherChild;

    @BeforeEach
    void seed() {
        StudentStatus active = new StudentStatus();
        active.setName("Active");
        studentStatusDao.save(active);

        Guardian perera = guardian("Mr Perera", "811111111V", "0771111111", "perera@example.test");
        Guardian silva = guardian("Mrs Silva", "822222222V", "0772222222", "silva@example.test");

        ownChild = student("Kavindu Perera", "4001", "BC4001", active, perera);
        student("Sanduni Perera", "4002", "BC4002", active, perera);
        otherChild = student("Nethmi Silva", "4003", "BC4003", active, silva);

        parentAccount("perera.parent", "perera.login@example.test", perera);
        staffAccount("clerk1", "clerk1@example.test");
    }

    @Test
    @DisplayName("A parent sees exactly the children registered under their guardian record")
    void parentSeesOnlyTheirOwnChildren() throws Exception {
        MockHttpSession session = signIn("perera.parent");

        mockMvc.perform(get("/api/parent/children").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].admissionNo")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("4001", "4002")));
    }

    @Test
    @DisplayName("Asking for another family's child by id is refused, not answered")
    void anotherFamilysChildIsRefused() throws Exception {
        MockHttpSession session = signIn("perera.parent");

        // The id is real and the child exists; the only thing standing between
        // the caller and somebody else's record is this check.
        mockMvc.perform(get("/api/parent/children/" + otherChild.getId() + "/terms").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/parent/children/" + otherChild.getId() + "/attendance")
                .param("from", LocalDate.of(2026, 1, 5).toString())
                .param("to", LocalDate.of(2026, 3, 5).toString())
                .session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/parent/children/" + otherChild.getId() + "/payments").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A parent may reach their own child's records")
    void ownChildIsReachable() throws Exception {
        MockHttpSession session = signIn("perera.parent");

        mockMvc.perform(get("/api/parent/children/" + ownChild.getId() + "/terms").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/parent/children/" + ownChild.getId() + "/payments").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A staff account is turned away from the portal rather than shown everything")
    void staffAccountsAreNotParents() throws Exception {
        MockHttpSession session = signIn("clerk1");

        // The portal is a different view of the school, not a reduced one. An
        // account with no guardian behind it has no children to show, and
        // saying so beats falling through to "all of them".
        mockMvc.perform(get("/api/parent/children").session(session))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------

    private Guardian guardian(String name, String nic, String mobile, String email) {
        Guardian guardian = new Guardian();
        guardian.setFullname(name);
        guardian.setEmail(email);
        guardian.setMobile(mobile);
        guardian.setNic(nic);
        guardian.setRelationship("Father");
        guardian.setAddress("Kandy");
        guardian.setGuardian_no(mobile.substring(4));
        return guardianDao.save(guardian);
    }

    private Student student(String name, String admissionNo, String birthCertNo,
            StudentStatus status, Guardian guardian) {
        Student student = new Student();
        student.setFullname(name);
        student.setCallingname(name.split(" ")[0]);
        student.setStu_no(admissionNo);
        student.setBirth_certi_no(birthCertNo);
        student.setDob(LocalDate.of(2011, 6, 1));
        student.setGender("Female");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("Kandy");
        student.setStudent_status_id(status);
        student.setGuardian_id(guardian);
        student.setAdded_datetime(LocalDateTime.now());
        return studentDao.save(student);
    }

    private void parentAccount(String username, String email, Guardian guardian) {
        Role parentRole = roleDao.findByName(PrivilegeService.ROLE_PARENT).orElseGet(() -> {
            Role role = new Role();
            role.setName(PrivilegeService.ROLE_PARENT);
            return roleDao.save(role);
        });

        User account = new User();
        account.setUsername(username);
        account.setUseremail(email);
        account.setStatus(true);
        account.setAdded_datetime(LocalDateTime.now());
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRoles(Set.of(parentRole));
        account.setGuardian_id(guardian);
        userDao.save(account);
    }

    private void staffAccount(String username, String email) {
        Role clerkRole = roleDao.findByName("Clerk").orElseGet(() -> {
            Role role = new Role();
            role.setName("Clerk");
            return roleDao.save(role);
        });

        User account = new User();
        account.setUsername(username);
        account.setUseremail(email);
        account.setStatus(true);
        account.setAdded_datetime(LocalDateTime.now());
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRoles(Set.of(clerkRole));
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
