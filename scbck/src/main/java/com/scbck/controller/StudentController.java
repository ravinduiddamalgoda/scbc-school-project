package com.scbck.controller;

import java.time.LocalDateTime;
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
import com.scbck.model.Student;
import com.scbck.model.User;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.UserDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Student CRUD.
 *
 * The module previously had only a read endpoint, and the browser code posted
 * new students to /employee/insert - writing student records into the employee
 * table. Create, update and delete are implemented here against the student
 * table, and authorisation now checks the Student module rather than Employee.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    /** student_status id representing a soft-deleted record. */
    private static final int STUDENT_STATUS_DELETED = 3;

    private final StudentDao studentDao;
    private final StudentStatusDao studentStatusDao;
    private final UserDao userDao;
    private final PrivilegeService privilegeService;

    public StudentController(StudentDao studentDao, StudentStatusDao studentStatusDao, UserDao userDao,
            PrivilegeService privilegeService) {
        this.studentDao = studentDao;
        this.studentStatusDao = studentStatusDao;
        this.userDao = userDao;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public List<Student> findAll() {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return studentDao.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Student findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return studentDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Student " + id + " does not exist."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Student> create(@Valid @RequestBody Student student) {
        privilegeService.requireInsert(PrivilegeService.MODULE_STUDENT);

        assertNoDuplicates(student, null);

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        student.setId(null);
        student.setAdded_datetime(LocalDateTime.now());
        student.setAdded_user_id(currentUser == null ? null : currentUser.getId());
        student.setUpdated_datetime(null);
        student.setUpdated_user_id(null);
        student.setDeleted_datetime(null);
        student.setDeleted_user_id(null);
        student.setStu_no(studentDao.getNextStuNo());

        return ResponseEntity.status(HttpStatus.CREATED).body(studentDao.save(student));
    }

    @PutMapping("/{id}")
    @Transactional
    public Student update(@PathVariable Integer id, @Valid @RequestBody Student student) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_STUDENT);

        Student existing = studentDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Student " + id + " does not exist."));

        assertNoDuplicates(student, id);

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        existing.setFullname(student.getFullname());
        existing.setCallingname(student.getCallingname());
        existing.setBirth_certi_no(student.getBirth_certi_no());
        existing.setNic(student.getNic());
        existing.setGender(student.getGender());
        existing.setDob(student.getDob());
        existing.setReligion(student.getReligion());
        existing.setNationality(student.getNationality());
        existing.setPrevious_scl(student.getPrevious_scl());
        existing.setAddress(student.getAddress());
        existing.setNote(student.getNote());
        existing.setStudent_status_id(student.getStudent_status_id());
        existing.setGrade_id(student.getGrade_id());
        existing.setGuardian_id(student.getGuardian_id());
        if (student.getStu_photo() != null) {
            existing.setStu_photo(student.getStu_photo());
        }

        existing.setUpdated_datetime(LocalDateTime.now());
        existing.setUpdated_user_id(currentUser == null ? null : currentUser.getId());

        return studentDao.save(existing);
    }

    /** Soft delete: moves the student to the "Deleted" student status. */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_STUDENT);

        Student existing = studentDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Student " + id + " does not exist."));

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        existing.setDeleted_datetime(LocalDateTime.now());
        existing.setDeleted_user_id(currentUser == null ? null : currentUser.getId());
        existing.setStudent_status_id(studentStatusDao.findById(STUDENT_STATUS_DELETED)
                .orElseThrow(() -> ApiException
                        .badRequest("The 'Deleted' student status row is missing from the database.")));

        studentDao.save(existing);

        return MessageResponse.of("Student " + existing.getStu_no() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void assertNoDuplicates(Student candidate, Integer selfId) {
        Student byBirthCert = studentDao.getByBirthCertiNo(candidate.getBirth_certi_no());
        if (byBirthCert != null && !Objects.equals(byBirthCert.getId(), selfId)) {
            throw ApiException.conflict("The birth certificate number " + candidate.getBirth_certi_no()
                    + " already belongs to another student.");
        }

        // NIC is optional for younger students, so only check it when supplied.
        if (candidate.getNic() != null && !candidate.getNic().isBlank()) {
            Student byNic = studentDao.getByNic(candidate.getNic());
            if (byNic != null && !Objects.equals(byNic.getId(), selfId)) {
                throw ApiException.conflict("The NIC " + candidate.getNic() + " already belongs to another student.");
            }
        }
    }
}
