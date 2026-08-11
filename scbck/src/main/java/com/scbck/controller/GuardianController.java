package com.scbck.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
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
import com.scbck.model.Guardian;
import com.scbck.repository.GuardianDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Guardian CRUD.
 *
 * The guardian screen existed in the old UI but had no entity, repository or
 * controller behind it - the page loaded the student script and wrote nothing.
 * This is the missing server side, matching the guardian table in the ER model.
 *
 * Deletion is hard rather than soft because the guardian table carries no
 * status column; the endpoint refuses to remove a guardian that students still
 * reference.
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianController {

    private final GuardianDao guardianDao;
    private final PrivilegeService privilegeService;

    public GuardianController(GuardianDao guardianDao, PrivilegeService privilegeService) {
        this.guardianDao = guardianDao;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<Guardian> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_GUARDIAN);
        return guardianDao.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Guardian findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_GUARDIAN);
        return guardianDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Guardian " + id + " does not exist."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Guardian> create(@Valid @RequestBody Guardian guardian) {
        privilegeService.requireInsert(PrivilegeService.MODULE_GUARDIAN);

        assertNoDuplicates(guardian, null);

        guardian.setId(null);
        guardian.setGuardian_no(guardianDao.getNextGuardianNo());

        return ResponseEntity.status(HttpStatus.CREATED).body(guardianDao.save(guardian));
    }

    @PutMapping("/{id}")
    @Transactional
    public Guardian update(@PathVariable Integer id, @Valid @RequestBody Guardian guardian) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_GUARDIAN);

        Guardian existing = guardianDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Guardian " + id + " does not exist."));

        assertNoDuplicates(guardian, id);

        existing.setFullname(guardian.getFullname());
        existing.setNic(guardian.getNic());
        existing.setMobile(guardian.getMobile());
        existing.setEmail(guardian.getEmail());
        existing.setOccupation(guardian.getOccupation());
        existing.setEmployer(guardian.getEmployer());
        existing.setAddress(guardian.getAddress());
        existing.setRelationship(guardian.getRelationship());
        existing.setS_g_name(guardian.getS_g_name());
        existing.setS_g_mobile(guardian.getS_g_mobile());
        existing.setS_g_relationship(guardian.getS_g_relationship());
        existing.setS_g_address(guardian.getS_g_address());
        existing.setS_g_email(guardian.getS_g_email());

        return guardianDao.save(existing);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_GUARDIAN);

        Guardian existing = guardianDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Guardian " + id + " does not exist."));

        // The database rejects the delete if students still reference this row;
        // DataIntegrityViolationException is translated into a 409 by the handler.
        guardianDao.delete(existing);

        return MessageResponse.of("Guardian " + existing.getGuardian_no() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void assertNoDuplicates(Guardian candidate, Integer selfId) {
        Guardian byNic = guardianDao.getByNic(candidate.getNic());
        if (byNic != null && !Objects.equals(byNic.getId(), selfId)) {
            throw ApiException.conflict("The NIC " + candidate.getNic() + " already belongs to another guardian.");
        }

        Guardian byMobile = guardianDao.getByMobile(candidate.getMobile());
        if (byMobile != null && !Objects.equals(byMobile.getId(), selfId)) {
            throw ApiException.conflict(
                    "The mobile number " + candidate.getMobile() + " already belongs to another guardian.");
        }

        if (candidate.getEmail() != null && !candidate.getEmail().isBlank()) {
            Guardian byEmail = guardianDao.getByEmail(candidate.getEmail());
            if (byEmail != null && !Objects.equals(byEmail.getId(), selfId)) {
                throw ApiException
                        .conflict("The email " + candidate.getEmail() + " already belongs to another guardian.");
            }
        }
    }
}
