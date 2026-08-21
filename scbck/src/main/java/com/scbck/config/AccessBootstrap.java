package com.scbck.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.Module;
import com.scbck.model.Role;
import com.scbck.repository.ModuleDao;
import com.scbck.repository.RoleDao;
import com.scbck.service.PrivilegeService;

/**
 * Creates the roles and privilege modules the newer screens are gated on.
 *
 * Without a {@code module} row the permission matrix has nothing to grant, so
 * a screen added since the last seed run is reachable only by the built-in
 * Admin account - which looks exactly like a broken permission check. Seeding
 * from code rather than only from {@code academic-seed.sql} means an existing
 * deployment picks the new screens up by restarting, instead of by remembering
 * to re-run a script.
 *
 * Additive and idempotent: a row is created only when none of that name exists,
 * and nothing is ever renamed or removed.
 */
@Component
@Order(10)
public class AccessBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccessBootstrap.class);

    /**
     * Privilege modules.
     *
     * "Achievement" gates conduct, health, leadership and co-curricular
     * records; "SBA" gates School Based Assessment marks and the Department's
     * workbooks. Both are separate from Student and Marks on purpose - see
     * {@link PrivilegeService} for why.
     */
    private static final List<String> MODULES = List.of(
            PrivilegeService.MODULE_ACHIEVEMENT,
            PrivilegeService.MODULE_SBA);

    /**
     * Roles.
     *
     * "Records Officer" is the role the school asked for: staff who keep a
     * student's conduct, health, leadership and talents up to date without
     * being able to touch the student's identity or their marks. It holds no
     * privileges of its own - the matrix decides what it may do, which is the
     * point of it being a role rather than a hard-coded set of rights.
     *
     * "Parent" is not staff at all. It is checked by name rather than through
     * the matrix, because a parent's access is "these three children" and no
     * module-level grant can express that.
     */
    private static final List<String> ROLES = List.of(
            "Records Officer",
            PrivilegeService.ROLE_PARENT);

    private final ModuleDao moduleDao;
    private final RoleDao roleDao;

    public AccessBootstrap(ModuleDao moduleDao, RoleDao roleDao) {
        this.moduleDao = moduleDao;
        this.roleDao = roleDao;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createMissingModules();
        createMissingRoles();
    }

    private void createMissingModules() {
        Set<String> existing = moduleDao.findAll().stream()
                .map(module -> key(module.getName()))
                .collect(Collectors.toSet());

        List<Module> created = new ArrayList<>();
        for (String name : MODULES) {
            if (existing.contains(key(name))) {
                continue;
            }
            Module module = new Module();
            module.setName(name);
            created.add(module);
        }

        if (!created.isEmpty()) {
            moduleDao.saveAll(created);
            log.info("Access: created privilege module(s) {}.",
                    created.stream().map(Module::getName).toList());
        }
    }

    private void createMissingRoles() {
        Set<String> existing = roleDao.findAll().stream()
                .map(role -> key(role.getName()))
                .collect(Collectors.toSet());

        List<Role> created = new ArrayList<>();
        for (String name : ROLES) {
            if (existing.contains(key(name))) {
                continue;
            }
            Role role = new Role();
            role.setName(name);
            created.add(role);
        }

        if (!created.isEmpty()) {
            roleDao.saveAll(created);
            log.info("Access: created role(s) {}.", created.stream().map(Role::getName).toList());
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
