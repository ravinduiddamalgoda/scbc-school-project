package com.scbck.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.AuthUserResponse;
import com.scbck.dto.ChangeProfileRequest;
import com.scbck.dto.LoginRequest;
import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.Role;
import com.scbck.model.User;
import com.scbck.repository.RoleDao;
import com.scbck.repository.UserDao;
import com.scbck.service.PrivilegeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Session lifecycle and profile endpoints for the React client.
 *
 * Replaces the view-returning LoginController: nothing here renders HTML or
 * redirects, so the client keeps full control of navigation.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDao userDao;
    private final RoleDao roleDao;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PrivilegeService privilegeService;

    /** Persists the authentication into the HTTP session after a manual login. */
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * Initial password for the bootstrap Admin account. Supplied by
     * configuration (SCBC_ADMIN_INITIAL_PASSWORD) rather than hard-coded, and
     * intentionally empty by default so the endpoint is inert until an operator
     * opts in.
     */
    @Value("${scbc.admin.initial-password:}")
    private String adminInitialPassword;

    public AuthController(AuthenticationManager authenticationManager, UserDao userDao, RoleDao roleDao,
            BCryptPasswordEncoder passwordEncoder, PrivilegeService privilegeService) {
        this.authenticationManager = authenticationManager;
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.passwordEncoder = passwordEncoder;
        this.privilegeService = privilegeService;
    }

    /**
     * Primes the XSRF-TOKEN cookie. The client calls this once on start-up so a
     * token is available before the first mutating request.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Authenticates a JSON credential pair and starts a session.
     *
     * Returns the same payload as /me so the client can render immediately
     * without a follow-up request.
     */
    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        // Spring Security 6 no longer saves the context implicitly for manual
        // authentication, so it is written to the session explicitly here.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return toAuthUser(userDao.getByUsername(authentication.getName()), authentication);
    }

    /**
     * The signed-in user plus the full privilege matrix. Called on every client
     * boot to restore session state after a page refresh.
     */
    @GetMapping("/me")
    public AuthUserResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userDao.getByUsername(authentication.getName());

        if (user == null) {
            throw ApiException.notFound("The signed-in account no longer exists.");
        }

        return toAuthUser(user, authentication);
    }

    /**
     * Updates the signed-in user's own username, email, photo and password.
     */
    @PutMapping("/profile")
    public MessageResponse updateProfile(@Valid @RequestBody ChangeProfileRequest request) {

        // The account being edited comes from the session, not the request body.
        String currentUsername = privilegeService.currentUsername();
        User user = userDao.getByUsername(currentUsername);

        if (user == null) {
            throw ApiException.notFound("The signed-in account no longer exists.");
        }

        User existingWithName = userDao.getByUsername(request.username());
        if (existingWithName != null && !existingWithName.getId().equals(user.getId())) {
            throw ApiException.conflict("The username " + request.username() + " is already taken.");
        }

        // A password change requires proof of the current password.
        boolean wantsPasswordChange = request.newPassword() != null && !request.newPassword().isBlank();
        if (wantsPasswordChange) {
            if (request.oldPassword() == null || request.oldPassword().isBlank()) {
                throw ApiException.badRequest("Enter your current password to set a new one.");
            }
            if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
                throw ApiException.badRequest("Your current password is incorrect.");
            }
            if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
                throw ApiException.badRequest("The new password must differ from the current one.");
            }
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }

        user.setUsername(request.username());
        user.setUseremail(request.email());
        if (request.photo() != null) {
            user.setUserphoto(request.photo().isBlank() ? null : request.photo().getBytes(StandardCharsets.UTF_8));
        }
        user.setUpdatedatetime(LocalDateTime.now());

        userDao.save(user);

        // Changing the username or password invalidates the current principal, so
        // the client is told to sign in again.
        boolean mustReauthenticate = wantsPasswordChange || !currentUsername.equals(request.username());
        return MessageResponse.of(mustReauthenticate
                ? "Profile updated. Please sign in again."
                : "Profile updated.");
    }

    /**
     * One-time bootstrap of the built-in Admin account on an empty database.
     *
     * Refuses to run once the account exists, and requires the initial password
     * to be supplied by configuration rather than hard-coded in source.
     *
     * Normally there is no need to call this: AdminBootstrap does the same work
     * at start-up from the same property. It is kept for the case where the
     * account has to be recreated without restarting the API. Note that CSRF
     * applies here even though the endpoint is reachable while logged out, so a
     * caller must fetch /api/auth/csrf first and echo the token back in
     * X-XSRF-TOKEN; a bare POST is answered with 403.
     */
    @PostMapping("/createadmin")
    public ResponseEntity<MessageResponse> createAdmin() {

        if (userDao.getByUsername("Admin") != null) {
            throw ApiException.conflict("The Admin account already exists.");
        }

        if (adminInitialPassword == null || adminInitialPassword.isBlank()) {
            throw ApiException.badRequest(
                    "Set scbc.admin.initial-password (or SCBC_ADMIN_INITIAL_PASSWORD) before bootstrapping Admin.");
        }

        // By name, not by id: a database seeded in a different order does not
        // necessarily give the Admin role id 1.
        Role adminRole = roleDao.findByName("Admin")
                .orElseThrow(() -> ApiException.badRequest("Seed the role table before creating the Admin account."));

        User admin = new User();
        admin.setUsername("Admin");
        admin.setUseremail("adminscbc@gmail.com");
        admin.setStatus(true);
        admin.setAdded_datetime(LocalDateTime.now());
        admin.setPassword(passwordEncoder.encode(adminInitialPassword));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);

        userDao.save(admin);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponse.of("Admin account created. Sign in and change the password immediately."));
    }

    // -------------------------------------------------------------------------

    private AuthUserResponse toAuthUser(User user, Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String photo = user.getUserphoto() == null || user.getUserphoto().length == 0
                ? null
                : new String(user.getUserphoto(), StandardCharsets.UTF_8);

        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getUseremail(),
                photo,
                roles,
                privilegeService.privilegeMatrix(user.getUsername()));
    }
}
