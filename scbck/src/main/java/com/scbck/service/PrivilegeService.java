package com.scbck.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.scbck.dto.ModulePrivilege;
import com.scbck.exception.ApiException;
import com.scbck.model.Module;
import com.scbck.repository.ModuleDao;
import com.scbck.repository.PrivilageDao;

/**
 * Central authority for "may this user do X to module Y".
 *
 * This replaces the old UserPrivilageController, which was a @Controller used
 * as a helper and parsed the privilege string without any null guards - a user
 * with no privilege rows at all caused a NullPointerException instead of a
 * clean 403.
 */
@Service
public class PrivilegeService {

    /** Modules the UI knows about, used to build the privilege matrix. */
    public static final String MODULE_EMPLOYEE = "Employee";
    public static final String MODULE_STUDENT = "Student";
    public static final String MODULE_GUARDIAN = "Guardian";
    public static final String MODULE_USER = "User";
    public static final String MODULE_PRIVILEGE = "Privilage";
    public static final String MODULE_SUBJECT = "Subject";
    public static final String MODULE_CLASS = "Class";
    public static final String MODULE_REPORT = "Report";
    public static final String MODULE_ATTENDANCE = "Attendance";
    public static final String MODULE_PAYMENT = "Payment";

    private final PrivilageDao privilageDao;
    private final ModuleDao moduleDao;

    public PrivilegeService(PrivilageDao privilageDao, ModuleDao moduleDao) {
        this.privilageDao = privilageDao;
        this.moduleDao = moduleDao;
    }

    /** Username of the caller on the current request. */
    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw ApiException.forbidden("No authenticated user on this request.");
        }
        return auth.getName();
    }

    /**
     * Resolves the caller's privileges for a single module.
     *
     * The built-in "Admin" account bypasses the table and holds every right, so
     * the system can never lock itself out of privilege administration.
     */
    public ModulePrivilege privilegesFor(String username, String moduleName) {
        if ("admin".equalsIgnoreCase(username)) {
            return ModulePrivilege.all();
        }

        String raw = privilageDao.getUserPrivilageByUserModule(username, moduleName);

        // No matching privilege row at all: the user simply has no access.
        if (raw == null || raw.isBlank()) {
            return ModulePrivilege.none();
        }

        String[] flags = raw.split(",");
        if (flags.length < 4) {
            return ModulePrivilege.none();
        }

        return new ModulePrivilege(
                isSet(flags[0]),
                isSet(flags[1]),
                isSet(flags[2]),
                isSet(flags[3]));
    }

    /** Convenience overload for the caller on the current request. */
    public ModulePrivilege privilegesFor(String moduleName) {
        return privilegesFor(currentUsername(), moduleName);
    }

    /**
     * The complete matrix for one user, keyed by module name. Every module known
     * to the database is present, so the client can rely on lookups never
     * returning undefined.
     */
    public Map<String, ModulePrivilege> privilegeMatrix(String username) {
        Map<String, ModulePrivilege> matrix = new LinkedHashMap<>();

        List<Module> modules = moduleDao.findAll();
        for (Module module : modules) {
            matrix.put(module.getName(), privilegesFor(username, module.getName()));
        }

        // Guarantee the modules the SPA routes on are always present, even before
        // the corresponding rows have been seeded into the module table.
        for (String known : List.of(MODULE_EMPLOYEE, MODULE_STUDENT, MODULE_GUARDIAN,
                MODULE_USER, MODULE_PRIVILEGE, MODULE_SUBJECT, MODULE_CLASS, MODULE_REPORT,
                MODULE_ATTENDANCE, MODULE_PAYMENT)) {
            matrix.putIfAbsent(known, privilegesFor(username, known));
        }

        return matrix;
    }

    // ---- Assertions used by controllers -------------------------------------

    public void requireSelect(String moduleName) {
        if (!privilegesFor(moduleName).select()) {
            throw ApiException.forbidden("You do not have permission to view " + moduleName + " records.");
        }
    }

    public void requireInsert(String moduleName) {
        if (!privilegesFor(moduleName).insert()) {
            throw ApiException.forbidden("You do not have permission to create " + moduleName + " records.");
        }
    }

    public void requireUpdate(String moduleName) {
        if (!privilegesFor(moduleName).update()) {
            throw ApiException.forbidden("You do not have permission to modify " + moduleName + " records.");
        }
    }

    public void requireDelete(String moduleName) {
        if (!privilegesFor(moduleName).delete()) {
            throw ApiException.forbidden("You do not have permission to delete " + moduleName + " records.");
        }
    }

    /**
     * Marks are entered by role rather than through the privilege matrix.
     *
     * The school's rule is that any teacher may enter marks for any class, not
     * only the class teacher - papers are marked by whoever taught the subject,
     * and at the end of term that is rarely one person per class. Expressing
     * that as a matrix module would mean granting every teacher rights they
     * already have by being a teacher, so the check is on the role itself and
     * {@code StudentMark.updated_user_id} carries the accountability instead.
     */
    public void requireMarkEntry() {
        if (!hasAnyRole(MARK_ENTRY_ROLES)) {
            throw ApiException.forbidden(
                    "Only teaching staff may view or enter marks.");
        }
    }

    /** Roles that may enter marks. */
    private static final List<String> MARK_ENTRY_ROLES = List.of("Admin", "Principal", "Teacher");

    /** True when the caller holds any of the named roles, ignoring case. */
    public boolean hasAnyRole(List<String> roleNames) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // The built-in Admin account holds every right even before any role row
        // exists, for the same reason it bypasses the privilege table.
        if ("admin".equalsIgnoreCase(auth.getName())) {
            return true;
        }

        return auth.getAuthorities().stream()
                .anyMatch(granted -> roleNames.stream()
                        .anyMatch(role -> role.equalsIgnoreCase(granted.getAuthority())));
    }

    /**
     * The native query returns MySQL BIT_OR results, which arrive as "1"/"0"
     * or as "true"/"false" depending on driver settings. Both are accepted.
     */
    private boolean isSet(String flag) {
        String value = flag == null ? "" : flag.trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
