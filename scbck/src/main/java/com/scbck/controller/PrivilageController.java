package com.scbck.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.scbck.model.Privilage;
import com.scbck.repository.PrivilageDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Role-to-module permission matrix administration.
 */
@RestController
@RequestMapping("/api/privileges")
public class PrivilageController {

    private final PrivilageDao privilageDao;
    private final PrivilegeService privilegeService;

    public PrivilageController(PrivilageDao privilageDao, PrivilegeService privilegeService) {
        this.privilageDao = privilageDao;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<Privilage> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_PRIVILEGE);
        return privilageDao.findAll(Sort.by(Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Privilage findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_PRIVILEGE);
        return privilageDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Privilege " + id + " does not exist."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Privilage> create(@Valid @RequestBody Privilage privilage) {
        privilegeService.requireInsert(PrivilegeService.MODULE_PRIVILEGE);

        assertRoleAndModulePresent(privilage);
        assertNoDuplicate(privilage, null);

        privilage.setId(null);

        return ResponseEntity.status(HttpStatus.CREATED).body(privilageDao.save(privilage));
    }

    @PutMapping("/{id}")
    @Transactional
    public Privilage update(@PathVariable Integer id, @Valid @RequestBody Privilage privilage) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_PRIVILEGE);

        Privilage existing = privilageDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Privilege " + id + " does not exist."));

        assertRoleAndModulePresent(privilage);
        assertNoDuplicate(privilage, id);

        existing.setRole_id(privilage.getRole_id());
        existing.setModule_id(privilage.getModule_id());
        existing.setPrivilage_select(privilage.getPrivilage_select());
        existing.setPrivilage_insert(privilage.getPrivilage_insert());
        existing.setPrivilage_update(privilage.getPrivilage_update());
        existing.setPrivilage_delete(privilage.getPrivilage_delete());

        return privilageDao.save(existing);
    }

    /**
     * Soft delete: clears all four flags, which revokes the permission without
     * removing the role/module pairing from the matrix.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_PRIVILEGE);

        Privilage existing = privilageDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Privilege " + id + " does not exist."));

        existing.setPrivilage_select(false);
        existing.setPrivilage_insert(false);
        existing.setPrivilage_update(false);
        existing.setPrivilage_delete(false);

        privilageDao.save(existing);

        return MessageResponse.of("All permissions revoked for this role and module.");
    }

    // -------------------------------------------------------------------------

    private void assertRoleAndModulePresent(Privilage privilage) {
        if (privilage.getRole_id() == null || privilage.getRole_id().getId() == null) {
            throw ApiException.badRequest("Select a role.");
        }
        if (privilage.getModule_id() == null || privilage.getModule_id().getId() == null) {
            throw ApiException.badRequest("Select a module.");
        }
    }

    private void assertNoDuplicate(Privilage candidate, Integer selfId) {
        Privilage existing = privilageDao.getPrivilageRoleModule(
                candidate.getRole_id().getId(),
                candidate.getModule_id().getId());

        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw ApiException.conflict("This role already has a permission entry for that module.");
        }
    }
}
