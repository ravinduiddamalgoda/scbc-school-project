package com.scbck.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.MessageResponse;
import com.scbck.dto.UserRequest;
import com.scbck.exception.ApiException;
import com.scbck.model.Employee;
import com.scbck.model.Guardian;
import com.scbck.model.Role;
import com.scbck.model.User;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.GuardianDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.UserDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * User (login account) management.
 *
 * Requests bind to {@link UserRequest} rather than the entity, so the client
 * can never write the password hash, audit columns or id directly. Responses
 * carry the entity, whose password field is write-only and therefore omitted.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserDao userDao;
    private final RoleDao roleDao;
    private final EmployeeDao employeeDao;
    private final GuardianDao guardianDao;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PrivilegeService privilegeService;

    public UserController(UserDao userDao, RoleDao roleDao, EmployeeDao employeeDao,
            GuardianDao guardianDao, BCryptPasswordEncoder passwordEncoder,
            PrivilegeService privilegeService) {
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.employeeDao = employeeDao;
        this.guardianDao = guardianDao;
        this.passwordEncoder = passwordEncoder;
        this.privilegeService = privilegeService;
    }

    /** Every account except the caller's own and the built-in Admin. */
    @GetMapping
    public List<User> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_USER);
        return userDao.findAllExceptUsername(privilegeService.currentUsername());
    }

    @GetMapping("/{id}")
    public User findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_USER);
        return userDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("User " + id + " does not exist."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<User> create(@Valid @RequestBody UserRequest request) {
        privilegeService.requireInsert(PrivilegeService.MODULE_USER);

        if (request.password() == null || request.password().isBlank()) {
            throw ApiException.badRequest("A password is required when creating an account.");
        }

        assertNoDuplicates(request, null);

        User user = new User();
        apply(request, user);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAdded_datetime(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(userDao.save(user));
    }

    @PutMapping("/{id}")
    @Transactional
    public User update(@PathVariable Integer id, @Valid @RequestBody UserRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_USER);

        User existing = userDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("User " + id + " does not exist."));

        assertNoDuplicates(request, id);

        apply(request, existing);

        // A blank password means "leave the current one alone". The old version
        // saved whatever arrived in the body straight into the password column.
        if (request.password() != null && !request.password().isBlank()) {
            existing.setPassword(passwordEncoder.encode(request.password()));
        }

        existing.setUpdatedatetime(LocalDateTime.now());

        return userDao.save(existing);
    }

    /** Soft delete: deactivates the account so the login stops working. */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_USER);

        User existing = userDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("User " + id + " does not exist."));

        if ("Admin".equalsIgnoreCase(existing.getUsername())) {
            throw ApiException.badRequest("The built-in Admin account cannot be deleted.");
        }

        if (existing.getUsername().equals(privilegeService.currentUsername())) {
            throw ApiException.badRequest("You cannot delete the account you are signed in with.");
        }

        existing.setStatus(false);
        existing.setDeleted_datetime(LocalDateTime.now());
        userDao.save(existing);

        return MessageResponse.of("User " + existing.getUsername() + " deactivated.");
    }

    // -------------------------------------------------------------------------

    /** Copies client-owned fields from the request onto the entity. */
    private void apply(UserRequest request, User user) {
        user.setUsername(request.username());
        user.setUseremail(request.useremail());
        user.setStatus(request.status());
        user.setNote(request.note());

        if (request.photo() != null) {
            user.setUserphoto(request.photo().isBlank()
                    ? null
                    : request.photo().getBytes(StandardCharsets.UTF_8));
        }

        if (request.employeeId() == null) {
            user.setEmployee_id(null);
        } else {
            Employee employee = employeeDao.findById(request.employeeId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Employee " + request.employeeId() + " does not exist."));
            user.setEmployee_id(employee);
        }

        if (request.guardianId() == null) {
            user.setGuardian_id(null);
        } else {
            Guardian guardian = guardianDao.findById(request.guardianId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Guardian " + request.guardianId() + " does not exist."));
            user.setGuardian_id(guardian);
        }

        // An account is either a member of staff or a parent. Both links at
        // once would make "whose records may this account see" unanswerable,
        // and the parent portal would hand a staff member the wrong children.
        if (user.getEmployee_id() != null && user.getGuardian_id() != null) {
            throw ApiException.badRequest(
                    "An account belongs either to a staff member or to a guardian, not both.");
        }

        Set<Role> roles = new HashSet<>();
        for (Integer roleId : request.roleIds()) {
            roles.add(roleDao.findById(roleId)
                    .orElseThrow(() -> ApiException.badRequest("Role " + roleId + " does not exist.")));
        }
        if (roles.isEmpty()) {
            throw ApiException.badRequest("Assign at least one role to the account.");
        }
        user.setRoles(roles);

        // A parent account with no guardian behind it can sign in and then find
        // the portal empty, with nothing on screen explaining why. Refusing it
        // here turns that into a message at the point the mistake is made.
        boolean isParent = roles.stream()
                .anyMatch(role -> PrivilegeService.ROLE_PARENT.equalsIgnoreCase(role.getName()));
        if (isParent && user.getGuardian_id() == null) {
            throw ApiException.badRequest(
                    "A Parent account must be linked to the guardian whose children it may see.");
        }
    }

    private void assertNoDuplicates(UserRequest request, Integer selfId) {
        User byUsername = userDao.getByUsername(request.username());
        if (byUsername != null && !Objects.equals(byUsername.getId(), selfId)) {
            throw ApiException.conflict("The username " + request.username() + " is already taken.");
        }

        User byEmail = userDao.getByUseremail(request.useremail());
        if (byEmail != null && !Objects.equals(byEmail.getId(), selfId)) {
            throw ApiException.conflict("The email " + request.useremail() + " is already registered.");
        }
    }
}
