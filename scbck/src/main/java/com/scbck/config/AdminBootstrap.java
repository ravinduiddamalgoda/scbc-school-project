package com.scbck.config;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.Role;
import com.scbck.model.User;
import com.scbck.repository.RoleDao;
import com.scbck.repository.UserDao;

/**
 * Creates the built-in Admin account at start-up when an initial password has
 * been supplied and the account does not exist yet.
 *
 * This exists because the equivalent HTTP endpoint, POST /api/auth/createadmin,
 * is easy to get wrong in exactly the way that leaves an operator locked out of
 * a fresh install:
 *
 *   - it is reachable while logged out, but CSRF still applies to it, so a
 *     plain "curl -X POST" is rejected with a 403 whose body says nothing about
 *     tokens;
 *   - the password is read once at start-up, so exporting the variable in a
 *     second terminal after the API is already running has no effect, and the
 *     endpoint then answers 400 instead.
 *
 * Setting the variable in the shell that starts the API needs neither, and it
 * is the same property either way. The endpoint is kept for the case where the
 * account has to be recreated without a restart.
 *
 * Every outcome is logged and none of them abort the boot: a school that has
 * not loaded the seed data yet should still get a running API and a message
 * telling it what is missing.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final String ADMIN_USERNAME = "Admin";
    private static final String ADMIN_EMAIL = "adminscbc@gmail.com";
    private static final String ADMIN_ROLE = "Admin";

    private final UserDao userDao;
    private final RoleDao roleDao;
    private final BCryptPasswordEncoder passwordEncoder;

    /** Same property the bootstrap endpoint uses; blank by default. */
    @Value("${scbc.admin.initial-password:}")
    private String initialPassword;

    public AdminBootstrap(UserDao userDao, RoleDao roleDao, BCryptPasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (initialPassword == null || initialPassword.isBlank()) {
            log.info("Admin bootstrap skipped: no initial password configured. "
                    + "Set SCBC_ADMIN_INITIAL_PASSWORD before starting the API to create the Admin account.");
            return;
        }

        if (userDao.getByUsername(ADMIN_USERNAME) != null) {
            log.info("Admin bootstrap skipped: the {} account already exists. "
                    + "Unset SCBC_ADMIN_INITIAL_PASSWORD.", ADMIN_USERNAME);
            return;
        }

        Optional<Role> adminRole = roleDao.findByName(ADMIN_ROLE);
        if (adminRole.isEmpty()) {
            log.warn("Admin bootstrap skipped: no '{}' row in the role table. "
                    + "Load scbck/seed/academic-seed.sql, then restart.", ADMIN_ROLE);
            return;
        }

        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setUseremail(ADMIN_EMAIL);
        admin.setStatus(true);
        admin.setAdded_datetime(LocalDateTime.now());
        admin.setPassword(passwordEncoder.encode(initialPassword));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole.get());
        admin.setRoles(roles);

        userDao.save(admin);

        log.info("Admin bootstrap: created the {} account. Sign in, change the password from My profile, "
                + "then unset SCBC_ADMIN_INITIAL_PASSWORD.", ADMIN_USERNAME);
    }
}
