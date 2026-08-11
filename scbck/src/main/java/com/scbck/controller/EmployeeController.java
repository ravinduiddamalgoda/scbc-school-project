package com.scbck.controller;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Sort;
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
import com.scbck.exception.ApiException;
import com.scbck.model.Employee;
import com.scbck.model.Role;
import com.scbck.model.User;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StatusDao;
import com.scbck.repository.UserDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Employee (staff) CRUD.
 *
 * Resource-style URLs and real HTTP status codes replace the previous
 * "return a sentence and always answer 200" protocol.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    /** Status id representing a soft-deleted record in the status table. */
    private static final int STATUS_DELETED = 3;

    private final EmployeeDao employeeDao;
    private final StatusDao statusDao;
    private final UserDao userDao;
    private final RoleDao roleDao;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PrivilegeService privilegeService;

    public EmployeeController(EmployeeDao employeeDao, StatusDao statusDao, UserDao userDao, RoleDao roleDao,
            BCryptPasswordEncoder passwordEncoder, PrivilegeService privilegeService) {
        this.employeeDao = employeeDao;
        this.statusDao = statusDao;
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.passwordEncoder = passwordEncoder;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<Employee> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_EMPLOYEE);
        return employeeDao.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Employee findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_EMPLOYEE);
        return employeeDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Employee " + id + " does not exist."));
    }

    /** Staff records that do not yet have a login, used by the User form. */
    @GetMapping("/without-account")
    public List<Employee> listWithoutAccount() {
        privilegeService.requireSelect(PrivilegeService.MODULE_EMPLOYEE);
        return employeeDao.listUsersWithoutAccount();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Employee> create(@Valid @RequestBody Employee employee) {
        privilegeService.requireInsert(PrivilegeService.MODULE_EMPLOYEE);

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        assertNoDuplicates(employee, null);

        // Server-owned fields: never trust the client for these.
        employee.setId(null);
        employee.setAdded_datetime(LocalDateTime.now());
        employee.setAdded_user_id(currentUser == null ? null : currentUser.getId());
        employee.setUpdated_datetime(null);
        employee.setUpdated_user_id(null);
        employee.setDeletd_datetime(null);
        employee.setDeleted_user_id(null);
        employee.setEmp_no(employeeDao.getNextEmpNo());

        Employee saved = employeeDao.save(employee);

        createLoginIfDesignationRequiresOne(saved);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public Employee update(@PathVariable Integer id, @Valid @RequestBody Employee employee) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_EMPLOYEE);

        Employee existing = employeeDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Employee " + id + " does not exist."));

        assertNoDuplicates(employee, id);

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        // Copy only client-owned fields onto the managed entity, so audit columns
        // and the staff number survive the update.
        existing.setFullname(employee.getFullname());
        existing.setCallingname(employee.getCallingname());
        existing.setNic(employee.getNic());
        existing.setGender(employee.getGender());
        existing.setDob(employee.getDob());
        existing.setEmail(employee.getEmail());
        existing.setCivilstatus(employee.getCivilstatus());
        existing.setMobileno(employee.getMobileno());
        existing.setLandno(employee.getLandno());
        existing.setAddress(employee.getAddress());
        existing.setNote(employee.getNote());
        existing.setDesignation_id(employee.getDesignation_id());
        existing.setStatus_id(employee.getStatus_id());
        if (employee.getEmp_photo() != null) {
            existing.setEmp_photo(employee.getEmp_photo());
        }

        existing.setUpdated_datetime(LocalDateTime.now());
        existing.setUpdated_user_id(currentUser == null ? null : currentUser.getId());

        return employeeDao.save(existing);
    }

    /**
     * Soft delete: flips the record to the "Deleted" status and stamps the audit
     * columns.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_EMPLOYEE);

        Employee existing = employeeDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Employee " + id + " does not exist."));

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        existing.setDeletd_datetime(LocalDateTime.now());
        existing.setDeleted_user_id(currentUser == null ? null : currentUser.getId());
        existing.setStatus_id(statusDao.findById(STATUS_DELETED)
                .orElseThrow(() -> ApiException.badRequest("The 'Deleted' status row is missing from the database.")));

        employeeDao.save(existing);

        return MessageResponse.of("Employee " + existing.getEmp_no() + " deleted.");
    }

    // -------------------------------------------------------------------------

    /**
     * Rejects NIC, email and mobile collisions.
     *
     * The previous version dereferenced the NIC lookup result while checking
     * email and mobile, so changing an employee's NIC threw a
     * NullPointerException, and it compared Integer ids with != which silently
     * failed for ids above 127.
     */
    private void assertNoDuplicates(Employee candidate, Integer selfId) {
        Employee byNic = employeeDao.getByNIC(candidate.getNic());
        if (byNic != null && !Objects.equals(byNic.getId(), selfId)) {
            throw ApiException.conflict("The NIC " + candidate.getNic() + " already belongs to another employee.");
        }

        Employee byEmail = employeeDao.getByEmail(candidate.getEmail());
        if (byEmail != null && !Objects.equals(byEmail.getId(), selfId)) {
            throw ApiException.conflict("The email " + candidate.getEmail() + " already belongs to another employee.");
        }

        Employee byMobile = employeeDao.getByMobile(candidate.getMobileno());
        if (byMobile != null && !Objects.equals(byMobile.getId(), selfId)) {
            throw ApiException
                    .conflict("The mobile number " + candidate.getMobileno()
                            + " already belongs to another employee.");
        }
    }

    /**
     * Designations flagged with user_account get a login provisioned
     * automatically, seeded with the employee's NIC as the initial password.
     */
    private void createLoginIfDesignationRequiresOne(Employee employee) {
        if (employee.getDesignation_id() == null
                || !Boolean.TRUE.equals(employee.getDesignation_id().getUser_account())) {
            return;
        }

        Integer roleId = employee.getDesignation_id().getRole_id();
        if (roleId == null) {
            throw ApiException.badRequest("The designation is set to create a login but has no role assigned.");
        }

        Role role = roleDao.findById(roleId)
                .orElseThrow(() -> ApiException.badRequest("Role " + roleId + " does not exist."));

        User user = new User();
        user.setUsername(employee.getEmp_no());
        user.setUseremail(employee.getEmail());
        user.setStatus(true);
        user.setAdded_datetime(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(employee.getNic()));
        user.setEmployee_id(employee);
        if (employee.getEmp_photo() != null) {
            user.setUserphoto(employee.getEmp_photo());
        }

        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        userDao.save(user);
    }
}
