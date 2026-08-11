package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.scbck.model.User;
import com.scbck.repository.RoleDao;
import com.scbck.repository.UserDao;

/**
 * Exercises the authentication and authorisation surface the React client
 * depends on: JSON login, session persistence, the privilege matrix, CSRF
 * enforcement, and the guarantee that no response ever carries a password hash.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void seedAdminAccount() {
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

    /** Logs in and returns the authenticated session for reuse. */
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

    @Test
    @DisplayName("Protected endpoints answer 401, not a redirect, when anonymous")
    void anonymousRequestIsRejectedWithJsonStatus() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login returns the user with a full privilege matrix")
    void loginReturnsUserAndPrivileges() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Admin"))
                .andExpect(jsonPath("$.email").value("admin@scbc.test"))
                .andExpect(jsonPath("$.roles[0]").value("Admin"))
                // Admin bypasses the privilege table and holds every right.
                .andExpect(jsonPath("$.privileges.Employee.select").value(true))
                .andExpect(jsonPath("$.privileges.Student.insert").value(true))
                .andExpect(jsonPath("$.privileges.Guardian.update").value(true))
                .andExpect(jsonPath("$.privileges.User.delete").value(true));
    }

    @Test
    @DisplayName("Wrong credentials answer 401 without revealing which field was wrong")
    void badCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Admin\",\"password\":\"not-the-password\"}")
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    @DisplayName("The session survives across requests")
    void sessionIsReusedAcrossRequests() throws Exception {
        MockHttpSession session = signIn();
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Admin"));

        mockMvc.perform(get("/api/employees").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A mutating request without a CSRF token is rejected")
    void mutatingRequestWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = signIn();

        // Same call as the passing test, minus .with(csrf()).
        mockMvc.perform(put("/api/auth/profile")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Admin\",\"email\":\"admin@scbc.test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No response ever carries the password hash")
    void passwordHashIsNeverSerialised() throws Exception {
        MockHttpSession session = signIn();

        String usersBody = mockMvc.perform(get("/api/users").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String meBody = mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // BCrypt hashes all start with $2; their presence would mean a leak.
        assertThat(usersBody).doesNotContain("password").doesNotContain("$2a$").doesNotContain("$2b$");
        assertThat(meBody).doesNotContain("password").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    @DisplayName("Logout clears the session")
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A profile password change requires the current password")
    void profilePasswordChangeRequiresCurrentPassword() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(put("/api/auth/profile")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "Admin",
                          "email": "admin@scbc.test",
                          "oldPassword": "wrong-password",
                          "newPassword": "SomethingElse1"
                        }
                        """)
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your current password is incorrect."));
    }

    @Test
    @DisplayName("The Admin bootstrap endpoint refuses to run twice")
    void adminBootstrapIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/auth/createadmin").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The Admin account already exists."));
    }

    @Test
    @DisplayName("Lookup data is readable once signed in")
    void lookupsAreAvailableToAuthenticatedUsers() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/lookups/roles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Admin"));

        mockMvc.perform(get("/api/lookups/grades").session(session))
                .andExpect(status().isOk());
    }
}
